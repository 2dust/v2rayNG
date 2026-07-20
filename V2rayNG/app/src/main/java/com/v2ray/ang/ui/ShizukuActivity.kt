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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.dto.HotspotRoutingSnapshot
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.shizuku.HotspotRoutingConfig
import com.v2ray.ang.shizuku.IShizukuTetheringService
import com.v2ray.ang.shizuku.ShizukuTetheringService
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

private enum class ShizukuStatus(
    val statusRes: Int,
    val detailsRes: Int?,
    val canRequestPermission: Boolean = false,
) {
    CHECKING(R.string.shizuku_status_checking, R.string.shizuku_status_checking),
    NOT_INSTALLED(R.string.shizuku_status_not_installed, R.string.shizuku_status_open_manager),
    NOT_RUNNING(R.string.shizuku_status_not_running, R.string.shizuku_status_open_manager),
    UNSUPPORTED(R.string.shizuku_status_unsupported, R.string.shizuku_status_update_required),
    PERMISSION_REQUIRED(
        R.string.shizuku_status_permission_required,
        R.string.shizuku_status_permission_hint,
        true,
    ),
    PERMISSION_DENIED(
        R.string.shizuku_status_permission_denied,
        R.string.shizuku_status_permission_hint,
    ),
    READY(R.string.shizuku_status_ready, null),
}

private enum class TetheringOperation {
    NONE,
    CONNECTING,
    CHECKING,
    STARTING_ROUTING,
    STOPPING_ROUTING,
    STARTING_HOTSPOT,
    STOPPING_HOTSPOT;

    val isLoading: Boolean
        get() = this == STARTING_ROUTING || this == STOPPING_ROUTING ||
            this == STARTING_HOTSPOT || this == STOPPING_HOTSPOT
}

private data class TetheringUiState(
    val shizukuStatus: ShizukuStatus = ShizukuStatus.CHECKING,
    val operation: TetheringOperation = TetheringOperation.NONE,
    val hotspotState: Int = ShizukuTetheringService.HOTSPOT_STATE_UNKNOWN,
    val routingState: Int = ShizukuTetheringService.ROUTING_STATE_DISABLED,
    val routingDetail: String = "",
    val activeTetheringTypes: Int = ShizukuTetheringService.TETHERING_TYPES_UNKNOWN,
    val coreRunning: Boolean = false,
)

private val TetheringUiState.routingActive: Boolean
    get() = routingState == ShizukuTetheringService.ROUTING_STATE_ACTIVE_HEV ||
        routingState == ShizukuTetheringService.ROUTING_STATE_ACTIVE_NATIVE

private val TetheringUiState.routingSessionEnabled: Boolean
    get() = routingActive || routingState == ShizukuTetheringService.ROUTING_STATE_WAITING

private val TetheringUiState.hotspotEnabled: Boolean
    get() = hotspotState == ShizukuTetheringService.HOTSPOT_STATE_ENABLED

class ShizukuActivity : BaseComponentActivity() {
    private var tetheringService: IShizukuTetheringService? = null
    private var operationJob: Job? = null
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
            platformSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
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
        operationJob?.cancel()
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
        requestCoreSnapshotAsync()
    }

    private fun requestCoreSnapshotAsync() {
        MessageUtil.sendMsg2Service(this, AppConfig.MSG_QUERY_HOTSPOT_CONFIG, "")
    }

    private fun refreshShizukuStatus() {
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val installed = isPackageInstalled(SHIZUKU_PACKAGE_NAME)

        if (!binderAlive) {
            clearServiceState()
            uiState = uiState.copy(
                shizukuStatus = if (installed) {
                    ShizukuStatus.NOT_RUNNING
                } else {
                    ShizukuStatus.NOT_INSTALLED
                }
            )
            return
        }

        if (runCatching { Shizuku.isPreV11() }.getOrDefault(true)) {
            clearServiceState()
            uiState = uiState.copy(shizukuStatus = ShizukuStatus.UNSUPPORTED)
            return
        }

        val permissionGranted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!permissionGranted) {
            val permanentlyDenied = runCatching {
                Shizuku.shouldShowRequestPermissionRationale()
            }.getOrDefault(false)
            clearServiceState()
            uiState = uiState.copy(
                shizukuStatus = if (permanentlyDenied) {
                    ShizukuStatus.PERMISSION_DENIED
                } else {
                    ShizukuStatus.PERMISSION_REQUIRED
                }
            )
            return
        }

        uiState = uiState.copy(shizukuStatus = ShizukuStatus.READY)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            clearServiceState()
            return
        }

        if (tetheringService == null) bindUserService() else refreshTetheringStatus()
    }

    private fun requestShizukuPermission() {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            toastError(R.string.shizuku_status_not_running)
            refreshShizukuStatus()
            return
        }

        runCatching {
            when {
                Shizuku.isPreV11() -> toastError(R.string.shizuku_status_unsupported)
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                    refreshShizukuStatus()
                }
                Shizuku.shouldShowRequestPermissionRationale() -> {
                    toastError(R.string.shizuku_permission_denied)
                }
                else -> Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            }
        }.onFailure {
            toastError(it.message ?: getString(R.string.shizuku_operation_failed))
            refreshShizukuStatus()
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
        val service = tetheringService ?: run {
            clearServiceState()
            return
        }
        operationJob?.cancel()
        uiState = uiState.copy(operation = TetheringOperation.CHECKING)
        operationJob = lifecycleScope.launch {
            val status = withContext(Dispatchers.IO) {
                StatusSnapshot(
                    hotspot = runCatching { service.wifiHotspotState }
                        .getOrDefault(ShizukuTetheringService.HOTSPOT_STATE_UNKNOWN),
                    routing = runCatching { service.routingState }
                        .getOrDefault(ShizukuTetheringService.ROUTING_STATE_ERROR),
                    detail = runCatching { service.routingDetail }.getOrDefault(""),
                    tetheringTypes = runCatching { service.activeTetheringTypes }
                        .getOrDefault(ShizukuTetheringService.TETHERING_TYPES_UNKNOWN),
                )
            }
            uiState = uiState.copy(
                operation = TetheringOperation.NONE,
                hotspotState = status.hotspot,
                routingState = status.routing,
                routingDetail = status.detail,
                activeTetheringTypes = status.tetheringTypes,
            )
            operationJob = null
        }
    }

    private fun toggleRouting() {
        val service = tetheringService ?: return
        val enable = !uiState.routingSessionEnabled
        operationJob?.cancel()
        uiState = uiState.copy(
            operation = if (enable) {
                TetheringOperation.STARTING_ROUTING
            } else {
                TetheringOperation.STOPPING_ROUTING
            }
        )

        operationJob = lifecycleScope.launch {
            if (enable) {
                val result = startRouting(service)
                if (result != ShizukuTetheringService.RESULT_OK) {
                    showRoutingError(result, service)
                    refreshAfterOperation()
                    return@launch
                }
                toastSuccess(R.string.shizuku_routing_enabled)
            } else {
                val result = withContext(Dispatchers.IO) {
                    runCatching { service.stopRouting() }
                        .getOrDefault(ShizukuTetheringService.RESULT_INTERNAL_ERROR)
                }
                if (result != ShizukuTetheringService.RESULT_OK) {
                    showRoutingError(result, service)
                    refreshAfterOperation()
                    return@launch
                }
                MmkvManager.encodeSettings(AppConfig.PREF_SHIZUKU_SYNC_TOKEN, "")
                toastSuccess(R.string.shizuku_routing_disabled)
            }
            refreshAfterOperation()
        }
    }

    private fun toggleHotspot() {
        val service = tetheringService ?: return
        val enable = !uiState.hotspotEnabled
        operationJob?.cancel()
        uiState = uiState.copy(
            operation = if (enable) {
                TetheringOperation.STARTING_HOTSPOT
            } else {
                TetheringOperation.STOPPING_HOTSPOT
            }
        )

        operationJob = lifecycleScope.launch {
            var routingStartedHere = false
            if (enable && !uiState.routingActive) {
                val routingResult = startRouting(service)
                if (routingResult != ShizukuTetheringService.RESULT_OK) {
                    showRoutingError(routingResult, service)
                    refreshAfterOperation()
                    return@launch
                }
                routingStartedHere = true
            }

            val result = withContext(Dispatchers.IO) {
                runCatching { service.setWifiHotspotEnabled(enable) }
                    .getOrDefault(ShizukuTetheringService.RESULT_INTERNAL_ERROR)
            }
            if (result != ShizukuTetheringService.RESULT_OK) {
                if (routingStartedHere) {
                    withContext(Dispatchers.IO) { runCatching { service.stopRouting() } }
                    MmkvManager.encodeSettings(AppConfig.PREF_SHIZUKU_SYNC_TOKEN, "")
                }
                toastError(getString(R.string.shizuku_hotspot_operation_failed, result))
                refreshAfterOperation()
                return@launch
            }

            var state = ShizukuTetheringService.HOTSPOT_STATE_UNKNOWN
            for (attempt in 0 until HOTSPOT_STATUS_POLL_COUNT) {
                delay(HOTSPOT_STATUS_POLL_INTERVAL_MS)
                state = withContext(Dispatchers.IO) {
                    runCatching { service.wifiHotspotState }
                        .getOrDefault(ShizukuTetheringService.HOTSPOT_STATE_UNKNOWN)
                }
                val reachedTarget = enable && state == ShizukuTetheringService.HOTSPOT_STATE_ENABLED ||
                    !enable && state == ShizukuTetheringService.HOTSPOT_STATE_DISABLED
                if (reachedTarget) break
            }
            uiState = uiState.copy(hotspotState = state)
            toastSuccess(
                if (enable) R.string.shizuku_hotspot_enabled
                else R.string.shizuku_hotspot_disabled
            )
            refreshAfterOperation()
        }
    }

    private suspend fun startRouting(service: IShizukuTetheringService): Int {
        val snapshot = requestCoreSnapshot() ?: run {
            toastError(R.string.shizuku_routing_snapshot_timeout)
            return ShizukuTetheringService.RESULT_INTERNAL_ERROR
        }
        val launchConfig = runCatching {
            withContext(Dispatchers.Default) {
                HotspotRoutingConfig.fromSnapshot(this@ShizukuActivity, snapshot)
            }
        }.getOrElse {
            toastError(it.message ?: getString(R.string.shizuku_routing_snapshot_timeout))
            return ShizukuTetheringService.RESULT_ROUTING_FAILED
        }

        val syncToken = Utils.getUuid()
        MmkvManager.encodeSettings(AppConfig.PREF_SHIZUKU_SYNC_TOKEN, syncToken)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                service.startRouting(
                    launchConfig.useHev,
                    launchConfig.profileName,
                    launchConfig.coreConfig,
                    launchConfig.hevConfig,
                    launchConfig.assetPath,
                    launchConfig.xudpKey,
                    syncToken,
                )
            }.getOrDefault(ShizukuTetheringService.RESULT_INTERNAL_ERROR)
        }
        if (result != ShizukuTetheringService.RESULT_OK &&
            MmkvManager.decodeSettingsString(AppConfig.PREF_SHIZUKU_SYNC_TOKEN) == syncToken
        ) {
            MmkvManager.encodeSettings(AppConfig.PREF_SHIZUKU_SYNC_TOKEN, "")
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

    private fun refreshAfterOperation() {
        uiState = uiState.copy(operation = TetheringOperation.NONE)
        operationJob = null
        refreshTetheringStatus()
    }

    private fun clearServiceState() {
        operationJob?.cancel()
        operationJob = null
        tetheringService = null
        uiState = uiState.copy(
            operation = TetheringOperation.NONE,
            hotspotState = ShizukuTetheringService.HOTSPOT_STATE_UNKNOWN,
            routingState = ShizukuTetheringService.ROUTING_STATE_DISABLED,
            routingDetail = "",
            activeTetheringTypes = ShizukuTetheringService.TETHERING_TYPES_UNKNOWN,
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
        val hotspot: Int,
        val routing: Int,
        val detail: String,
        val tetheringTypes: Int,
    )

    companion object {
        private const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
        private const val HOTSPOT_STATUS_POLL_COUNT = 6
        private const val HOTSPOT_STATUS_POLL_INTERVAL_MS = 750L
        private const val CORE_SNAPSHOT_TIMEOUT_MS = 5_000L
    }
}

private data class TetheringAction(
    val statusRes: Int,
    val buttonRes: Int,
    val enabled: Boolean,
)

private fun routingAction(
    state: TetheringUiState,
    serviceConnected: Boolean,
    platformSupported: Boolean,
): TetheringAction {
    val statusRes = if (!platformSupported) {
        R.string.shizuku_routing_status_unavailable
    } else when (state.operation) {
        TetheringOperation.CONNECTING -> R.string.shizuku_routing_status_connecting
        TetheringOperation.CHECKING -> R.string.shizuku_routing_status_checking
        TetheringOperation.STARTING_ROUTING -> R.string.shizuku_routing_status_starting
        TetheringOperation.STOPPING_ROUTING -> R.string.shizuku_routing_status_stopping
        else -> when {
            !serviceConnected -> R.string.shizuku_routing_status_unavailable
            state.routingState == ShizukuTetheringService.ROUTING_STATE_ACTIVE_HEV ->
                R.string.shizuku_routing_status_hev
            state.routingState == ShizukuTetheringService.ROUTING_STATE_ACTIVE_NATIVE ->
                R.string.shizuku_routing_status_native
            state.routingState == ShizukuTetheringService.ROUTING_STATE_STARTING ->
                R.string.shizuku_routing_status_starting
            state.routingState == ShizukuTetheringService.ROUTING_STATE_STOPPING ->
                R.string.shizuku_routing_status_stopping
            state.routingState == ShizukuTetheringService.ROUTING_STATE_WAITING ->
                R.string.shizuku_routing_status_waiting
            state.routingState == ShizukuTetheringService.ROUTING_STATE_ERROR ->
                R.string.shizuku_routing_status_error
            state.coreRunning -> R.string.shizuku_routing_status_disabled
            else -> R.string.shizuku_routing_status_start_v2ray
        }
    }
    val enabled = platformSupported && serviceConnected &&
        state.operation == TetheringOperation.NONE &&
        when (state.routingState) {
            ShizukuTetheringService.ROUTING_STATE_ACTIVE_HEV,
            ShizukuTetheringService.ROUTING_STATE_ACTIVE_NATIVE,
            ShizukuTetheringService.ROUTING_STATE_WAITING -> true
            ShizukuTetheringService.ROUTING_STATE_ERROR,
            ShizukuTetheringService.ROUTING_STATE_DISABLED -> state.coreRunning
            else -> false
        }
    return TetheringAction(
        statusRes = statusRes,
        buttonRes = if (state.routingSessionEnabled) {
            R.string.shizuku_routing_disable
        } else {
            R.string.shizuku_routing_enable
        },
        enabled = enabled,
    )
}

private fun hotspotAction(
    state: TetheringUiState,
    serviceConnected: Boolean,
    platformSupported: Boolean,
): TetheringAction {
    val statusRes = if (!platformSupported) {
        R.string.shizuku_hotspot_status_unsupported
    } else when (state.operation) {
        TetheringOperation.CONNECTING -> R.string.shizuku_hotspot_status_connecting
        TetheringOperation.CHECKING -> R.string.shizuku_hotspot_status_checking
        TetheringOperation.STARTING_HOTSPOT -> R.string.shizuku_hotspot_status_starting
        TetheringOperation.STOPPING_HOTSPOT -> R.string.shizuku_hotspot_status_stopping
        else -> when {
            !serviceConnected -> R.string.shizuku_hotspot_status_unavailable
            state.hotspotEnabled && state.routingActive -> R.string.shizuku_hotspot_status_enabled
            state.hotspotEnabled &&
                state.routingState == ShizukuTetheringService.ROUTING_STATE_WAITING ->
                R.string.shizuku_hotspot_status_waiting
            state.hotspotEnabled -> R.string.shizuku_hotspot_status_enabled_direct
            state.hotspotState == ShizukuTetheringService.HOTSPOT_STATE_DISABLED ->
                R.string.shizuku_hotspot_status_disabled
            else -> R.string.shizuku_hotspot_status_unavailable
        }
    }
    return TetheringAction(
        statusRes = statusRes,
        buttonRes = if (state.hotspotEnabled) {
            R.string.shizuku_hotspot_disable
        } else {
            R.string.shizuku_hotspot_enable
        },
        enabled = platformSupported && serviceConnected &&
            state.operation == TetheringOperation.NONE &&
            (state.hotspotEnabled ||
                state.hotspotState == ShizukuTetheringService.HOTSPOT_STATE_DISABLED &&
                (state.routingActive || state.coreRunning)),
    )
}

@Composable
private fun TetheringScreen(
    state: TetheringUiState,
    serviceConnected: Boolean,
    platformSupported: Boolean,
    onBackClick: () -> Unit,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onToggleRouting: () -> Unit,
    onToggleHotspot: () -> Unit,
) {
    val routingAction = routingAction(state, serviceConnected, platformSupported)
    val hotspotAction = hotspotAction(state, serviceConnected, platformSupported)
    val routingSummary = stringResource(R.string.shizuku_routing_summary)
    val usbStatus = stringResource(R.string.shizuku_usb_status_enabled)
    val usbActive = state.activeTetheringTypes >= 0 &&
        state.activeTetheringTypes and (1 shl ShizukuTetheringService.TETHERING_TYPE_USB) != 0
    val routingDetails = buildString {
        append(state.routingDetail.ifBlank { routingSummary })
        if (usbActive) append("\n").append(usbStatus)
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_tethering),
                onBackClick = onBackClick,
                isLoading = state.operation.isLoading,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            TetheringStatusSection(
                iconRes = R.drawable.ic_device_hub_24dp,
                title = stringResource(R.string.shizuku_status_title),
                status = stringResource(state.shizukuStatus.statusRes),
                details = state.shizukuStatus.detailsRes
                    ?.let { listOf(stringResource(it)) }
                    .orEmpty(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TetheringActionButton(
                    text = stringResource(R.string.shizuku_request_permission),
                    enabled = state.shizukuStatus.canRequestPermission &&
                        !state.operation.isLoading,
                    onClick = onRequestPermission,
                    modifier = Modifier.weight(1f),
                )
                TetheringActionButton(
                    text = stringResource(R.string.shizuku_refresh_permission),
                    enabled = !state.operation.isLoading,
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            TetheringStatusSection(
                iconRes = R.drawable.ic_routing_24dp,
                title = stringResource(R.string.shizuku_routing_title),
                status = stringResource(routingAction.statusRes),
                details = listOf(
                    routingDetails.ifBlank { stringResource(R.string.shizuku_routing_summary) },
                    stringResource(R.string.shizuku_routing_rules_disclaimer),
                ),
            )
            TetheringActionButton(
                text = stringResource(routingAction.buttonRes),
                enabled = routingAction.enabled,
                onClick = onToggleRouting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))
            TetheringStatusSection(
                iconRes = R.drawable.ic_wifi_tethering_24dp,
                title = stringResource(R.string.shizuku_hotspot_title),
                status = stringResource(hotspotAction.statusRes),
                details = listOf(stringResource(R.string.shizuku_hotspot_summary)),
            )
            TetheringActionButton(
                text = stringResource(hotspotAction.buttonRes),
                enabled = hotspotAction.enabled,
                onClick = onToggleHotspot,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TetheringStatusSection(
    iconRes: Int,
    title: String,
    status: String,
    details: List<String>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            details.filter { it.isNotBlank() }.forEach { detail ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TetheringActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
