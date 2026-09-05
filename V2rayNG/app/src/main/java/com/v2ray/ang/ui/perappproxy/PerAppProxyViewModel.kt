package com.v2ray.ang.ui.perappproxy

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import com.v2ray.ang.R
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.repository.PerAppProxyRepository
import com.v2ray.ang.ui.base.BaseResult
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val SEARCH_DEBOUNCE_MS = 300L
private const val KEY_SAVED = "per_app_proxy_saved_state"
private const val KEY_QUERY = "query"

class PerAppProxyViewModel(
    private val repo: PerAppProxyRepository,
    private val handle: SavedStateHandle
) : BaseViewModel<PerAppProxyUiState, PerAppProxyAction>(initialState(repo, handle)) {

    private var allApps: List<AppInfo> = emptyList()
    private var visiblePackages: List<String> = emptyList()
    private var queryJob: Job? = null
    private var persistJob: Job? = null
    private var changed = false

    init {
        handle.setSavedStateProvider(KEY_SAVED) {
            Bundle().apply { putString(KEY_QUERY, state.query) }
        }
        load()
    }

    override fun onAction(action: PerAppProxyAction) {
        when (action) {
            PerAppProxyAction.Back -> finish()
            is PerAppProxyAction.ToggleApp -> publishSelection(toggled(action.packageName))
            is PerAppProxyAction.PerAppProxyChanged -> applyPerAppProxyEnabled(action.enabled)
            is PerAppProxyAction.BypassModeChanged -> applyBypassMode(action.enabled)
            PerAppProxyAction.SearchOpen -> setState { copy(searchActive = true) }
            PerAppProxyAction.SearchClose -> applyQuery("", searchActive = false, debounce = false)
            is PerAppProxyAction.QueryChanged ->
                applyQuery(action.value, searchActive = true, debounce = true)
            PerAppProxyAction.SelectAll -> mutateVisible(repo::toggleAll)
            PerAppProxyAction.InvertSelection -> mutateVisible(repo::invert)
            PerAppProxyAction.SelectRecommended -> selectRecommended()
            PerAppProxyAction.ImportSelection -> importSelection()
            PerAppProxyAction.ExportSelection -> exportSelection()
        }
    }

    private fun load() = launch(loading = true) {
        val apps = repo.loadApps(selectedSnapshot = state.selected)
        allApps = apps
        queryJob?.cancel()
        publishRows(filtered(apps, state.query))
    }

    private fun applyQuery(query: String, searchActive: Boolean, debounce: Boolean) {
        setState { copy(query = query, searchActive = searchActive) }
        queryJob?.cancel()
        queryJob = launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            publishRows(filtered(allApps, query))
        }
    }

    private suspend fun filtered(apps: List<AppInfo>, query: String): List<AppInfo> =
        withContext(Dispatchers.Default) { repo.filter(apps, query) }

    private fun toggled(packageName: String): Set<String> = with(state.selected) {
        if (packageName in this) this - packageName else this + packageName
    }

    private fun publishSelection(next: Set<String>) {
        if (next == state.selected) return
        setState { copy(selected = next) }
        markChanged()
        persist { repo.saveSelection(next) }
    }

    private fun mutateVisible(operation: (Set<String>, Collection<String>) -> Set<String>) =
        launch {
            val visible = visiblePackages
            if (visible.isEmpty()) return@launch
            val current = state.selected
            val next = withContext(Dispatchers.Default) { operation(current, visible) }
            publishSelection(next)
            applyPerAppProxyEnabled(true)
        }

    private fun applyPerAppProxyEnabled(enabled: Boolean) {
        if (state.perAppProxyEnabled == enabled) return
        setState { copy(perAppProxyEnabled = enabled) }
        markChanged()
        persist { repo.setPerAppProxyEnabled(enabled) }
    }

    private fun applyBypassMode(enabled: Boolean) {
        if (state.bypassMode == enabled) return
        setState { copy(bypassMode = enabled) }
        markChanged()
        persist { repo.setBypassMode(enabled) }
    }

    private fun selectRecommended() = launch(loading = true) {
        val content = repo.fetchRecommendedList()
        if (content.isBlank()) {
            toastError()
            return@launch
        }
        applyProxyList(content, forceGoogleApps = true)
    }

    private fun importSelection() = launch(loading = true) {
        val content = repo.readClipboard()
        if (content.isBlank()) {
            toastError(R.string.toast_none_data_clipboard)
            return@launch
        }
        applyProxyList(content, forceGoogleApps = false)
    }

    private suspend fun applyProxyList(content: String, forceGoogleApps: Boolean) {
        val apps = allApps
        if (apps.isEmpty()) {
            toastError(R.string.toast_none_data)
            return
        }
        val next = repo.resolveProxyList(
            packageNames = apps.map { it.packageName },
            proxyAppList = content,
            bypassApps = state.bypassMode,
            forceGoogleApps = forceGoogleApps
        )
        publishSelection(next)
        applyPerAppProxyEnabled(true)
        toastSuccess()
    }

    private fun exportSelection() = launch {
        val selection = state.selected
        if (selection.isEmpty()) {
            toastError(R.string.toast_none_data)
            return@launch
        }
        if (repo.exportSelection(state.bypassMode, selection)) toastSuccess() else toastError()
    }

    private fun finish() = launch(onError = { report() }) {
        persistJob?.join()
        report()
    }

    private fun report() = finishWith(
        if (changed) BaseResult.Changed(restartService = true) else BaseResult.Cancelled
    )

    private fun publishRows(apps: List<AppInfo>) {
        val rows = apps.map { ProxyAppRow(packageName = it.packageName, appName = it.appName) }
        visiblePackages = rows.map { it.packageName }
        setState { copy(apps = rows) }
    }

    private fun markChanged() {
        if (changed) return
        changed = true
        persist { repo.notifyRestartService() }
    }

    // 保持写入顺序，确保退出前落盘
    private fun persist(block: suspend () -> Unit) {
        val previous = persistJob
        persistJob = launch(onError = {}) {
            previous?.join()
            withContext(NonCancellable) { block() }
        }
    }

    override fun onCleared() {
        queryJob?.cancel()
        queryJob = null
        persistJob = null
        allApps = emptyList()
        visiblePackages = emptyList()
        super.onCleared()
    }
}

private fun initialState(
    repo: PerAppProxyRepository,
    handle: SavedStateHandle
): PerAppProxyUiState {
    val prefs = repo.loadPreferences()
    val query = handle.get<Bundle>(KEY_SAVED)?.getString(KEY_QUERY).orEmpty()
    return PerAppProxyUiState(
        selected = prefs.selected,
        perAppProxyEnabled = prefs.perAppProxyEnabled,
        bypassMode = prefs.bypassMode,
        query = query,
        searchActive = query.isNotEmpty()
    )
}
