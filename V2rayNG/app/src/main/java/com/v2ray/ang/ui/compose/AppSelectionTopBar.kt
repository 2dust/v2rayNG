package com.v2ray.ang.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R

internal data class AppSelectionMenuAction(val label: String, val onClick: () -> Unit)

@Composable
internal fun AppSelectionTopBar(
    title: String,
    isLoading: Boolean,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onSearchOpen: () -> Unit,
    onBackClick: () -> Unit,
    backFocusRequester: FocusRequester,
    onMoveDown: () -> Boolean,
    menuActions: List<AppSelectionMenuAction>
) {
    BackHandler(enabled = isTelevisionDevice() && isSearchActive, onBack = onSearchClose)
    AppTopBar(
        title = title,
        onBackClick = onBackClick,
        isLoading = isLoading,
        isSearchActive = isSearchActive,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onSearchClose = onSearchClose,
        searchPlaceholder = stringResource(R.string.menu_item_search),
        navigationFocusRequester = backFocusRequester,
        onMoveDown = onMoveDown,
        actionItems = buildList {
            if (!isSearchActive) add(AppTopBarAction(
                painterResource(R.drawable.ic_search_24dp), stringResource(R.string.menu_item_search), onSearchOpen
            ))
            add(AppTopBarAction(
                painterResource(R.drawable.ic_more_vert_24dp), stringResource(R.string.action_more), menuActions = menuActions
            ))
        }
    )
}
