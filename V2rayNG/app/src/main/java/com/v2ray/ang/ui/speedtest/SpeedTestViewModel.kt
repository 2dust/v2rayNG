package com.v2ray.ang.ui.speedtest

import android.app.Application
import com.v2ray.ang.R
import com.v2ray.ang.handler.ConfigBenchmark
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.NetworkOperator
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ranking screen: real ping for every config, real throughput for the connected one, and the
 * suggestions that belong to the network the phone is on right now.
 */
class SpeedTestViewModel(application: Application) : BaseViewModel(application) {

    data class Row(
        val guid: String,
        val name: String,
        val pingMs: Long,
        val downloadMbps: Double?,
        val uploadMbps: Double?,
        val isSelected: Boolean,
    )

    data class UiState(
        val operator: String = NetworkOperator.UNKNOWN,
        val rows: List<Row> = emptyList(),
        val suggestions: List<Row> = emptyList(),
        val testing: Boolean = false,
        val progress: String? = null,
        val throughputBusy: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun refresh() {
        load()
    }

    /** Pings every config through the core and stores the results for this network. */
    fun pingAll() {
        if (_state.value.testing) return
        val guids = MmkvManager.decodeAllServerList()
        if (guids.isEmpty()) {
            toastError(R.string.speedtest_no_configs)
            return
        }

        val operator = NetworkOperator.current(getApplication<Application>())
        _state.value = _state.value.copy(
            testing = true,
            progress = "0 / ${guids.size}",
        )

        launchLoading {
            val pings = ConfigBenchmark.pingConfigs(getApplication<Application>(), guids) { done, total ->
                _state.value = _state.value.copy(progress = "$done / $total")
            }
            val now = System.currentTimeMillis()
            val fresh = pings.map { (guid, latency) ->
                ConfigBenchmark.Measurement(guid = guid, pingMs = latency, measuredAt = now)
            }
            val merged = ConfigBenchmark.merge(ConfigBenchmark.load(operator), fresh)
            ConfigBenchmark.store(operator, merged.values)
            _state.value = _state.value.copy(testing = false, progress = null)
            load()
        }
    }

    /**
     * Measures upload and download through the running tunnel. Real throughput needs the core up, so
     * this records the numbers against the connected config and asks to connect otherwise.
     */
    fun testThroughput() {
        if (_state.value.throughputBusy) return
        val guid = MmkvManager.getSelectServer()
        if (guid.isNullOrBlank()) {
            toastError(R.string.speedtest_no_configs)
            return
        }

        val operator = NetworkOperator.current(getApplication<Application>())
        _state.value = _state.value.copy(throughputBusy = true)

        launchLoading {
            val throughput = ConfigBenchmark.measureThroughput()
            _state.value = _state.value.copy(throughputBusy = false)
            if (throughput.downloadMbps == null && throughput.uploadMbps == null) {
                toastError(R.string.speedtest_needs_connection)
                return@launchLoading
            }

            val merged = ConfigBenchmark.merge(
                ConfigBenchmark.load(operator),
                listOf(
                    ConfigBenchmark.Measurement(
                        guid = guid,
                        downloadMbps = throughput.downloadMbps,
                        uploadMbps = throughput.uploadMbps,
                        measuredAt = System.currentTimeMillis(),
                    )
                )
            )
            ConfigBenchmark.store(operator, merged.values)
            toastSuccess(R.string.speedtest_done)
            load()
        }
    }

    private fun load() {
        val operator = NetworkOperator.current(getApplication<Application>())
        val measurements = ConfigBenchmark.load(operator)
        val selected = MmkvManager.getSelectServer()

        val rows = MmkvManager.decodeAllServerList().mapNotNull { guid ->
            val config = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
            val measurement = measurements[guid]
            Row(
                guid = guid,
                name = config.remarks.ifBlank { config.getServerAddressAndPort() },
                pingMs = measurement?.pingMs ?: ConfigBenchmark.NOT_MEASURED,
                downloadMbps = measurement?.downloadMbps,
                uploadMbps = measurement?.uploadMbps,
                isSelected = guid == selected,
            )
        }.sortedWith(
            compareBy<Row>(
                { if (it.pingMs > 0) it.pingMs else Long.MAX_VALUE },
                { it.name.lowercase() }
            )
        )

        val suggested = ConfigBenchmark.suggestions(measurements.values).mapNotNull { suggestion ->
            rows.firstOrNull { it.guid == suggestion.guid }
        }

        _state.value = _state.value.copy(
            operator = operator,
            rows = rows,
            suggestions = suggested,
        )
    }
}
