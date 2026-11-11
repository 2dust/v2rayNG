package com.v2ray.ang.ui.cloudflare

import android.app.Application
import com.v2ray.ang.R
import com.v2ray.ang.handler.CloudflareAssistant
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    )

    /** What the hands-off pass decided, so the screen can explain itself. */
    enum class AutoOutcome { None, Healthy, Unblocked, SwitchedEdge }

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

        launchLoading {
            val report = CloudflareAssistant.autoRepair()
            val outcome = when {
                report.switchedAddress != null -> AutoOutcome.SwitchedEdge
                report.unblocked -> AutoOutcome.Unblocked
                report.latencyMs != null -> AutoOutcome.Healthy
                else -> AutoOutcome.None
            }
            _state.value = _state.value.copy(
                probeFinished = report.latencyMs != null || report.unblocked,
                probeLatencyMs = report.latencyMs,
                antiBlock = CloudflareAssistant.isAntiBlockEnabled(),
                autoOutcome = outcome,
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

    fun applyChosen() {
        val guid = CloudflareAssistant.selectedGuid()
        val address = _state.value.chosenAddress
        if (guid == null || address == null) {
            toastError(R.string.toast_cloudflare_no_config)
            return
        }

        if (CloudflareAssistant.applyAddress(guid, address)) {
            toastSuccess(R.string.toast_cloudflare_applied)
            refresh()
        } else {
            toastError(R.string.toast_failure)
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
    }
}
