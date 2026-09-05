package com.v2ray.ang.ui.apppicker

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.repository.AppListRepository
import com.v2ray.ang.ui.AppRoute
import com.v2ray.ang.ui.base.BaseResult
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class AppPickerViewModel(
    private val repo: AppListRepository,
    private val handle: SavedStateHandle
) : BaseViewModel<AppPickerUiState, AppPickerAction>(initialState(handle)) {

    /** Unfiltered list; the state only ever exposes the filtered view of it. */
    private var allApps: List<AppInfo> = emptyList()

    /**
     * Taken from the Intent before [restoreSavedState] runs, hence the entry state to diff the
     * result against. Declared above `init` on purpose: property initializers run first.
     */
    private val initialSelected: Set<String> = state.selected

    /** Held so a new keystroke cancels the pending filter instead of queueing another one. */
    private var queryJob: Job? = null

    init {
        restoreSavedState()
        handle.setSavedStateProvider(KEY_SAVED) {
            Bundle().apply {
                putStringArrayList(KEY_SELECTED, ArrayList(state.selected))
                putString(KEY_QUERY, state.query)
            }
        }
        load()
    }

    override fun onAction(action: AppPickerAction) {
        when (action) {
            AppPickerAction.Back -> back()

            is AppPickerAction.ToggleApp -> toggle(action.packageName)

            AppPickerAction.SearchOpen -> setState { copy(searchActive = true) }
            AppPickerAction.SearchClose -> closeSearch()
            is AppPickerAction.QueryChanged ->
                applyQuery(action.value, searchActive = true, debounce = true)

            AppPickerAction.SelectAll -> mutateSelection(repo::selectAll)
            AppPickerAction.InvertSelection -> mutateSelection(repo::invert)
        }
    }

    // ===== loading =====

    /**
     * Loads the list ordered against the selection the screen started with.
     */
    private fun load() = launch(loading = true, context = Dispatchers.Default) {
        val snapshot = state.selected
        allApps = repo.loadApps(selectedSnapshot = snapshot)
        republish()
    }

    private fun restoreSavedState() {
        val saved = handle.get<Bundle>(KEY_SAVED) ?: return
        val selected = saved.getStringArrayList(KEY_SELECTED)?.toSet() ?: return
        val query = saved.getString(KEY_QUERY).orEmpty()
        setState { copy(selected = selected, query = query, searchActive = query.isNotEmpty()) }
    }

    // ===== search =====

    private fun applyQuery(query: String, searchActive: Boolean, debounce: Boolean) {
        setState { copy(query = query, searchActive = searchActive) }
        queryJob?.cancel()
        queryJob = launch(context = Dispatchers.Default) {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            publishRows(repo.filter(allApps, query))
        }
    }

    private fun closeSearch() = applyQuery("", searchActive = false, debounce = false)

    /**
     * Cancels a filter that may still be holding the pre-load (empty) list, so the load is always
     * the last publisher and the screen cannot be left showing an empty result.
     */
    private fun republish() {
        queryJob?.cancel()
        publishRows(repo.filter(allApps, state.query))
    }

    // ===== selection =====

    private fun toggle(packageName: String) {
        val current = state.selected
        val next = if (packageName in current) current - packageName else current + packageName
        setState { copy(selected = next) }
    }

    /** Bulk actions act on the visible rows only, so a filter narrows their scope as expected. */
    private fun mutateSelection(operation: (Set<String>, Collection<String>) -> Set<String>) {
        val visible = state.apps.map { it.packageName }
        if (visible.isEmpty()) return
        val next = operation(state.selected, visible)
        setState { copy(selected = next) }
    }

    // ===== exit =====

    private fun back() {
        if (state.searchActive) {
            closeSearch()
            return
        }
        finishWith(
            if (state.selected == initialSelected) BaseResult.Cancelled
            else BaseResult.Selected(state.selected.sorted())
        )
    }

    // ===== reduction helpers =====

    /** Mapping happens once per list change, never per frame and never inside the reducer. */
    private fun publishRows(apps: List<AppInfo>) {
        val rows = apps.map { it.toRow() }
        setState { copy(apps = rows) }
    }

    private fun AppInfo.toRow() = AppRow(
        packageName = packageName,
        appName = appName,
        isUnidentified = packageName == AppConfig.UNIDENTIFIED_PACKAGE
    )

    override fun onCleared() {
        queryJob = null
        allApps = emptyList()
        super.onCleared()
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val KEY_SAVED = "app_picker_saved_state"
        const val KEY_SELECTED = "selected"
        const val KEY_QUERY = "query"
    }
}

/**
 * Seeds the state from the Intent arguments.
 */
private fun initialState(handle: SavedStateHandle) = AppPickerUiState(
    titleRes = handle.get<Int>(AppRoute.EXTRA_PICKER_TITLE_RES)?.takeIf { it != 0 }
        ?: AppPickerUiState.DEFAULT_TITLE_RES,
    selected = handle.get<ArrayList<String>>(AppRoute.EXTRA_PICKER_SELECTED)?.toSet().orEmpty()
)
