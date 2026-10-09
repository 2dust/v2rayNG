package com.v2ray.ang.ui.xfinder

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class XfinderSource(val url: String, val label: String) {
    TOP20(XfinderSources.TOP20_URL, "top20"),
    ALL(XfinderSources.ALL_URL, "all"),
}

data class XfinderUiState(
    val source: XfinderSource = XfinderSource.TOP20,
    val items: List<XfinderConfig> = emptyList(),
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    /** Share link ([XfinderConfig.raw]) of the row the user last sent to the core. */
    val selectedRaw: String? = null,
)

class XfinderViewModel(application: Application) : BaseViewModel(application) {
    private val repository = XfinderRepository()

    private val _uiState = MutableStateFlow(XfinderUiState())
    val uiState: StateFlow<XfinderUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(XfinderSource.TOP20)
    }

    fun selectSource(source: XfinderSource) {
        if (source == _uiState.value.source && !_uiState.value.hasError) return
        load(source)
    }

    fun refresh() = load(_uiState.value.source)

    private fun load(source: XfinderSource) {
        // A newer request supersedes the running one, so a slow "all" never overwrites "top 20".
        loadJob?.cancel()
        _uiState.update { it.copy(source = source, isLoading = true, hasError = false) }
        loadJob = viewModelScope.launch {
            try {
                XfinderLogStore.info("fetch ${source.label}: ${source.url}")
                val text = repository.fetchText(source.url)
                val parsed = withContext(Dispatchers.Default) { XfinderParser.parse(text) }
                if (text == null || parsed.isEmpty()) {
                    XfinderLogStore.error("fetch ${source.label}: failed or empty")
                    _uiState.update { it.copy(items = emptyList(), isLoading = false, hasError = true) }
                } else {
                    XfinderLogStore.info("fetch ${source.label}: ${parsed.size} configs")
                    _uiState.update { it.copy(items = parsed, isLoading = false, hasError = false) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                XfinderLogStore.error("fetch ${source.label}: ${e.javaClass.simpleName}")
                _uiState.update { it.copy(items = emptyList(), isLoading = false, hasError = true) }
            }
        }
    }

    /** Imports the row's config, selects it and hands it to the core via the main screen's restart flag. */
    fun onConfigClick(config: XfinderConfig) {
        viewModelScope.launch {
            val guid = repository.importAndSelect(config.raw)
            if (guid == null) {
                XfinderLogStore.error("select ${config.protocol} ${config.server}:${config.port}: import failed")
                toastError(R.string.xf_config_import_failed)
            } else {
                XfinderLogStore.info("select ${config.protocol} ${config.server}:${config.port} -> core")
                _uiState.update { it.copy(selectedRaw = config.raw) }
                toastSuccess(R.string.xf_config_selected)
            }
        }
    }

    /** Fetches the Top 20 list and adds all of its configs as connection links in one go. */
    fun addAllTop20() {
        viewModelScope.launch {
            val text = repository.fetchText(XfinderSource.TOP20.url)
            val parsed = withContext(Dispatchers.Default) { XfinderParser.parse(text) }
            val added = if (parsed.isEmpty()) 0 else repository.importAll(parsed.map { it.raw })
            if (added <= 0) {
                XfinderLogStore.error("add all top20: nothing imported")
                toastError(R.string.xf_config_import_failed)
            } else {
                XfinderLogStore.info("add all top20: $added configs")
                toastSuccess(R.string.xf_all_added)
            }
        }
    }
}
