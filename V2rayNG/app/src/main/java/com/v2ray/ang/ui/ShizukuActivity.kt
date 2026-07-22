package com.v2ray.ang.ui

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.HotspotRoutingSnapshot
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.extension.toastWarning
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.shizuku.HotspotRoutingConfig
import com.v2ray.ang.shizuku.IShizukuTetheringService
import com.v2ray.ang.shizuku.ShizukuTetheringService
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

class ShizukuActivity : BaseComponentActivity() {
    private var tetheringService: IShizukuTetheringService? = null
    private var operationJob: Job? = null
    private var operationGeneration = 0L
    private var snapshotWaiter: CompletableDeferred<HotspotRoutingSnapshot>? = null
    private var uiState by mutableStateOf(TetheringUiState())

    private val userServiceArgs by lazy {
        ShizukuTetheringService.createUserServiceArgs()
    }

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            tetheringService = IShizukuTetheringService.Stub.asInterface(binder)
            refreshTetheringStatus()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            clearServiceState()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread { refreshShizukuStatus() }
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread {
            clearServiceState()
            refreshShizukuStatus()
        }
    }
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            runOnUiThread {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    toastSuccess(R.string.shizuku_permission_granted)
                } else {
                    toastError(R.string.shizuku_permission_denied)
                }
                refreshShizukuStatus()
            }
        }
    }

    private val coreStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING,
                AppConfig.MSG_STATE_START_SUCCESS -> {
                    uiState = uiState.copy(coreRunning = true)
                    requestCoreSnapshotAsync()
                }

                AppConfig.MSG_STATE_NOT_RUNNING,
                AppConfig.MSG_STATE_STOP_SUCCESS,
                AppConfig.MSG_STATE_START_FAILURE -> {
                    uiState = uiState.copy(coreRunning = false)
                }

                AppConfig.MSG_HOTSPOT_CONFIG_RESPONSE -> {
                    val snapshot = intent.serializable<HotspotRoutingSnapshot>("content")
                        ?: HotspotRoutingSnapshot()
                    uiState = uiState.copy(coreRunning = snapshot.running)
                    snapshotWaiter?.takeIf { !it.isCompleted }?.complete(snapshot)
                }
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        TetheringScreen(
            state = uiState,
            serviceConnected = tetheringService != null,
            onBackClick = { finish() },
            onRequestPermission = ::requestShizukuPermission,
            onRefresh = {
                requestCoreSnapshotAsync()
                refreshShizukuStatus()
            },
            onToggleRouting = ::toggleRouting,
            onToggleHotspot = ::toggleHotspot,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ContextCompat.registerReceiver(
            this,
            coreStateReceiver,
            IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
            Utils.receiverFlags()
        )
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
    }

    override fun onResume() {
        super.onResume()
        queryCoreState()
        refreshShizukuStatus()
    }

    override fun onDestroy() {
        cancelCurrentOperation()
        snapshotWaiter?.cancel()
        runCatching { unregisterReceiver(coreStateReceiver) }
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        if (tetheringService != null || uiState.operation == TetheringOperation.CONNECTING) {
            runCatching {
                // The service owns the live TUN and tethering upstream. Screen teardown must
                // never destroy it based on a potentially stale status snapshot; only the
                // explicit Disable action is allowed to stop and remove the datapath.
                Shizuku.unbindUserService(userServiceArgs, userServiceConnection, false)
            }
        }
        tetheringService = null
        super.onDestroy()
    }

    private fun queryCoreState() {
        MessageUtil.sendMsg2Service(this, AppConfig.MSG_REGISTER_CLIENT, "")
    }

    private fun requestCoreSnapshotAsync() {
        MessageUtil.sendMsg2Service(this, AppConfig.MSG_QUERY_HOTSPOT_CONFIG, "")
    }

    private fun refreshShizukuStatus() {
        val status = getShizukuStatus()
        if (status != ShizukuStatus.READY) {
            clearServiceState()
            uiState = uiState.copy(shizukuStatus = status)
            return
        }
        uiState = uiState.copy(shizukuStatus = status)

        if (tetheringService == null) bindUserService() else refreshTetheringStatus()
    }

    private fun getShizukuStatus(): ShizukuStatus {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            return if (isPackageInstalled(ShizukuProvider.MANAGER_APPLICATION_ID)) {
                ShizukuStatus.NOT_RUNNING
            } else {
                ShizukuStatus.NOT_INSTALLED
            }
        }
        if (runCatching { Shizuku.isPreV11() }.getOrDefault(true)) {
            return ShizukuStatus.UNSUPPORTED
        }
        val permissionGranted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (permissionGranted) return ShizukuStatus.READY

        return if (runCatching {
                Shizuku.shouldShowRequestPermissionRationale()
            }.getOrDefault(false)
        ) {
            ShizukuStatus.PERMISSION_DENIED
        } else {
            ShizukuStatus.PERMISSION_REQUIRED
        }
    }

    private fun requestShizukuPermission() {
        when (val status = getShizukuStatus()) {
            ShizukuStatus.PERMISSION_REQUIRED -> runCatching {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            }.onFailure {
                toastError(it.message ?: getString(R.string.shizuku_operation_failed))
                refreshShizukuStatus()
            }

            ShizukuStatus.READY -> refreshShizukuStatus()
            else -> {
                toastError(status.statusRes)
                refreshShizukuStatus()
            }
        }
    }

    private fun bindUserService() {
        if (uiState.operation == TetheringOperation.CONNECTING || tetheringService != null) return
        uiState = uiState.copy(operation = TetheringOperation.CONNECTING)

        runCatching {
            Shizuku.bindUserService(userServiceArgs, userServiceConnection)
        }.onFailure {
            clearServiceState()
            toastError(it.message ?: getString(R.string.shizuku_operation_failed))
        }
    }

    private fun refreshTetheringStatus() {
        if (uiState.operation.isToggleInProgress) return
        val service = tetheringService ?: run {
            clearServiceState()
            return
        }
        val generation = cancelCurrentOperation()
        uiState = uiState.copy(operation = TetheringOperation.CHECKING)
        operationJob = lifecycleScope.launch {
            val ipv6Enabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED) == true
            val status = withContext(Dispatchers.IO) {
                StatusSnapshot(
                    routing = runCatching { service.routingState }
                        .getOrDefault(ShizukuTetheringService.ROUTING_STATE_ERROR),
                    detail = runCatching { service.routingDetail }.getOrDefault(""),
                    tetheringTypes = runCatching { service.activeTetheringTypes }
                        .getOrDefault(ShizukuTetheringService.TETHERING_TYPES_UNKNOWN),
                    ipv6TetheringTypes = if (ipv6Enabled) {
                        runCatching { service.ipv6TetheringTypes }
                            .getOrDefault(ShizukuTetheringService.TETHERING_TYPES_UNKNOWN)
                    } else {
                        ShizukuTetheringService.TETHERING_TYPES_UNKNOWN
                    },
                    warning = runCatching { service.consumeWarning() }
                        .getOrDefault(ShizukuTetheringService.RESULT_OK),
                )
            }
            if (generation != operationGeneration) return@launch
            uiState = uiState.copy(
                operation = TetheringOperation.NONE,
                routingState = status.routing,
                routingDetail = status.detail,
                activeTetheringTypes = status.tetheringTypes,
                ipv6TetheringTypes = status.ipv6TetheringTypes,
                ipv6Enabled = ipv6Enabled,
            )
            operationJob = null
            if (status.warning == ShizukuTetheringService.RESULT_UNPROTECTED_UPSTREAM) {
                toastWarning(R.string.shizuku_tethering_wrong_upstream)
            }
        }
    }

    private fun toggleRouting() {
        val enable = !uiState.routingSessionEnabled
        launchOperation(
            if (enable) TetheringOperation.STARTING_ROUTING
            else TetheringOperation.STOPPING_ROUTING
        ) { service ->
            val result = if (enable) {
                startRouting(service)
            } else {
                callService { service.stopRouting() }
            }
            if (result != ShizukuTetheringService.RESULT_OK) {
                showRoutingError(result, service)
                return@launchOperation
            }
            if (!enable) MmkvManager.encodeSettings(AppConfig.PREF_SHIZUKU_SYNC_TOKEN, "")
            toastSuccess(
                if (enable) R.string.shizuku_routing_enabled
                else R.string.shizuku_routing_disabled
            )
        }
    }

    private fun toggleHotspot() {
        val enable = !uiState.hotspotEnabled
        launchOperation(
            if (enable) TetheringOperation.STARTING_HOTSPOT
            else TetheringOperation.STOPPING_HOTSPOT
        ) { service ->
            var routingStartedHere = false
            if (enable && !uiState.routingActive) {
                val routingResult = startRouting(service)
                if (routingResult != ShizukuTetheringService.RESULT_OK) {
                    showRoutingError(routingResult, service)
                    return@launchOperation
                }
                routingStartedHere = true
            }

            val result = callService { service.setWifiHotspotEnabled(enable) }
            if (result != ShizukuTetheringService.RESULT_OK) {
                if (routingStartedHere) {
                    callService { service.stopRouting() }
                    MmkvManager.encodeSettings(AppConfig.PREF_SHIZUKU_SYNC_TOKEN, "")
                }
                if (result != ShizukuTetheringService.RESULT_UNPROTECTED_UPSTREAM) {
                    toastError(getString(R.string.shizuku_hotspot_operation_failed, result))
                }
                return@launchOperation
            }

            toastSuccess(
                if (enable) R.string.shizuku_hotspot_enabled
                else R.string.shizuku_hotspot_disabled
            )
        }
    }

    private fun launchOperation(
        operation: TetheringOperation,
        action: suspend (IShizukuTetheringService) -> Unit,
    ) {
        if (uiState.operation.isToggleInProgress) return
        val service = tetheringService ?: return
        val generation = cancelCurrentOperation()
        uiState = uiState.copy(operation = operation)
        operationJob = lifecycleScope.launch {
            try {
                action(service)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                toastError(error.message ?: getString(R.string.shizuku_operation_failed))
            } finally {
                if (generation == operationGeneration) {
                    operationJob = null
                    uiState = uiState.copy(operation = TetheringOperation.NONE)
                    refreshTetheringStatus()
                }
            }
        }
    }

    private suspend fun callService(action: () -> Int): Int = withContext(Dispatchers.IO) {
        runCatching(action).getOrDefault(ShizukuTetheringService.RESULT_INTERNAL_ERROR)
    }

    private suspend fun startRouting(service: IShizukuTetheringService): Int {
        val snapshot = requestCoreSnapshot() ?: run {
            toastError(R.string.shizuku_routing_snapshot_timeout)
            return ShizukuTetheringService.RESULT_INTERNAL_ERROR
        }
        val launchConfig = try {
            withContext(Dispatchers.Default) {
                HotspotRoutingConfig.launchFromSnapshot(this@ShizukuActivity, snapshot)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            toastError(error.message ?: getString(R.string.shizuku_routing_snapshot_timeout))
            return ShizukuTetheringService.RESULT_ROUTING_FAILED
        }

        val previousSyncToken = MmkvManager.decodeSettingsString(AppConfig.PREF_SHIZUKU_SYNC_TOKEN)
            .orEmpty()
        val syncToken = Utils.getUuid()
        MmkvManager.encodeSettings(AppConfig.PREF_SHIZUKU_SYNC_TOKEN, syncToken)
        val result = callService {
            service.startRouting(
                launchConfig.engine.useHev,
                launchConfig.engine.profileName,
                launchConfig.engine.content,
                launchConfig.dnsServers.toTypedArray(),
                launchConfig.ipv6Enabled,
                launchConfig.assetPath,
                launchConfig.xudpKey,
                syncToken,
            )
        }
        if (result != ShizukuTetheringService.RESULT_OK &&
            MmkvManager.decodeSettingsString(AppConfig.PREF_SHIZUKU_SYNC_TOKEN) == syncToken
        ) {
            MmkvManager.encodeSettings(AppConfig.PREF_SHIZUKU_SYNC_TOKEN, previousSyncToken)
        }
        return result
    }

    private suspend fun requestCoreSnapshot(): HotspotRoutingSnapshot? {
        val waiter = CompletableDeferred<HotspotRoutingSnapshot>()
        snapshotWaiter?.cancel()
        snapshotWaiter = waiter
        requestCoreSnapshotAsync()
        return withTimeoutOrNull(CORE_SNAPSHOT_TIMEOUT_MS) { waiter.await() }
            .also { if (snapshotWaiter === waiter) snapshotWaiter = null }
    }

    private suspend fun showRoutingError(result: Int, service: IShizukuTetheringService) {
        val detail = withContext(Dispatchers.IO) {
            runCatching { service.routingDetail }.getOrDefault("")
        }
        toastError(
            getString(
                R.string.shizuku_routing_operation_failed,
                result,
                detail.ifBlank { getString(R.string.shizuku_operation_failed) }
            )
        )
    }

    private fun cancelCurrentOperation(): Long {
        operationGeneration++
        operationJob?.cancel()
        operationJob = null
        return operationGeneration
    }

    private fun clearServiceState() {
        cancelCurrentOperation()
        tetheringService = null
        uiState = uiState.copy(
            operation = TetheringOperation.NONE,
            routingState = ShizukuTetheringService.ROUTING_STATE_DISABLED,
            routingDetail = "",
            activeTetheringTypes = ShizukuTetheringService.TETHERING_TYPES_UNKNOWN,
            ipv6TetheringTypes = ShizukuTetheringService.TETHERING_TYPES_UNKNOWN,
        )
    }

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(packageName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0)
            )
        } else {
            packageManager.getApplicationInfo(packageName, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private data class StatusSnapshot(
        val routing: Int,
        val detail: String,
        val tetheringTypes: Int,
        val ipv6TetheringTypes: Int,
        val warning: Int,
    )

    companion object {
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
        private const val CORE_SNAPSHOT_TIMEOUT_MS = 5_000L
    }
}
