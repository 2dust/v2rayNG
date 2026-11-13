package com.v2ray.ang.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.DpadHorizontalDirection
import com.v2ray.ang.ui.compose.TvIconButton
import com.v2ray.ang.ui.compose.TvTopBarSearchField
import com.v2ray.ang.ui.compose.adjacentDpadFocusTarget
import com.v2ray.ang.ui.compose.colorFabActive
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.dpadPopupHorizontalNavigation
import com.v2ray.ang.ui.compose.dpadTopBarFocusNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.verticalScrollbar

internal val tvMoreMenuActions = MainMoreMenuAction.entries - setOf(MainMoreMenuAction.RestartService, MainMoreMenuAction.DeleteAll)

internal class TvMainTopBarFocus {
    val start = FocusRequester()
    val test = FocusRequester()
    val search = FocusRequester()
    val add = FocusRequester()
    val restart = FocusRequester()
    val more = FocusRequester()
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun TvMainTopBar(
    isLoading: Boolean,
    isRunning: Boolean,
    showSearch: Boolean,
    searchQuery: String,
    focusRequesters: TvMainTopBarFocus,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onSearchOpen: () -> Unit,
    onOpenDrawer: () -> Unit,
    onMoveDown: () -> Boolean,
    onAction: (MainAction) -> Unit,
    onBulkDelete: (TvBulkDeleteTarget) -> Unit
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val maxMenuHeight = windowHeight - statusBarHeight - navBarHeight - 20.dp
    val moveDownModifier = Modifier.dpadVerticalFocusNavigation(onMoveUp = { false }, onMoveDown = onMoveDown)
    val focusOrder = remember(isRunning, focusRequesters) {
        buildList {
            add(focusRequesters.start)
            if (isRunning) add(focusRequesters.test)
            add(focusRequesters.search)
            add(focusRequesters.add)
            add(focusRequesters.restart)
            add(focusRequesters.more)
        }
    }

    @Composable
    fun MenuButton(
        icon: Painter,
        label: String,
        focusRequester: FocusRequester,
        content: @Composable ColumnScope.(close: () -> Unit) -> Unit
    ) {
        var expanded by remember { mutableStateOf(false) }
        val scrollState = rememberScrollState()
        val close = { expanded = false }
        fun closeAndMove(direction: DpadHorizontalDirection) {
            close()
            adjacentDpadFocusTarget(focusRequester, focusOrder, direction)?.requestFocus()
                ?: focusRequester.requestFocus()
        }
        Box(Modifier.wrapContentSize(Alignment.TopEnd)) {
            TvIconButton(
                icon, label, { expanded = true },
                focusRequester = focusRequester,
                modifier = moveDownModifier.dpadOrderedFocusNavigation(focusRequester, focusOrder)
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = close,
                scrollState = scrollState,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.heightIn(max = maxMenuHeight)
                    .dpadPopupHorizontalNavigation(
                        onMovePrevious = { closeAndMove(DpadHorizontalDirection.Previous) },
                        onMoveNext = { closeAndMove(DpadHorizontalDirection.Next) }
                    )
                    .verticalScrollbar(scrollState)
            ) { content(close) }
        }
    }

    val searchInputFocus = remember { FocusRequester() }
    val searchClearFocus = remember { FocusRequester() }
    // While searching, Back takes Start's place and the normal toolbar actions are hidden.
    val searchFocusOrder = if (searchQuery.isEmpty()) listOf(focusRequesters.start, searchInputFocus)
    else listOf(focusRequesters.start, searchInputFocus, searchClearFocus)
    Column {
        TopAppBar(
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 12.dp),
            title = {
                if (showSearch) {
                    TvTopBarSearchField(searchQuery, onSearchQueryChange, searchInputFocus, searchClearFocus, searchFocusOrder)
                } else {
                    Box(modifier = Modifier.height(48.dp), contentAlignment = Alignment.CenterStart) {
                        AppBrandTitle(
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 40.sp, lineHeight = 48.sp)
                        )
                    }
                }
            },
            navigationIcon = {
                if (showSearch) {
                    TvIconButton(
                        icon = painterResource(R.drawable.ic_tv_arrow_back_24dp),
                        label = stringResource(R.string.action_back),
                        contentDescription = stringResource(R.string.acc_back),
                        onClick = onSearchClose,
                        focusRequester = focusRequesters.start,
                        modifier = Modifier.dpadTopBarFocusNavigation(focusRequesters.start, searchFocusOrder) { false }
                    )
                }
            },
            actions = {
                if (!showSearch) {
                    TvIconButton(
                        icon = if (isRunning) {
                            painterResource(R.drawable.ic_stop_24dp)
                        } else {
                            painterResource(R.drawable.ic_play_24dp)
                        },
                        label = if (isRunning) {
                            stringResource(R.string.action_stop_service)
                        } else {
                            stringResource(R.string.tasker_start_service)
                        },
                        onClick = { onAction(MainAction.ToggleService) },
                        focusRequester = focusRequesters.start,
                        modifier = moveDownModifier.dpadOrderedFocusNavigation(
                            current = focusRequesters.start,
                            order = focusOrder,
                            onBeforeFirst = onOpenDrawer
                        ),
                        containerColor = if (isRunning) colorFabActive else Color.Transparent,
                        contentColor = if (isRunning) Color.White else null
                    )
                    if (isRunning) {
                        TvIconButton(
                            icon = painterResource(R.drawable.ic_stopwatch_24dp),
                            label = stringResource(R.string.connection_test_pending),
                            onClick = { onAction(MainAction.TestCurrentServer) },
                            focusRequester = focusRequesters.test,
                            modifier = moveDownModifier.dpadOrderedFocusNavigation(focusRequesters.test, focusOrder)
                        )
                    }
                    TvIconButton(
                        icon = painterResource(R.drawable.ic_search_24dp),
                        label = stringResource(R.string.menu_item_search),
                        onClick = onSearchOpen,
                        focusRequester = focusRequesters.search,
                        modifier = moveDownModifier.dpadOrderedFocusNavigation(focusRequesters.search, focusOrder)
                    )
                    MenuButton(
                        icon = painterResource(R.drawable.ic_add_24dp),
                        label = stringResource(R.string.menu_item_add_config),
                        focusRequester = focusRequesters.add
                    ) { close ->
                        ImportMenuAction.entries.forEach { item ->
                            TvMenuItem(item.labelRes) { close(); onAction(item.action) }
                        }
                    }
                    TvIconButton(
                        icon = painterResource(R.drawable.ic_restore_24dp),
                        label = stringResource(R.string.title_service_restart),
                        onClick = { onAction(MainAction.RestartService) },
                        focusRequester = focusRequesters.restart,
                        modifier = moveDownModifier.dpadOrderedFocusNavigation(focusRequesters.restart, focusOrder)
                    )
                    MenuButton(
                        icon = painterResource(R.drawable.ic_more_vert_24dp),
                        label = stringResource(R.string.action_more),
                        focusRequester = focusRequesters.more
                    ) { close ->
                        tvMoreMenuActions.forEach { item ->
                            TvMenuItem(item.labelRes) { close(); item.dispatch(onAction, onBulkDelete) }
                        }
                        TvMenuItem(R.string.action_exit) { close(); onAction(MainAction.Exit) }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
        AnimatedVisibility(isLoading, enter = expandVertically(), exit = shrinkVertically()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun TvMenuItem(labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp).dpadFocusOutline(cornerRadius = 10.dp),
        onClick = onClick
    )
}

private fun MainMoreMenuAction.dispatch(onAction: (MainAction) -> Unit, onBulkDelete: (TvBulkDeleteTarget) -> Unit) {
    when (this) {
        MainMoreMenuAction.RestartService, MainMoreMenuAction.DeleteAll -> Unit // Not shown in the TV menu.
        MainMoreMenuAction.DeleteDuplicate -> onBulkDelete(TvBulkDeleteTarget.Duplicate)
        MainMoreMenuAction.DeleteInvalid -> onBulkDelete(TvBulkDeleteTarget.Invalid)
        MainMoreMenuAction.ExportAll -> onAction(MainAction.ExportAll)
        MainMoreMenuAction.LocateSelected -> onAction(MainAction.LocateSelectedServer)
        MainMoreMenuAction.SortByTestResults -> onAction(MainAction.SortByTestResults)
        MainMoreMenuAction.TestAll -> onAction(MainAction.TestAllServers)
        MainMoreMenuAction.TestAllRealPing -> onAction(MainAction.TestRealAllServers)
        MainMoreMenuAction.UpdateSubscriptions -> onAction(MainAction.UpdateSubscriptions)
    }
}
