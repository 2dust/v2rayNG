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
import androidx.compose.runtime.mutableIntStateOf
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
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.dto.HotspotRoutingSnapshot
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
import java.util.UUID

class ShizukuActivity : BaseComponentActivity() {
    private var tetheringService: IShizukuTetheringService? = null
    private var bindingUserService = false
    private var hotspotState = ShizukuTetheringService.HOTSPOT_STATE_UNKNOWN
    private var routingState = ShizukuTetheringService.ROUTING_STATE_DISABLED
    private var routingDetail = ""
    private var activeTetheringTypes = ShizukuTetheringService.TETHERING_TYPES_UNKNOWN
    private var coreRunning = false
    private var operationJob: Job? = null
    private var snapshotWaiter: CompletableDeferred<HotspotRoutingSnapshot>? = null

    private var shizukuStatusRes by mutableIntStateOf(R.string.shizuku_status_checking)
    private var shizukuDetailsRes by mutableStateOf<Int?>(R.string.shizuku_status_checking)
    private var requestPermissionEnabled by mutableStateOf(false)
    private var routingStatusRes by mutableIntStateOf(R.string.shizuku_routing_status_unavailable)
    private var routingDetails by mutableStateOf("")
    private var routingButtonRes by mutableIntStateOf(R.string.shizuku_routing_enable)
    private var routingButtonEnabled by mutableStateOf(false)
    private var hotspotStatusRes by mutableIntStateOf(R.string.shizuku_hotspot_status_unavailable)
    private var hotspotButtonRes by mutableIntStateOf(R.string.shizuku_hotspot_enable)
    private var hotspotButtonEnabled by mutableStateOf(false)
    private var isLoading by mutableStateOf(false)

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, ShizukuTetheringService::class.java.name)
        )
            .daemon(true)
            .processNameSuffix("shizuku_tethering")
            .debuggable(BuildConfig.DEBUG)
            .version(ShizukuTetheringService.USER_SERVICE_VERSION)
    }

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            bindingUserService = false
            tetheringService = IShizukuTetheringService.Stub.asInterface(binder)
            refreshTetheringStatus()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            bindingUserService = false
            tetheringService = null
            renderRoutingUnavailable()
            renderHotspotUnavailable()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread { refreshShizukuStatus() }
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread {
            bindingUserService = false
            tetheringService = null
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
                    coreRunning = true
                    requestCoreSnapshotAsync()
                    renderRoutingState()
                    renderHotspotState()
                }

                AppConfig.MSG_STATE_NOT_RUNNING,
                AppConfig.MSG_STATE_STOP_SUCCESS,
                AppConfig.MSG_STATE_START_FAILURE -> {
                    coreRunning = false
                    renderRoutingState()
                    renderHotspotState()
                }

                AppConfig.MSG_HOTSPOT_CONFIG_RESPONSE -> {
                    val snapshot = readSnapshot(intent) ?: HotspotRoutingSnapshot.stopped()
                    coreRunning = snapshot.running
                    snapshotWaiter?.takeIf { !it.isCompleted }?.complete(snapshot)
                    renderRoutingState()
                    renderHotspotState()
                }
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        TetheringScreen(
            isLoading = isLoading,
            shizukuStatusRes = shizukuStatusRes,
            shizukuDetailsRes = shizukuDetailsRes,
            requestPermissionEnabled = requestPermissionEnabled,
            routingStatusRes = routingStatusRes,
            routingDetails = routingDetails,
            routingButtonRes = routingButtonRes,
            routingButtonEnabled = routingButtonEnabled,
            hotspotStatusRes = hotspotStatusRes,
            hotspotButtonRes = hotspotButtonRes,
            hotspotButtonEnabled = hotspotButtonEnabled,
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
        queryCoreState()
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
        if (tetheringService != null || bindingUserService) {
            runCatching {
                // The service owns the live TUN and tethering upstream. Screen teardown must
                // never destroy it based on a potentially stale status snapshot; only the
                // explicit Disable action is allowed to stop and remove the datapath.
                Shizuku.unbindUserService(userServiceArgs, userServiceConnection, false)
            }
        }
        tetheringService = null
        bindingUserService = false
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
            shizukuStatusRes = if (installed) R.string.shizuku_status_not_running
            else R.string.shizuku_status_not_installed
            shizukuDetailsRes = R.string.shizuku_status_open_manager
            requestPermissionEnabled = false
            bindingUserService = false
            tetheringService = null
            renderRoutingUnavailable()
            renderHotspotUnavailable()
            return
        }

        if (runCatching { Shizuku.isPreV11() }.getOrDefault(true)) {
            shizukuStatusRes = R.string.shizuku_status_unsupported
            shizukuDetailsRes = R.string.shizuku_status_update_required
            requestPermissionEnabled = false
            renderRoutingUnavailable()
            renderHotspotUnavailable()
            return
        }

        val permissionGranted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!permissionGranted) {
            val permanentlyDenied = runCatching {
                Shizuku.shouldShowRequestPermissionRationale()
            }.getOrDefault(false)
            shizukuStatusRes = if (permanentlyDenied) R.string.shizuku_status_permission_denied
            else R.string.shizuku_status_permission_required
            shizukuDetailsRes = R.string.shizuku_status_permission_hint
            requestPermissionEnabled = !permanentlyDenied
            tetheringService = null
            bindingUserService = false
            renderRoutingUnavailable()
            renderHotspotUnavailable()
            return
        }

        shizukuStatusRes = R.string.shizuku_status_ready
        shizukuDetailsRes = null
        requestPermissionEnabled = false

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            hotspotStatusRes = R.string.shizuku_hotspot_status_unsupported
            hotspotButtonEnabled = false
            renderRoutingUnavailable()
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
        if (bindingUserService || tetheringService != null) return
        bindingUserService = true
        routingStatusRes = R.string.shizuku_routing_status_connecting
        hotspotStatusRes = R.string.shizuku_hotspot_status_connecting
        routingButtonEnabled = false
        hotspotButtonEnabled = false

        runCatching {
            Shizuku.bindUserService(userServiceArgs, userServiceConnection)
        }.onFailure {
            bindingUserService = false
            tetheringService = null
            renderRoutingUnavailable()
            renderHotspotUnavailable()
            toastError(it.message ?: getString(R.string.shizuku_operation_failed))
        }
    }

    private fun refreshTetheringStatus() {
        val service = tetheringService ?: run {
            renderRoutingUnavailable()
            renderHotspotUnavailable()
            return
        }
        operationJob?.cancel()
        routingStatusRes = R.string.shizuku_routing_status_checking
        hotspotStatusRes = R.string.shizuku_hotspot_status_checking
        routingButtonEnabled = false
        hotspotButtonEnabled = false
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
            hotspotState = status.hotspot
            routingState = status.routing
            routingDetail = status.detail
            activeTetheringTypes = status.tetheringTypes
            renderRoutingState()
            renderHotspotState()
        }
    }

    private fun toggleRouting() {
        val service = tetheringService ?: return
        val enable = !isRoutingActive()
        operationJob?.cancel()
        routingStatusRes = if (enable) R.string.shizuku_routing_status_starting
        else R.string.shizuku_routing_status_stopping
        routingButtonEnabled = false
        hotspotButtonEnabled = false
        isLoading = true

        operationJob = lifecycleScope.launch {
            if (enable) {
                val result = startRouting(service)
                if (result != ShizukuTetheringService.RESULT_OK) {
                    isLoading = false
                    showRoutingError(result, service)
                    refreshTetheringStatus()
                    return@launch
                }
                toastSuccess(R.string.shizuku_routing_enabled)
            } else {
                val result = withContext(Dispatchers.IO) {
                    val tetheringStop = runCatching { service.stopActiveTethering() }
                        .getOrDefault(ShizukuTetheringService.RESULT_INTERNAL_ERROR)
                    if (tetheringStop != ShizukuTetheringService.RESULT_OK) tetheringStop
                    else runCatching { service.stopRouting() }
                        .getOrDefault(ShizukuTetheringService.RESULT_INTERNAL_ERROR)
                }
                if (result != ShizukuTetheringService.RESULT_OK) {
                    isLoading = false
                    showRoutingError(result, service)
                    refreshTetheringStatus()
                    return@launch
                }
                MmkvManager.encodeSettings(AppConfig.PREF_SHIZUKU_SYNC_TOKEN, "")
                toastSuccess(R.string.shizuku_routing_disabled)
            }
            isLoading = false
            refreshTetheringStatus()
        }
    }

    private fun toggleHotspot() {
        val service = tetheringService ?: return
        val enable = hotspotState != ShizukuTetheringService.HOTSPOT_STATE_ENABLED
        operationJob?.cancel()
        hotspotStatusRes = if (enable) R.string.shizuku_hotspot_status_starting
        else R.string.shizuku_hotspot_status_stopping
        routingButtonEnabled = false
        hotspotButtonEnabled = false
        isLoading = true

        operationJob = lifecycleScope.launch {
            var routingStartedHere = false
            if (enable && !isRoutingActive()) {
                val routingResult = startRouting(service)
                if (routingResult != ShizukuTetheringService.RESULT_OK) {
                    isLoading = false
                    showRoutingError(routingResult, service)
                    refreshTetheringStatus()
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
                isLoading = false
                toastError(getString(R.string.shizuku_hotspot_operation_failed, result))
                refreshTetheringStatus()
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
            isLoading = false
            hotspotState = state
            toastSuccess(
                if (enable) R.string.shizuku_hotspot_enabled
                else R.string.shizuku_hotspot_disabled
            )
            refreshTetheringStatus()
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

        val syncToken = UUID.randomUUID().toString()
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

    private fun showRoutingError(result: Int, service: IShizukuTetheringService) {
        lifecycleScope.launch {
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
    }

    private fun renderRoutingState() {
        val usbActive = activeTetheringTypes >= 0 &&
            activeTetheringTypes and (1 shl ShizukuTetheringService.TETHERING_TYPE_USB) != 0
        when (routingState) {
            ShizukuTetheringService.ROUTING_STATE_ACTIVE_HEV -> {
                routingStatusRes = R.string.shizuku_routing_status_hev
                routingButtonRes = R.string.shizuku_routing_disable
                routingButtonEnabled = true
            }
            ShizukuTetheringService.ROUTING_STATE_ACTIVE_NATIVE -> {
                routingStatusRes = R.string.shizuku_routing_status_native
                routingButtonRes = R.string.shizuku_routing_disable
                routingButtonEnabled = true
            }
            ShizukuTetheringService.ROUTING_STATE_STARTING -> {
                routingStatusRes = R.string.shizuku_routing_status_starting
                routingButtonEnabled = false
            }
            ShizukuTetheringService.ROUTING_STATE_STOPPING -> {
                routingStatusRes = R.string.shizuku_routing_status_stopping
                routingButtonEnabled = false
            }
            ShizukuTetheringService.ROUTING_STATE_ERROR -> {
                routingStatusRes = R.string.shizuku_routing_status_error
                routingButtonRes = R.string.shizuku_routing_enable
                routingButtonEnabled = coreRunning
            }
            else -> {
                routingStatusRes = if (coreRunning) R.string.shizuku_routing_status_disabled
                else R.string.shizuku_routing_status_start_v2ray
                routingButtonRes = R.string.shizuku_routing_enable
                routingButtonEnabled = coreRunning && tetheringService != null
            }
        }
        routingDetails = buildString {
            append(routingDetail.ifBlank { getString(R.string.shizuku_routing_summary) })
            if (usbActive) append("\n").append(getString(R.string.shizuku_usb_status_enabled))
        }
    }

    private fun renderHotspotState() {
        when (hotspotState) {
            ShizukuTetheringService.HOTSPOT_STATE_ENABLED -> {
                hotspotStatusRes = if (isRoutingActive()) R.string.shizuku_hotspot_status_enabled
                else R.string.shizuku_hotspot_status_enabled_direct
                hotspotButtonRes = R.string.shizuku_hotspot_disable
                hotspotButtonEnabled = tetheringService != null
            }
            ShizukuTetheringService.HOTSPOT_STATE_DISABLED -> {
                hotspotStatusRes = R.string.shizuku_hotspot_status_disabled
                hotspotButtonRes = R.string.shizuku_hotspot_enable
                hotspotButtonEnabled = tetheringService != null &&
                    (isRoutingActive() || coreRunning)
            }
            else -> renderHotspotUnavailable()
        }
    }

    private fun isRoutingActive(): Boolean =
        routingState == ShizukuTetheringService.ROUTING_STATE_ACTIVE_HEV ||
            routingState == ShizukuTetheringService.ROUTING_STATE_ACTIVE_NATIVE

    private fun renderRoutingUnavailable() {
        routingState = ShizukuTetheringService.ROUTING_STATE_DISABLED
        routingDetail = ""
        routingStatusRes = R.string.shizuku_routing_status_unavailable
        routingDetails = getString(R.string.shizuku_routing_summary)
        routingButtonRes = R.string.shizuku_routing_enable
        routingButtonEnabled = false
    }

    private fun renderHotspotUnavailable() {
        hotspotState = ShizukuTetheringService.HOTSPOT_STATE_UNKNOWN
        hotspotStatusRes = R.string.shizuku_hotspot_status_unavailable
        hotspotButtonRes = R.string.shizuku_hotspot_enable
        hotspotButtonEnabled = false
    }

    @Suppress("DEPRECATION")
    private fun readSnapshot(intent: Intent): HotspotRoutingSnapshot? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("content", HotspotRoutingSnapshot::class.java)
        } else {
            intent.getSerializableExtra("content") as? HotspotRoutingSnapshot
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

@Composable
private fun TetheringScreen(
    isLoading: Boolean,
    shizukuStatusRes: Int,
    shizukuDetailsRes: Int?,
    requestPermissionEnabled: Boolean,
    routingStatusRes: Int,
    routingDetails: String,
    routingButtonRes: Int,
    routingButtonEnabled: Boolean,
    hotspotStatusRes: Int,
    hotspotButtonRes: Int,
    hotspotButtonEnabled: Boolean,
    onBackClick: () -> Unit,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onToggleRouting: () -> Unit,
    onToggleHotspot: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_tethering),
                onBackClick = onBackClick,
                isLoading = isLoading,
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
                status = stringResource(shizukuStatusRes),
                details = shizukuDetailsRes?.let { listOf(stringResource(it)) }.orEmpty(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TetheringActionButton(
                    text = stringResource(R.string.shizuku_request_permission),
                    enabled = requestPermissionEnabled && !isLoading,
                    onClick = onRequestPermission,
                    modifier = Modifier.weight(1f),
                )
                TetheringActionButton(
                    text = stringResource(R.string.shizuku_refresh_permission),
                    enabled = !isLoading,
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            TetheringStatusSection(
                iconRes = R.drawable.ic_routing_24dp,
                title = stringResource(R.string.shizuku_routing_title),
                status = stringResource(routingStatusRes),
                details = listOf(
                    routingDetails.ifBlank { stringResource(R.string.shizuku_routing_summary) },
                    stringResource(R.string.shizuku_routing_rules_disclaimer),
                ),
            )
            TetheringActionButton(
                text = stringResource(routingButtonRes),
                enabled = routingButtonEnabled && !isLoading,
                onClick = onToggleRouting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))
            TetheringStatusSection(
                iconRes = R.drawable.ic_wifi_tethering_24dp,
                title = stringResource(R.string.shizuku_hotspot_title),
                status = stringResource(hotspotStatusRes),
                details = listOf(stringResource(R.string.shizuku_hotspot_summary)),
            )
            TetheringActionButton(
                text = stringResource(hotspotButtonRes),
                enabled = hotspotButtonEnabled && !isLoading,
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
