package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTopBarLayout
import com.v2ray.ang.ui.compose.TopBarSearchField
import com.v2ray.ang.ui.compose.verticalScrollbar

@Composable
internal fun MainTopBar(
    isLoading: Boolean,
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onSearchOpen: () -> Unit,
    onOpenDrawer: () -> Unit,
    onAction: (MainAction) -> Unit,
    onBulkDelete: (BulkDeleteTarget) -> Unit
) {
    val maxMenuHeight = mainMenuMaxHeight()
    AppTopBarLayout(
        isLoading = isLoading,
        title = {
            if (showSearch) TopBarSearchField(searchQuery, onSearchQueryChange, stringResource(R.string.menu_item_search))
            else Text(stringResource(R.string.title_server))
        },
        navigationIcon = {
            if (showSearch) {
                IconButton(onClick = onSearchClose) {
                    Icon(painterResource(R.drawable.ic_arrow_back_24dp), stringResource(R.string.acc_back))
                }
            } else {
                IconButton(onClick = onOpenDrawer) {
                    Icon(painterResource(R.drawable.ic_menu_24dp), stringResource(R.string.acc_open_menu))
                }
            }
        },
        actions = {
            if (!showSearch) {
                IconButton(onClick = onSearchOpen) {
                    Icon(painterResource(R.drawable.ic_search_24dp), stringResource(R.string.acc_search))
                }
            }
            MainTopBarMenu(maxMenuHeight, button = { open ->
                IconButton(onClick = open) {
                    Icon(painterResource(R.drawable.ic_add_24dp), stringResource(R.string.acc_add))
                }
            }) { close -> ImportMenuContent { action -> close(); onAction(action) } }
            MainTopBarMenu(maxMenuHeight, button = { open ->
                IconButton(onClick = open) {
                    Icon(painterResource(R.drawable.ic_more_vert_24dp), stringResource(R.string.acc_more))
                }
            }) { close ->
                MoreMenuContent(
                    items = phoneMoreMenuActions,
                    onAction = { action -> close(); onAction(action) },
                    onBulkDelete = { target -> close(); onBulkDelete(target) }
                )
            }
        }
    )
}

@Composable
internal fun mainMenuMaxHeight(): Dp {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    return windowHeight - statusBarHeight - navBarHeight - 20.dp
}

@Composable
internal fun MainTopBarMenu(
    maxHeight: Dp,
    popupModifier: @Composable (close: () -> Unit) -> Modifier = { Modifier },
    button: @Composable (open: () -> Unit) -> Unit,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val close = { expanded = false }
    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
        button { expanded = true }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = close,
            scrollState = scrollState,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.heightIn(max = maxHeight).then(popupModifier(close)).verticalScrollbar(scrollState)
        ) {
            content(close)
        }
    }
}
