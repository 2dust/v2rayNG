package com.v2ray.ang.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.HotspotRoutingSnapshot
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.extension.toastInfo
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.shizuku.HotspotRoutingConfig
import com.v2ray.ang.shizuku.ICoreTetheringLease
import com.v2ray.ang.shizuku.IShizukuTetheringService
import com.v2ray.ang.shizuku.ITetheringStatusListener
import com.v2ray.ang.shizuku.ShizukuTetheringService
import com.v2ray.ang.shizuku.TetheringStatusSnapshot
import com.v2ray.ang.shizuku.coreTetheringLease
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

internal class ShizukuViewModel(application: Application) : BaseViewModel(application) {
    private val _uiState = MutableStateFlow(TetheringUiState())
    val uiState: StateFlow<TetheringUiState> = _uiState.asStateFlow()

    private var tetheringService: IShizukuTetheringService? = null
    private var operationJob: Job? = null
    private var operationGeneration = 0L
    private var statusRefreshPending = false
    private var snapshotWaiter: CompletableDeferred<CoreRoutingSnapshot>? = null
    private var bindingTimeout: Job? = null

    private val userServiceArgs by lazy {
        ShizukuTetheringService.createUserServiceArgs()
    }

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            viewModelScope.launch {
                bindingTimeout?.cancel()
                bindingTimeout = null
                val service = IShizukuTetheringService.Stub.asInterface(binder)
                tetheringService = service
                _uiState.update { it.withServiceConnection(true) }
                withContext(Dispatchers.IO) {
                    shizukuCall("register status listener", Unit) { service.setStatusListener(statusListener) }
                }
                refreshTetheringStatus()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            viewModelScope.launch { clearServiceState() }
        }
    }

    private val statusListener = object : ITetheringStatusListener.Stub() {
        override fun onStatusChanged() {
            // Platform callbacks and core synchronization can change the shell-side state while
            // this screen is idle. Refresh from that source of truth instead of polling it.
            viewModelScope.launch { refreshTetheringStatus() }
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        viewModelScope.launch { refreshShizukuStatus() }
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        viewModelScope.launch {
            clearServiceState()
            refreshShizukuStatus()
        }
    }
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            viewModelScope.launch {
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
                    _uiState.update { it.withCoreRunning(true) }
                    requestCoreSnapshotAsync()
                }

                AppConfig.MSG_STATE_NOT_RUNNING,
                AppConfig.MSG_STATE_STOP_SUCCESS,
                AppConfig.MSG_STATE_START_FAILURE -> _uiState.update { it.withCoreRunning(false) }

                AppConfig.MSG_HOTSPOT_CONFIG_RESPONSE -> {
                    val snapshot = intent.serializable<HotspotRoutingSnapshot>("content")
                        ?: HotspotRoutingSnapshot()
                    _uiState.update { it.withCoreSnapshot(snapshot) }
                    snapshotWaiter?.takeIf { !it.isCompleted }?.complete(
                        CoreRoutingSnapshot(snapshot, intent.coreTetheringLease()),
                    )
                }
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            app,
            coreStateReceiver,
            IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
            // The response carries the Binder that owns the protected test network.
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
    }

    fun onResume() {
        queryCoreState()
        viewModelScope.launch { refreshShizukuStatus() }
    }

    fun onAction(action: ShizukuAction) {
        when (action) {
            ShizukuAction.RequestPermission -> requestShizukuPermission()
            ShizukuAction.Refresh -> {
                requestCoreSnapshotAsync()
                viewModelScope.launch { refreshShizukuStatus() }
            }
            ShizukuAction.ToggleRouting -> toggleRouting()
            ShizukuAction.ToggleHotspot -> toggleHotspot()
        }
    }

    private fun queryCoreState() {
        MessageHelper.sendMsg2Service(app, AppConfig.MSG_REGISTER_CLIENT, "")
    }

    private fun requestCoreSnapshotAsync() {
        MessageHelper.sendMsg2Service(app, AppConfig.MSG_QUERY_HOTSPOT_CONFIG, "")
    }

    private suspend fun refreshShizukuStatus() {
        val status = getShizukuStatus()
        _uiState.update { it.copy(shizukuStatus = status) }
        if (status != ShizukuStatus.READY) {
            clearServiceState()
            return
        }
        if (tetheringService == null) bindUserService() else refreshTetheringStatus()
    }

    private suspend fun getShizukuStatus(): ShizukuStatus = withContext(Dispatchers.IO) {
        if (!shizukuCall("check Binder", false) { Shizuku.pingBinder() }) {
            return@withContext if (isPackageInstalled(ShizukuProvider.MANAGER_APPLICATION_ID)) {
                ShizukuStatus.NOT_RUNNING
            } else {
                ShizukuStatus.NOT_INSTALLED
            }
        }
        if (shizukuCall("check server version", true) { Shizuku.isPreV11() }) {
            return@withContext ShizukuStatus.UNSUPPORTED
        }
        val permissionGranted = shizukuCall("check permission", false) {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
        if (permissionGranted) return@withContext ShizukuStatus.READY

        if (shizukuCall("check permission rationale", false) {
                Shizuku.shouldShowRequestPermissionRationale()
            }) {
            ShizukuStatus.PERMISSION_DENIED
        } else {
            ShizukuStatus.PERMISSION_REQUIRED
        }
    }

    private fun requestShizukuPermission() {
        viewModelScope.launch {
            when (val status = getShizukuStatus()) {
                ShizukuStatus.PERMISSION_REQUIRED -> runCatching {
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                }.onFailure {
                    logUiFailure("permission", "request permission", it)
                    toastError(R.string.shizuku_operation_failed)
                    refreshShizukuStatus()
                }

                ShizukuStatus.READY -> refreshShizukuStatus()
                else -> {
                    toastError(status.statusRes)
                    refreshShizukuStatus()
                }
            }
        }
    }

    private fun bindUserService() {
        if (_uiState.value.operation == TetheringOperation.CONNECTING || tetheringService != null) return
        _uiState.update { it.copy(operation = TetheringOperation.CONNECTING) }

        runCatching {
            Shizuku.bindUserService(userServiceArgs, userServiceConnection)
            bindingTimeout = viewModelScope.launch {
                delay(USER_SERVICE_BIND_TIMEOUT_MS)
                if (tetheringService == null) {
                    runCatching { Shizuku.unbindUserService(userServiceArgs, userServiceConnection, false) }
                        .onFailure { logUiFailure("connection", "unbind timed-out UserService", it) }
                    clearServiceState()
                    toastError(R.string.shizuku_operation_failed)
                }
            }
        }.onFailure {
            clearServiceState()
            logUiFailure("connection", "bind UserService", it)
            toastError(R.string.shizuku_operation_failed)
        }
    }

    private fun refreshTetheringStatus() {
        if (_uiState.value.operation.isToggleInProgress) return
        if (_uiState.value.operation == TetheringOperation.CHECKING) {
            statusRefreshPending = true
            return
        }
        val service = tetheringService ?: run {
            clearServiceState()
            return
        }
        statusRefreshPending = false
        val generation = cancelCurrentOperation()
        _uiState.update { it.copy(operation = TetheringOperation.CHECKING) }
        operationJob = viewModelScope.launch {
            val (ipv6Enabled, status) = withContext(Dispatchers.IO) {
                val enabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED) == true
                enabled to try {
                    service.getStatus(enabled)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    logUiFailure("status", "read UserService status", error)
                    TetheringStatusSnapshot(
                        routingState = ShizukuTetheringService.ROUTING_STATE_ERROR,
                        routingDetail = "",
                        activeTetheringTypes = ShizukuTetheringService.TETHERING_TYPES_UNKNOWN,
                        ipv6TetheringTypes = ShizukuTetheringService.TETHERING_TYPES_UNKNOWN,
                        warning = ShizukuTetheringService.RESULT_OK,
                    )
                }
            }
            if (generation != operationGeneration) return@launch
            _uiState.update { it.withTetheringStatus(status, ipv6Enabled) }
            operationJob = null
            if (status.warning == ShizukuTetheringService.RESULT_UNPROTECTED_UPSTREAM) {
                localizedContext.toastInfo(R.string.shizuku_tethering_wrong_upstream)
            }
            if (statusRefreshPending) refreshTetheringStatus()
        }
    }

    private fun toggleRouting() {
        val operation = _uiState.value.operationFor(ShizukuAction.ToggleRouting) ?: return
        val enable = operation == TetheringOperation.STARTING_ROUTING
        launchOperation(operation) { service ->
            val result = if (enable) {
                startRouting(service)
            } else {
                stopRouting(service)
            }
            if (result != ShizukuTetheringService.RESULT_OK) {
                showRoutingError(result)
                return@launchOperation
            }
            toastSuccess(
                if (enable) R.string.shizuku_routing_enabled
                else R.string.shizuku_routing_disabled
            )
        }
    }

    private fun toggleHotspot() {
        val operation = _uiState.value.operationFor(ShizukuAction.ToggleHotspot) ?: return
        val enable = operation == TetheringOperation.STARTING_HOTSPOT
        launchOperation(operation) { service ->
            var routingStartedHere = false
            if (enable && !_uiState.value.routingActive) {
                val routingResult = startRouting(service)
                if (routingResult != ShizukuTetheringService.RESULT_OK) {
                    showRoutingError(routingResult)
                    return@launchOperation
                }
                routingStartedHere = true
            }

            val result = callService("set Wi-Fi hotspot enabled=$enable") {
                service.setWifiHotspotEnabled(enable)
            }
            if (result != ShizukuTetheringService.RESULT_OK) {
                if (routingStartedHere) {
                    stopRouting(service)
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
        if (_uiState.value.operation.isToggleInProgress) return
        val service = tetheringService ?: return
        val generation = cancelCurrentOperation()
        _uiState.update { it.copy(operation = operation) }
        operationJob = viewModelScope.launch {
            try {
                action(service)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                logUiFailure("operation", operation.name, error)
                toastError(R.string.shizuku_operation_failed)
            } finally {
                if (generation == operationGeneration) {
                    operationJob = null
                    _uiState.update { it.copy(operation = TetheringOperation.NONE) }
                    refreshTetheringStatus()
                }
            }
        }
    }

    private suspend fun callService(operation: String, action: () -> Int): Int = withContext(Dispatchers.IO) {
        // A reopened screen must not overtake an old screen's pending remote operation and its
        // token bookkeeping. This process-wide ordering never blocks the UI thread.
        synchronized(serviceOperationLock) {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                logUiFailure("Binder", operation, error)
                ShizukuTetheringService.RESULT_INTERNAL_ERROR
            }
        }
    }

    private suspend fun startRouting(service: IShizukuTetheringService): Int {
        val core = requestCoreSnapshot() ?: run {
            toastError(R.string.shizuku_routing_snapshot_timeout)
            return ShizukuTetheringService.RESULT_INTERNAL_ERROR
        }
        val snapshot = core.snapshot
        val parameters = try {
            withContext(Dispatchers.Default) {
                HotspotRoutingConfig.parametersFromSnapshot(snapshot)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            logUiFailure("configuration", "prepare tethering parameters", error)
            toastError(R.string.shizuku_routing_snapshot_timeout)
            return ShizukuTetheringService.RESULT_ROUTING_FAILED
        }
        val coreLease = core.lease ?: run {
            toastError(R.string.shizuku_operation_failed)
            return ShizukuTetheringService.RESULT_ROUTING_FAILED
        }

        return callService("start routing") {
            val previousToken = MmkvManager.decodeSettingsString(AppConfig.PREF_SHIZUKU_SYNC_TOKEN).orEmpty()
            val token = Utils.getUuid()
            MmkvManager.encodeSettings(AppConfig.PREF_SHIZUKU_SYNC_TOKEN, token)
            var started = false
            try {
                service.startRouting(
                    parameters.useHev, parameters.profileName, parameters.dnsServers.toTypedArray(),
                    parameters.ipv6Enabled, parameters.xudpKey, token, parameters.launchId, coreLease,
                ).also { started = it == ShizukuTetheringService.RESULT_OK }
            } finally {
                if (!started) {
                    MmkvManager.encodeSettings(AppConfig.PREF_SHIZUKU_SYNC_TOKEN, previousToken)
                }
            }
        }
    }

    private suspend fun stopRouting(service: IShizukuTetheringService): Int = callService("stop routing") {
        // Binder calls cannot be cancelled once submitted. Commit their bookkeeping in the same
        // IO block, before dispatching back to a ViewModel which may already have been cleared.
        service.stopRouting().also { result ->
            if (result == ShizukuTetheringService.RESULT_OK) MmkvManager.encodeSettings(AppConfig.PREF_SHIZUKU_SYNC_TOKEN, "")
        }
    }

    private suspend fun requestCoreSnapshot(): CoreRoutingSnapshot? {
        val waiter = CompletableDeferred<CoreRoutingSnapshot>()
        snapshotWaiter?.cancel()
        snapshotWaiter = waiter
        requestCoreSnapshotAsync()
        return withTimeoutOrNull(CORE_SNAPSHOT_TIMEOUT_MS) { waiter.await() }
            .also { if (snapshotWaiter === waiter) snapshotWaiter = null }
    }

    private fun showRoutingError(result: Int) {
        toastError(
            getString(
                R.string.shizuku_routing_operation_failed,
                result,
                getString(R.string.shizuku_operation_failed)
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
        bindingTimeout?.cancel()
        bindingTimeout = null
        cancelCurrentOperation()
        statusRefreshPending = false
        tetheringService = null
        _uiState.update { it.withServiceConnection(false) }
    }

    private fun isPackageInstalled(packageName: String): Boolean = try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        app.packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (error: Throwable) {
        logUiFailure("status", "query Shizuku package", error)
        false
    }

    private fun <T> shizukuCall(operation: String, default: T, action: () -> T): T = try {
        action()
    } catch (error: Throwable) {
        logUiFailure("status", operation, error)
        default
    }

    private fun logUiFailure(phase: String, operation: String, error: Throwable) {
        LogUtil.e(
            AppConfig.TAG,
            "Shizuku tethering failure: component=ui phase=$phase operation=$operation",
            error,
        )
    }

    override fun onCleared() {
        cancelCurrentOperation()
        snapshotWaiter?.cancel()
        runCatching { app.unregisterReceiver(coreStateReceiver) }
            .onFailure { logUiFailure("cleanup", "unregister core-state receiver", it) }
        runCatching { Shizuku.removeBinderReceivedListener(binderReceivedListener) }
            .onFailure { logUiFailure("cleanup", "remove Binder-received listener", it) }
        runCatching { Shizuku.removeBinderDeadListener(binderDeadListener) }
            .onFailure { logUiFailure("cleanup", "remove Binder-dead listener", it) }
        runCatching { Shizuku.removeRequestPermissionResultListener(permissionResultListener) }
            .onFailure { logUiFailure("cleanup", "remove permission listener", it) }
        runCatching { tetheringService?.setStatusListener(null) }
            .onFailure { logUiFailure("cleanup", "clear status listener", it) }
        if (tetheringService != null || _uiState.value.operation == TetheringOperation.CONNECTING) {
            runCatching {
                // The service owns the live TUN and tethering upstream. Screen teardown must
                // never destroy it; only the explicit Disable action may stop the datapath.
                Shizuku.unbindUserService(userServiceArgs, userServiceConnection, false)
            }.onFailure { logUiFailure("cleanup", "unbind UserService", it) }
        }
        tetheringService = null
    }

    private data class CoreRoutingSnapshot(
        val snapshot: HotspotRoutingSnapshot,
        val lease: ICoreTetheringLease?,
    )

    companion object {
        private val serviceOperationLock = Any()
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
        private const val CORE_SNAPSHOT_TIMEOUT_MS = 5_000L
        private const val USER_SERVICE_BIND_TIMEOUT_MS = 5_000L
    }
}
