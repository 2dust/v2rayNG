package com.v2ray.ang.ui.logcat

import com.v2ray.ang.R
import com.v2ray.ang.extension.delay
import com.v2ray.ang.repository.LogcatRepository
import com.v2ray.ang.ui.base.BaseResult
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

class LogcatViewModel(
    private val repo: LogcatRepository
) : BaseViewModel<LogcatUiState, LogcatAction>(LogcatUiState()) {

    private var snapshot: List<LogLine> = emptyList()

    /** Reading and clearing share one slot: both replace the snapshot and must not interleave. */
    private var bufferJob: Job? = null
    private var filterJob: Job? = null

    private val busy: Boolean get() = bufferJob?.isActive == true

    init {
        refresh()
    }

    override fun onAction(action: LogcatAction) {
        when (action) {
            LogcatAction.Back -> back()
            LogcatAction.Refresh -> refresh()
            LogcatAction.CopyAll -> copyAll()
            LogcatAction.Clear -> clear()
            LogcatAction.Share -> share()

            LogcatAction.SearchOpened -> setState { copy(searchActive = true) }
            LogcatAction.SearchClosed -> closeSearch()
            is LogcatAction.QueryChanged -> changeQuery(action.value)

            is LogcatAction.LineLongPressed -> copyLine(action.text)
            is LogcatAction.ShareFinished -> if (!action.ok) toastError()
        }
    }

    // ---------------- navigation & search ----------------

    /** Back leaves the search mode first */
    private fun back() {
        if (state.searchActive) closeSearch() else finishWith(BaseResult.Cancelled)
    }

    private fun closeSearch() {
        if (!state.searchActive && state.query.isEmpty()) return
        setState { copy(searchActive = false, query = "") }
        applyFilter(debounce = false)
    }

    private fun changeQuery(value: String) {
        // The text field is a plain slot: an identical value must not restart the filter.
        if (value == state.query) return
        setState { copy(query = value) }
        applyFilter(debounce = true)
    }

    // ---------------- loading ----------------

    /** A second refresh is refused rather than queued: two reads would race over [snapshot]. */
    private fun refresh() {
        if (busy) return
        bufferJob = launch(loading = true) {
            val raw = repo.read()
            snapshot = withContext(Dispatchers.Default) { parseLogLines(raw) }
            applyFilter(debounce = false)
        }
    }

    private fun clear() {
        if (busy) return toastInfo(R.string.msg_dialog_progress)
        bufferJob = launch(loading = true) {
            repo.clear()
            filterJob?.cancel()
            snapshot = emptyList()
            setState { copy(lines = emptyList()) }
            toastSuccess()
        }
    }

    // ---------------- filtering ----------------

    /**
     * Recomputes the visible rows.
     */
    private fun applyFilter(debounce: Boolean) {
        filterJob?.cancel()
        filterJob = launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            val query = state.query.trim()
            val source = snapshot
            val visible = if (query.isEmpty()) {
                source
            } else {
                withContext(Dispatchers.Default) {
                    source.filter { it.raw.contains(query, ignoreCase = true) }
                }
            }
            setState { copy(lines = visible) }
        }
    }

    // ---------------- actions ----------------

    private fun copyLine(text: String) = launch {
        repo.copyToClipboard(text)
        toastSuccess()
    }

    private fun copyAll() = launch {
        val lines = state.lines
        if (lines.isEmpty()) return@launch toastError(R.string.toast_none_data)
        val text = withContext(Dispatchers.Default) { lines.joinToString("\n") { it.raw } }
        repo.copyToClipboard(text)
        toastSuccess()
    }

    private fun share() = launch(loading = true) {
        val lines = state.lines
        if (lines.isEmpty()) return@launch toastError(R.string.toast_none_data)
        val raw = withContext(Dispatchers.Default) { lines.map { it.raw } }
        val path = repo.writeShareFile(raw) ?: return@launch toastError()
        platform(LogcatEvent.ShareFile(path))
    }

    /** Releases the snapshot eagerly instead of waiting for the whole ViewModel to be collected. */
    override fun onCleared() {
        bufferJob?.cancel()
        filterJob?.cancel()
        bufferJob = null
        filterJob = null
        snapshot = emptyList()
        super.onCleared()
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
