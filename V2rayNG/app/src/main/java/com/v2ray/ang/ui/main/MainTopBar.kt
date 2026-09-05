package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppSearchState
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.verticalScrollbar

@Composable
fun MainTopBar(
    isLoading: Boolean,
    isSearchActive: Boolean,
    query: String,
    onMenuClick: () -> Unit,
    onAction: (MainAction) -> Unit,
    onShowDialog: (MainDialog) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showImportMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val importScrollState = rememberScrollState()
    val moreScrollState = rememberScrollState()
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val maxMenuHeight =
        LocalConfiguration.current.screenHeightDp.dp - statusBarHeight - navBarHeight - 20.dp

    val closeSearch: () -> Unit = { onAction(MainAction.SetSearchActive(false)) }

    AppTopBar(
        modifier = modifier,
        title = stringResource(R.string.title_server),
        onBackClick = onMenuClick,
        isLoading = isLoading,
        searchState = if (isSearchActive) AppSearchState(
            isActive = true,
            query = query,
            placeholder = stringResource(R.string.menu_item_search)
        ) else null,
        onSearchQueryChange = { newQuery -> onAction(MainAction.Search(newQuery)) },
        onSearchClose = closeSearch,
        navigationIcon = {
            if (isSearchActive) {
                IconButton(onClick = closeSearch) {
                    Icon(
                        painterResource(R.drawable.ic_arrow_back_24dp),
                        contentDescription = stringResource(R.string.acc_back)
                    )
                }
            } else {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        painterResource(R.drawable.ic_menu_24dp),
                        contentDescription = stringResource(R.string.acc_open_menu)
                    )
                }
            }
        },
        actions = {
            if (!isSearchActive) {
                IconButton(onClick = { onAction(MainAction.SetSearchActive(true)) }) {
                    Icon(
                        painterResource(R.drawable.ic_search_24dp),
                        contentDescription = stringResource(R.string.acc_search),
                    )
                }
            }
            Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                IconButton(onClick = { showImportMenu = true }) {
                    Icon(
                        painterResource(R.drawable.ic_add_24dp),
                        contentDescription = stringResource(R.string.acc_add),
                    )
                }
                DropdownMenu(
                    expanded = showImportMenu,
                    onDismissRequest = { showImportMenu = false },
                    scrollState = importScrollState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .heightIn(max = maxMenuHeight)
                        .verticalScrollbar(importScrollState),
                ) {
                    ImportMenuContent { action ->
                        showImportMenu = false
                        onAction(action)
                    }
                }
            }
            Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                IconButton(onClick = { showMoreMenu = true }) {
                    Icon(
                        painterResource(R.drawable.ic_more_vert_24dp),
                        contentDescription = stringResource(R.string.acc_more),
                    )
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false },
                    scrollState = moreScrollState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .heightIn(max = maxMenuHeight)
                        .verticalScrollbar(moreScrollState),
                ) {
                    MoreMenuContent(
                        onAction = { action ->
                            showMoreMenu = false
                            onAction(action)
                        },
                        onShowDialog = { dialog ->
                            showMoreMenu = false
                            onShowDialog(dialog)
                        },
                    )
                }
            }
        }
    )
}
