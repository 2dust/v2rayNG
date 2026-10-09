package com.v2ray.ang.ui.server

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.R
import com.v2ray.ang.core.AetherCoreManager
import com.v2ray.ang.core.AetherIdentity
import com.v2ray.ang.core.AetherIdentityManager
import com.v2ray.ang.core.AetherKey
import com.v2ray.ang.core.AetherKeys
import com.v2ray.ang.core.AetherKeysSettings
import com.v2ray.ang.enums.AetherFingerprint
import com.v2ray.ang.enums.AetherKeyKind
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/** What the page that gets new WARP keys tells once, beside its log. */
sealed interface AetherKeysNotice {
    /** The new keys are in place, and the cores of the run, the Aether core and the Xray exit it dialled out through, have ended. */
    data object Renewed : AetherKeysNotice

    /** The settings cannot run, for [message]. */
    data class Invalid(@StringRes val message: Int) : AetherKeysNotice
}

/**
 * The page that gets new WARP keys: its settings, which are its own and no profile's, the run of the core that
 * registers the keys, and the log of that run and of the keys in use.
 */
class AetherKeysViewModel(
    application: Application,
    private val source: AetherKeysSource,
) : BaseViewModel(application) {

    private val _isCoreAvailable = MutableStateFlow(true)
    val isCoreAvailable: StateFlow<Boolean> = _isCoreAvailable.asStateFlow()

    /**
     * The settings of the page, null until they are read. Compose state rather than a flow, so that a text field
     * reads back at once what was typed into it; written on the main thread only.
     */
    var settings by mutableStateOf<AetherKeysSettings?>(null)
        private set

    private val _isRenewing = MutableStateFlow(false)
    val isRenewing: StateFlow<Boolean> = _isRenewing.asStateFlow()

    /** The daemon's live Aether session, if any; the keys it uses must not change under it. */
    private val _session = MutableStateFlow<AetherSession?>(null)
    val session: StateFlow<AetherSession?> = _session.asStateFlow()

    private val _log = MutableStateFlow<List<AetherLogEntry>>(emptyList())
    val log: StateFlow<List<AetherLogEntry>> = _log.asStateFlow()

    private val _notice = MutableStateFlow<AetherKeysNotice?>(null)
    val notice: StateFlow<AetherKeysNotice?> = _notice.asStateFlow()

    private val nextLogId = AtomicLong()
    private var renewJob: Job? = null

    /** The identity of each key file as the log last showed it. */
    private val shownKeys = mutableMapOf<String, AetherIdentity?>()

    /** One save at a time, each of the settings as they are by then, so that the last one written is the latest. */
    private val saving = Mutex()

    init {
        viewModelScope.launch { _isCoreAvailable.value = source.isCoreAvailable() }
        viewModelScope.launch { settings = source.loadSettings() }
        viewModelScope.launch { showKeys(source.keys(), onlyChanges = false) }
        refreshSession()
    }

    fun refreshSession() {
        viewModelScope.launch { _session.value = source.activeSession() }
    }

    fun setKind(kind: AetherKeyKind) = update { it.copy(kind = kind) }

    fun setEnrollAddress(address: String) = update { it.copy(enrollAddress = address) }

    fun setEch(on: Boolean) = update { it.copy(ech = on) }

    fun setEchDns(dns: String) = update { it.copy(echDns = dns) }

    fun setEchDomain(domain: String) = update { it.copy(echDomain = domain) }

    fun setFingerprint(fingerprint: AetherFingerprint) = update { it.copy(fingerprint = fingerprint) }

    fun setFinalMask(finalMask: String) = update { it.copy(finalMask = finalMask) }

    fun setDialMode(dialMode: String) = update { it.copy(dialMode = dialMode) }

    /** Takes [command] as written by hand; written back to the one the settings give, it follows the settings again. */
    fun setCommand(command: String) = update { it.copy(command = if (command.trim() == AetherKeys.builtCommand(it)) "" else command) }

    /** Drops the command written by hand, so that the settings make the command again. */
    fun useSettings() = update { it.copy(command = "") }

    private fun update(change: (AetherKeysSettings) -> AetherKeysSettings) {
        // What a run was started with stays on show while it runs.
        if (_isRenewing.value) return
        val current = settings ?: return
        val next = change(current)
        if (next == current) return
        settings = next
        viewModelScope.launch {
            saving.withLock { settings?.let { source.saveSettings(it) } }
        }
    }

    /**
     * Gets new keys as the settings say: registers them with a run of the core, which dials out through the exit-node
     * of the settings, and puts them in place of the keys in use once all of them are ready. A session that uses one
     * of those keys keeps them; the other keys can change under it.
     */
    fun getKeys() {
        if (_isRenewing.value) return
        val current = settings ?: return
        AetherKeys.problem(current)?.let { problem ->
            _notice.value = AetherKeysNotice.Invalid(messageOf(problem))
            return
        }
        val arguments = AetherKeys.runArguments(current)
        val kind = AetherKeys.kindOf(arguments) ?: return
        _isRenewing.value = true
        renewJob = viewModelScope.launch {
            try {
                // Checked at the tap, the session may have come up after the page opened.
                val session = source.activeSession()
                _session.value = session
                if (session?.usesKeysOf(kind) == true) {
                    append(Log.WARN, AetherLogText.Resource(R.string.aether_renew_blocked))
                    return@launch
                }
                append(Log.INFO, AetherLogText.Resource(R.string.aether_log_key_renewing))
                val keys = source.renew(kind, arguments, current.exit, ::appendOutput)
                if (keys == null) {
                    append(Log.ERROR, AetherLogText.Resource(R.string.aether_log_key_renew_failed))
                } else {
                    // By now the core and the exit it dialled out through have ended, and the new keys are in place.
                    append(Log.INFO, AetherLogText.Resource(R.string.aether_log_key_renewed))
                    showKeys(keys, onlyChanges = false)
                    _notice.value = AetherKeysNotice.Renewed
                }
            } catch (e: CancellationException) {
                // By now the core and what it started have ended. A run cancelled once every new key was ready has put
                // them in place all the same, so the keys in use are shown where they changed.
                withContext(NonCancellable) { showKeys(source.keys(), onlyChanges = true) }
                throw e
            } finally {
                _isRenewing.value = false
            }
        }
    }

    /**
     * Stops getting new keys: the core that registers them ends, with whatever it started, and until then the run
     * shows as going on. The keys in use stay, unless every new key was ready already; see
     * [com.v2ray.ang.core.AetherIdentityManager.renew].
     */
    fun cancel() {
        val job = renewJob ?: return
        if (!_isRenewing.value || job.isCancelled) return
        append(Log.WARN, AetherLogText.Resource(R.string.aether_log_key_renew_cancelled))
        job.cancel()
    }

    fun onNoticeShown() {
        _notice.value = null
    }

    /** Shows each of [keys], or with [onlyChanges] only those whose identity is not the one the log showed last. */
    private fun showKeys(keys: List<AetherKey>, onlyChanges: Boolean) {
        for (key in keys) {
            if (onlyChanges && key.file in shownKeys && shownKeys[key.file] == key.identity) continue
            shownKeys[key.file] = key.identity
            append(Log.INFO, keyLine(key))
        }
    }

    private fun appendOutput(line: String) {
        val message = AetherCoreManager.outputMessage(line)
        if (message.isNotEmpty()) {
            append(AetherCoreManager.outputPriority(line.trim()), AetherLogText.Raw(message))
        }
    }

    private fun append(priority: Int, text: AetherLogText) {
        val entry = AetherLogEntry(nextLogId.incrementAndGet(), priority, text)
        _log.update { (it + entry).takeLast(ServerAetherViewModel.LOG_CAPACITY) }
    }

    companion object {
        /** What tells the user why settings cannot run. */
        @StringRes
        internal fun messageOf(problem: AetherKeys.Problem): Int = when (problem) {
            AetherKeys.Problem.INVALID_ENROLL_ADDRESS -> R.string.aether_keys_invalid_enroll_address
            AetherKeys.Problem.INVALID_ECH_DNS -> R.string.aether_invalid_ech_dns
            AetherKeys.Problem.INVALID_ECH_DOMAIN -> R.string.aether_invalid_ech_domain
            AetherKeys.Problem.INVALID_FINAL_MASK -> R.string.aether_lab_exit_final_mask
            AetherKeys.Problem.INVALID_COMMAND -> R.string.aether_keys_invalid_command
        }

        /** The log line of [key], named after its file: its device and addresses, or that there is none yet. */
        internal fun keyLine(key: AetherKey): AetherLogText.Resource = when (key.file) {
            AetherIdentityManager.WIREGUARD_FILE ->
                ServerAetherViewModel.keyLine(key.identity, R.string.aether_log_wireguard_key_ready, R.string.aether_log_wireguard_key_missing)

            AetherIdentityManager.WIREGUARD_INNER_FILE ->
                ServerAetherViewModel.keyLine(key.identity, R.string.aether_log_wireguard_inner_key_ready, R.string.aether_log_wireguard_inner_key_missing)

            AetherIdentityManager.MASQUE_FILE ->
                ServerAetherViewModel.keyLine(key.identity, R.string.aether_log_masque_key_ready, R.string.aether_log_masque_key_missing)

            else ->
                ServerAetherViewModel.keyLine(key.identity, R.string.aether_log_masque_inner_key_ready, R.string.aether_log_masque_inner_key_missing)
        }
    }
}
