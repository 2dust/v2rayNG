package com.v2ray.ang.ui.cloudflare

import android.app.Application
import com.v2ray.ang.R
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.CloudflareAssistant
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class CloudflareViewModel(application: Application) : BaseViewModel(application) {

    data class UiState(
        val selectedName: String? = null,
        val selectedAddress: String? = null,
        val isCloudflare: Boolean = false,
        val antiBlock: Boolean = false,
        val scanning: Boolean = false,
        val results: List<CloudflareAssistant.ScanResult> = emptyList(),
        val chosenAddress: String? = null,
        val probeFinished: Boolean = false,
        val probeLatencyMs: Long? = null,
        val autoEnabled: Boolean = true,
        val autoOutcome: AutoOutcome = AutoOutcome.None,
        /** How many configs the Cloudflare tab currently holds. */
        val groupCount: Int = 0,
        /** The config the last fix produced, with its real measured latency. */
        val fixedName: String? = null,
        val fixedGuid: String? = null,
        val fixedPingMs: Long? = null,
    )

    /** What the hands-off pass decided, so the screen can explain itself. */
    enum class AutoOutcome { None, Healthy, Unblocked, SwitchedEdge }

    private val groupName: String = application.getString(R.string.cloudflare_group_name)
    private val copySuffix: String = application.getString(R.string.cloudflare_copy_suffix)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Re-reads the selected config and the anti-block state, e.g. after returning to the screen. */
    fun refresh() {
        val config = CloudflareAssistant.selectedConfig()
        _state.value = _state.value.copy(
            selectedName = config?.remarks?.takeIf { it.isNotBlank() },
            selectedAddress = config?.server,
            isCloudflare = CloudflareAssistant.isCloudflareConfig(config),
            antiBlock = CloudflareAssistant.isAntiBlockEnabled(),
            autoEnabled = CloudflareAssistant.isAutoEnabled(),
            groupCount = CloudflareAssistant.groupSize(groupName),
        )
    }

    fun setAutoEnabled(enabled: Boolean) {
        CloudflareAssistant.setAutoEnabled(enabled)
        _state.value = _state.value.copy(autoEnabled = enabled)
    }

    /**
     * Runs once when the screen opens: measures the selected Cloudflare config and repairs it only
     * when it is broken, so a working setup is never touched.
     */
    fun autoCheckIfNeeded() {
        if (autoCheckStarted) return
        autoCheckStarted = true

        if (!CloudflareAssistant.isAutoEnabled()) return
        val config = CloudflareAssistant.selectedConfig() ?: return
        if (!CloudflareAssistant.isCloudflareConfig(config)) return

        val app = getApplication<Application>()
        launchLoading {
            val report = CloudflareAssistant.autoRepair(app, groupName, copySuffix)
            val outcome = when {
                report.newConfigGuid != null -> AutoOutcome.SwitchedEdge
                report.unblocked -> AutoOutcome.Unblocked
                report.latencyMs != null -> AutoOutcome.Healthy
                else -> AutoOutcome.None
            }
            _state.value = _state.value.copy(
                probeFinished = report.latencyMs != null || report.unblocked,
                probeLatencyMs = report.latencyMs,
                antiBlock = CloudflareAssistant.isAntiBlockEnabled(),
                autoOutcome = outcome,
                fixedName = report.newConfigName,
                fixedGuid = report.newConfigGuid,
                fixedPingMs = if (report.newConfigGuid != null) report.latencyMs else null,
            )
            refresh()
        }
    }

    fun setAntiBlock(enabled: Boolean) {
        CloudflareAssistant.setAntiBlock(enabled)
        _state.value = _state.value.copy(antiBlock = enabled)
    }

    fun scan() {
        if (_state.value.scanning) return
        val config = CloudflareAssistant.selectedConfig()
        // Present the same name the config uses, so the edge answers for the right zone.
        val sni = config?.sni?.takeIf { it.isNotBlank() }
            ?: config?.host?.takeIf { it.isNotBlank() }

        _state.value = _state.value.copy(scanning = true, results = emptyList(), chosenAddress = null)
        launchLoading {
            val results = CloudflareAssistant.scan(sni)
            _state.value = _state.value.copy(
                scanning = false,
                results = results.take(MAX_RESULTS),
                chosenAddress = results.firstOrNull()?.address,
            )
            if (results.isEmpty()) {
                toastError(R.string.toast_cloudflare_scan_failed)
            }
        }
    }

    fun choose(address: String) {
        _state.value = _state.value.copy(chosenAddress = address)
    }

    /**
     * Builds the Cloudflare config for the chosen edge: a copy inside the Cloudflare tab with a
     * real, measured latency. The profile the user picked is left exactly as it was.
     */
    fun applyChosen() {
        val sourceGuid = CloudflareAssistant.selectedGuid()
        val address = _state.value.chosenAddress
        if (sourceGuid == null || address == null) {
            toastError(R.string.toast_cloudflare_no_config)
            return
        }

        val app = getApplication<Application>()
        _state.value = _state.value.copy(fixedPingMs = null)
        launchLoading {
            val report = CloudflareAssistant.buildCloudflareConfig(
                context = app,
                sourceGuid = sourceGuid,
                address = address,
                groupName = groupName,
                remarksSuffix = copySuffix,
            )
            if (report?.newConfigGuid == null) {
                toastError(R.string.toast_failure)
                return@launchLoading
            }
            _state.value = _state.value.copy(
                fixedName = report.newConfigName,
                fixedGuid = report.newConfigGuid,
                fixedPingMs = report.latencyMs,
            )
            toastSuccess(R.string.toast_cloudflare_applied)
            refresh()
        }
    }

    /**
     * "\u06a9\u0627\u0646\u0641\u06cc\u06af \u062f\u06cc\u06af\u0631": anything that is not already in the app, added straight into the Cloudflare tab.
     * Newly added profiles get a real measured ping as well, so the tab never shows a made-up number.
     */
    fun addOtherConfig(text: String) {
        val body = text.trim()
        if (body.isEmpty()) return

        val app = getApplication<Application>()
        val groupId = CloudflareAssistant.ensureGroup(groupName)
        val before = MmkvManager.decodeServerList(groupId).toSet()

        launchLoading {
            withContext(Dispatchers.IO) {
                AngConfigManager.importBatchConfig(body, groupId, append = true)
            }
            val added = MmkvManager.decodeServerList(groupId).filter { it !in before }
            if (added.isEmpty()) {
                toastError(R.string.cloudflare_other_failed)
                return@launchLoading
            }
            added.take(MAX_MEASURE).forEach { guid ->
                CloudflareAssistant.measureRealPing(app, guid)
            }
            SettingsChangeManager.makeSetupGroupTab()
            toastSuccess(R.string.cloudflare_other_saved)
            refresh()
        }
    }

    fun probeSelected() {
        val config = CloudflareAssistant.selectedConfig()
        val host = config?.server
        if (host.isNullOrBlank()) {
            toastError(R.string.toast_cloudflare_no_config)
            return
        }

        launchLoading {
            val latency = CloudflareAssistant.measure(host, config.sni ?: config.host)
            _state.value = _state.value.copy(probeFinished = true, probeLatencyMs = latency)
        }
    }

    private var autoCheckStarted = false

    private companion object {
        const val MAX_RESULTS = 6

        /** Enough to give the user a number without starting a core run per config. */
        const val MAX_MEASURE = 3
    }
}
