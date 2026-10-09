package com.v2ray.ang.ui.xfinder

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScannerUiState(
    val data: ScannerData? = null,
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
)

class ScannerViewModel(application: Application) : BaseViewModel(application) {
    private val repository = XfinderRepository()

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, hasError = false) }
        loadJob = viewModelScope.launch {
            try {
                XfinderLogStore.info("fetch scanner: ${XfinderSources.SCANNER_URL}")
                val json = repository.fetchText(XfinderSources.SCANNER_URL)
                val data = withContext(Dispatchers.Default) { ScannerParser.parse(json) }
                if (data == null) {
                    XfinderLogStore.error("fetch scanner: failed or malformed")
                    _uiState.update { it.copy(isLoading = false, hasError = true) }
                } else {
                    XfinderLogStore.info("fetch scanner: ${data.ips.size} ips")
                    _uiState.update { it.copy(data = data, isLoading = false, hasError = false) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                XfinderLogStore.error("fetch scanner: ${e.javaClass.simpleName}")
                _uiState.update { it.copy(isLoading = false, hasError = true) }
            }
        }
    }

    fun copyIp(ip: String) {
        Utils.setClipboard(app, ip)
        toast(R.string.xf_ip_copied)
    }
}
