package com.v2ray.ang.ui.server

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.AetherCore
import com.v2ray.ang.core.AetherCoreManager
import com.v2ray.ang.core.AetherIdentity
import com.v2ray.ang.core.AetherIdentityManager
import com.v2ray.ang.core.AetherIdentityStatus
import com.v2ray.ang.core.AetherScanResult
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.AetherProtocol
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

sealed interface AetherScanState {
    data object Idle : AetherScanState
    data object Scanning : AetherScanState
    data class Found(val result: AetherScanResult) : AetherScanState
    data object NotFound : AetherScanState
}

/** What a check of the WARP keys a profile needs ends with, for the screen to act on once. */
sealed interface AetherKeysCheck {
    /** Every key the profile needs is there: the save goes on. */
    data object SaveReady : AetherKeysCheck

    /** A key the profile needs, or its scan when [scan], is missing: the screen asks whether to get it first. */
    data class Missing(val scan: Boolean) : AetherKeysCheck
}

sealed interface AetherLogText {
    data class Raw(val value: String) : AetherLogText
    data class Resource(@StringRes val id: Int, val args: List<String> = emptyList()) : AetherLogText
}

data class AetherLogEntry(
    val id: Long,
    val priority: Int,
    val text: AetherLogText,
)

class ServerAetherViewModel(
    application: Application,
    private val source: AetherEditorSource,
) : BaseViewModel(application) {

    private val _isCoreAvailable = MutableStateFlow(true)
    val isCoreAvailable: StateFlow<Boolean> = _isCoreAvailable.asStateFlow()

    /** Whether this build ships the Psiphon client; false until looked up, so nothing claims it does. */
    private val _isPsiphonAvailable = MutableStateFlow(false)
    val isPsiphonAvailable: StateFlow<Boolean> = _isPsiphonAvailable.asStateFlow()

    /** Whether this build ships the pluggable transport Tor's bridges run through; false until looked up. */
    private val _isTorTransportsAvailable = MutableStateFlow(false)
    val isTorTransportsAvailable: StateFlow<Boolean> = _isTorTransportsAvailable.asStateFlow()

    /** The exit countries Psiphon can be asked for, from the app's server list; empty until looked up or without a list. */
    private val _psiphonRegions = MutableStateFlow<List<String>>(emptyList())
    val psiphonRegions: StateFlow<List<String>> = _psiphonRegions.asStateFlow()

    /** The Aether listen port the command of a profile is built on; the default until it is read from the settings. */
    private val _listenPort = MutableStateFlow(AppConfig.PORT_AETHER_SOCKS.toInt())
    val listenPort: StateFlow<Int> = _listenPort.asStateFlow()

    private val _scanState = MutableStateFlow<AetherScanState>(AetherScanState.Idle)
    val scanState: StateFlow<AetherScanState> = _scanState.asStateFlow()

    /** The daemon's live Aether session, if any; a scan must not open a second tunnel on its key. */
    private val _session = MutableStateFlow<AetherSession?>(null)
    val session: StateFlow<AetherSession?> = _session.asStateFlow()

    private val _log = MutableStateFlow<List<AetherLogEntry>>(emptyList())
    val log: StateFlow<List<AetherLogEntry>> = _log.asStateFlow()

    private val _keysCheck = MutableStateFlow<AetherKeysCheck?>(null)
    val keysCheck: StateFlow<AetherKeysCheck?> = _keysCheck.asStateFlow()

    private val nextLogId = AtomicLong()
    private var scanJob: Job? = null
    private var reportedIdentity: AetherIdentityStatus? = null

    private val isBusy: Boolean
        get() = _scanState.value == AetherScanState.Scanning

    init {
        viewModelScope.launch { _isCoreAvailable.value = source.isCoreAvailable() }
        viewModelScope.launch { _isPsiphonAvailable.value = source.isPsiphonAvailable() }
        viewModelScope.launch { _isTorTransportsAvailable.value = source.isTorTransportsAvailable() }
        viewModelScope.launch { _psiphonRegions.value = source.psiphonRegions() }
        viewModelScope.launch { _listenPort.value = source.listenPort() }
        refreshSession()
    }

    fun refreshSession() {
        viewModelScope.launch { _session.value = source.activeSession() }
    }

    /**
     * Scans for an endpoint of [profile]. Unless [anyway], a scan whose protocol lacks a WARP key does not start, and
     * [keysCheck] asks first whether to get the key; the core would register it on its own.
     */
    fun scan(profile: ProfileItem, anyway: Boolean = false) {
        if (isBusy) return
        _scanState.value = AetherScanState.Scanning
        scanJob = viewModelScope.launch {
            try {
                // Checked at the tap: a second tunnel on the key of a live session would disturb it.
                val session = source.activeSession()
                _session.value = session
                if (session?.disturbedByScanOf(AetherProtocol.fromString(profile.aetherProtocol)) == true) {
                    _scanState.value = AetherScanState.Idle
                    append(Log.WARN, AetherLogText.Resource(R.string.aether_scan_blocked))
                    return@launch
                }
                if (!anyway) {
                    val needed = AetherIdentityManager.filesNeededBy(AetherCoreManager.buildArguments(profile, 0, scan = true))
                    if (source.missingKeys(needed).isNotEmpty()) {
                        _scanState.value = AetherScanState.Idle
                        _keysCheck.value = AetherKeysCheck.Missing(scan = true)
                        return@launch
                    }
                }
                append(Log.INFO, AetherLogText.Resource(R.string.aether_log_scan_started))
                val result = source.scan(profile, ::appendOutput)
                _scanState.value = result?.let(AetherScanState::Found) ?: AetherScanState.NotFound
                append(if (result == null) Log.WARN else Log.INFO, scanOutcome(result))
                reportIdentity(source.identityStatus(AetherProtocol.fromString(profile.aetherProtocol)), onlyChanges = true)
            } finally {
                // A cancelled scan stops being one here, once its core and whatever that started have ended.
                if (_scanState.value == AetherScanState.Scanning) _scanState.value = AetherScanState.Idle
            }
        }
    }

    /** Stops the scan, which shows as running until its core, with whatever that started, has ended. */
    fun cancelScan() {
        val job = scanJob ?: return
        if (_scanState.value != AetherScanState.Scanning || job.isCancelled) return
        append(Log.WARN, AetherLogText.Resource(R.string.aether_log_scan_cancelled))
        job.cancel()
    }

    fun onScanHandled() {
        if (_scanState.value != AetherScanState.Scanning) {
            _scanState.value = AetherScanState.Idle
        }
    }

    /**
     * Looks whether the WARP keys [profile] needs are there before it is saved: [keysCheck] then says
     * [AetherKeysCheck.SaveReady], or [AetherKeysCheck.Missing] for the screen to ask first. A profile that
     * runs Psiphon or Tor alone needs none.
     */
    fun checkKeysBeforeSave(profile: ProfileItem) {
        viewModelScope.launch {
            val needed = AetherIdentityManager.filesNeededBy(AetherCore.of(profile, _listenPort.value).arguments)
            _keysCheck.value = if (source.missingKeys(needed).isEmpty()) AetherKeysCheck.SaveReady else AetherKeysCheck.Missing(scan = false)
        }
    }

    fun onKeysCheckHandled() {
        _keysCheck.value = null
    }

    fun showIdentity(protocol: AetherProtocol) {
        viewModelScope.launch { reportIdentity(source.identityStatus(protocol), onlyChanges = true) }
    }

    /** Forgets what Psiphon has learned, unless a session runs on it; the outcome goes to the log. */
    fun clearPsiphonData() {
        if (isBusy) return
        viewModelScope.launch {
            // Checked again at the tap, the session may have come up after the screen opened.
            val session = source.activeSession()
            _session.value = session
            if (session != null) {
                append(Log.WARN, AetherLogText.Resource(R.string.aether_psiphon_clear_blocked))
                return@launch
            }
            val cleared = source.clearPsiphonData()
            append(
                if (cleared) Log.INFO else Log.ERROR,
                AetherLogText.Resource(if (cleared) R.string.aether_log_psiphon_cleared else R.string.aether_log_psiphon_clear_failed),
            )
        }
    }

    private fun reportIdentity(status: AetherIdentityStatus, onlyChanges: Boolean) {
        if (onlyChanges && status == reportedIdentity) return
        reportedIdentity = status
        identityLines(status).forEach { append(Log.INFO, it) }
    }

    private fun appendOutput(line: String) {
        val message = AetherCoreManager.outputMessage(line)
        if (message.isNotEmpty()) {
            append(AetherCoreManager.outputPriority(line.trim()), AetherLogText.Raw(message))
        }
    }

    private fun append(priority: Int, text: AetherLogText) {
        val entry = AetherLogEntry(nextLogId.incrementAndGet(), priority, text)
        _log.update { (it + entry).takeLast(LOG_CAPACITY) }
    }

    companion object {
        internal const val LOG_CAPACITY = 500
        private const val DEVICE_ID_LENGTH = 8

        internal fun scanOutcome(result: AetherScanResult?): AetherLogText.Resource {
            val innerHop = result?.innerHop
            return when {
                result == null -> AetherLogText.Resource(R.string.aether_scan_failed)
                innerHop != null -> AetherLogText.Resource(
                    R.string.aether_log_scan_found_hops,
                    listOf(result.endpoint.toString(), innerHop.toString())
                )

                else -> AetherLogText.Resource(R.string.aether_log_scan_found, listOf(result.endpoint.toString()))
            }
        }

        internal fun identityLines(status: AetherIdentityStatus): List<AetherLogText.Resource> = when (status.protocol) {
            AetherProtocol.MASQUE -> listOf(
                keyLine(status.primary, R.string.aether_log_masque_key_ready, R.string.aether_log_masque_key_missing)
            )

            AetherProtocol.WIREGUARD -> listOf(
                keyLine(status.primary, R.string.aether_log_wireguard_key_ready, R.string.aether_log_wireguard_key_missing)
            )

            AetherProtocol.GOOL, AetherProtocol.MIM -> listOf(
                keyLine(status.primary, R.string.aether_log_outer_key_ready, R.string.aether_log_outer_key_missing),
                keyLine(status.secondary, R.string.aether_log_inner_key_ready, R.string.aether_log_inner_key_missing),
            )
        }

        /** The log line of a key: [ready] with its device and addresses, or [missing] when there is none. */
        internal fun keyLine(identity: AetherIdentity?, @StringRes ready: Int, @StringRes missing: Int): AetherLogText.Resource =
            if (identity == null) {
                AetherLogText.Resource(missing)
            } else {
                AetherLogText.Resource(ready, listOf(shortDeviceId(identity.deviceId), identity.ipv4, identity.ipv6))
            }

        private fun shortDeviceId(deviceId: String): String =
            if (deviceId.length > DEVICE_ID_LENGTH) deviceId.take(DEVICE_ID_LENGTH) + "…" else deviceId
    }
}
