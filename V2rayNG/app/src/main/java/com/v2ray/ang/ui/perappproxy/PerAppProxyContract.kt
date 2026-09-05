package com.v2ray.ang.ui.perappproxy

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseAction
import com.v2ray.ang.ui.base.BaseUiState

/** One row of the per-app proxy list. */
@Immutable
data class ProxyAppRow(
    val packageName: String,
    val appName: String
)

/**
 * The rendered state of the screen, and nothing else.
 */
@Immutable
data class PerAppProxyUiState(
    val apps: List<ProxyAppRow> = emptyList(),
    val selected: Set<String> = emptySet(),
    val perAppProxyEnabled: Boolean = false,
    val bypassMode: Boolean = false,
    val query: String = "",
    val searchActive: Boolean = false
) : BaseUiState {

    val isEmptyResult: Boolean get() = apps.isEmpty() && query.isNotBlank()
}

/**
 * Overflow menu entries.
 */
internal enum class PerAppProxyMenu(
    @StringRes val labelRes: Int,
    val action: PerAppProxyAction
) {
    SelectAll(R.string.menu_item_select_all, PerAppProxyAction.SelectAll),
    InvertSelection(R.string.menu_item_invert_selection, PerAppProxyAction.InvertSelection),
    SelectProxyApps(R.string.menu_item_select_proxy_app, PerAppProxyAction.SelectRecommended),
    ImportSelection(R.string.menu_item_import_proxy_app, PerAppProxyAction.ImportSelection),
    ExportSelection(R.string.menu_item_export_proxy_app, PerAppProxyAction.ExportSelection)
}

/** The complete set of user intents of the per-app proxy screen. */
sealed interface PerAppProxyAction : BaseAction {

    /** Back / up: flushes pending writes, then closes and reports whatever was changed. */
    data object Back : PerAppProxyAction

    /** Tick or untick a single row. */
    data class ToggleApp(val packageName: String) : PerAppProxyAction

    /** Header switch: enable or disable per-app proxying as a whole. */
    data class PerAppProxyChanged(val enabled: Boolean) : PerAppProxyAction

    /** Header switch: swap between proxying and bypassing the checked apps. */
    data class BypassModeChanged(val enabled: Boolean) : PerAppProxyAction

    /** Open the search field in the top bar. */
    data object SearchOpen : PerAppProxyAction

    /** Close search and clear the filter. */
    data object SearchClose : PerAppProxyAction

    /** Every keystroke in the search field; debounced by the ViewModel. */
    data class QueryChanged(val value: String) : PerAppProxyAction

    /** Overflow: check every visible app, or clear them when all are already checked. */
    data object SelectAll : PerAppProxyAction

    /** Overflow: flip every visible app. */
    data object InvertSelection : PerAppProxyAction

    /** Overflow: replace the selection with the recommended package list. */
    data object SelectRecommended : PerAppProxyAction

    /** Overflow: replace the selection with a list pasted from the clipboard. */
    data object ImportSelection : PerAppProxyAction

    /** Overflow: copy the current selection to the clipboard. */
    data object ExportSelection : PerAppProxyAction
}
