package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTopBarLayout
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
import com.v2ray.ang.ui.compose.requestFocusWhenReady

internal class TvMainTopBarFocus {
    val start = FocusRequester()
    val test = FocusRequester()
    val search = FocusRequester()
    val add = FocusRequester()
    val restart = FocusRequester()
    val more = FocusRequester()
}

@Composable
internal fun rememberTvMainTopBarFocus(showSearch: Boolean): TvMainTopBarFocus {
    val focus = remember { TvMainTopBarFocus() }
    if (!showSearch) {
        LaunchedEffect(focus) { requestFocusWhenReady(focus.start) }
    }
    return focus
}

@Composable
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
    onBulkDelete: (BulkDeleteTarget) -> Unit
) {
    val maxMenuHeight = mainMenuMaxHeight()
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
        MainTopBarMenu(
            maxHeight = maxMenuHeight,
            popupModifier = { close ->
                fun closeAndMove(direction: DpadHorizontalDirection) {
                    close()
                    adjacentDpadFocusTarget(focusRequester, focusOrder, direction)?.requestFocus()
                        ?: focusRequester.requestFocus()
                }
                Modifier.dpadPopupHorizontalNavigation(
                    onMovePrevious = { closeAndMove(DpadHorizontalDirection.Previous) },
                    onMoveNext = { closeAndMove(DpadHorizontalDirection.Next) }
                )
            },
            button = { open ->
                TvIconButton(
                    icon = icon,
                    label = label,
                    onClick = open,
                    focusRequester = focusRequester,
                    modifier = moveDownModifier.dpadOrderedFocusNavigation(focusRequester, focusOrder)
                )
            },
            content = content
        )
    }

    val searchInputFocus = remember { FocusRequester() }
    val searchClearFocus = remember { FocusRequester() }
    // While searching, Back takes Start's place and the normal toolbar actions are hidden.
    val searchFocusOrder = if (searchQuery.isEmpty()) listOf(focusRequesters.start, searchInputFocus)
    else listOf(focusRequesters.start, searchInputFocus, searchClearFocus)
    AppTopBarLayout(
        isLoading = isLoading,
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
                ) { close -> ImportMenuContent(itemModifier = { Modifier.padding(horizontal = 8.dp, vertical = 2.dp).dpadFocusOutline(cornerRadius = 10.dp) }) { action -> close(); onAction(action) } }
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
                    MoreMenuContent(
                        items = tvMoreMenuActions,
                        itemModifier = { Modifier.padding(horizontal = 8.dp, vertical = 2.dp).dpadFocusOutline(cornerRadius = 10.dp) },
                        onAction = { action -> close(); onAction(action) },
                        onBulkDelete = { target -> close(); onBulkDelete(target) }
                    )
                }
            }
        }
    )
}
