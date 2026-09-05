package com.v2ray.ang.ui.apppicker

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseAction
import com.v2ray.ang.ui.base.BaseUiState

/**
 * One row of the picker.
 */
@Immutable
data class AppRow(
    val packageName: String,
    val appName: String,
    val isUnidentified: Boolean = false
)

@Immutable
data class AppPickerUiState(
    @StringRes val titleRes: Int = DEFAULT_TITLE_RES,
    val apps: List<AppRow> = emptyList(),
    val selected: Set<String> = emptySet(),
    val query: String = "",
    val searchActive: Boolean = false
) : BaseUiState {

    val isEmptyResult: Boolean get() = apps.isEmpty() && query.isNotBlank()

    companion object {

        /** Used when the caller passed no [com.v2ray.ang.ui.AppRoute.EXTRA_PICKER_TITLE_RES]. */
        @StringRes
        val DEFAULT_TITLE_RES: Int = R.string.per_app_proxy_settings
    }
}

/**
 * Overflow menu of the picker. Each entry carries the Action it dispatches, so the Screen renders
 * the menu without a second `when` mapping that would have to be kept in sync.
 */
internal enum class AppPickerMenu(
    @StringRes val labelRes: Int,
    val action: AppPickerAction
) {
    SelectAll(R.string.menu_item_select_all, AppPickerAction.SelectAll),
    InvertSelection(R.string.menu_item_invert_selection, AppPickerAction.InvertSelection)
}

/** The complete set of user intents of the app picker. */
sealed interface AppPickerAction : BaseAction {

    /**
     * Back / up. Closes the search field when it is open, otherwise confirms the current selection
     * and hands it to the caller.
     */
    data object Back : AppPickerAction

    /** Tick or untick a single row. */
    data class ToggleApp(val packageName: String) : AppPickerAction

    /** Open the search field in the top bar. */
    data object SearchOpen : AppPickerAction

    /** Close search and clear the filter. */
    data object SearchClose : AppPickerAction

    /** Every keystroke in the search field; debounced by the ViewModel. */
    data class QueryChanged(val value: String) : AppPickerAction

    /** Overflow menu: check every currently visible app. */
    data object SelectAll : AppPickerAction

    /** Overflow menu: flip every currently visible app. */
    data object InvertSelection : AppPickerAction
}
