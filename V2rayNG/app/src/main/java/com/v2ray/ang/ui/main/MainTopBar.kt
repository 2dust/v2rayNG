package com.v2ray.ang.ui.main

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.v2ray.ang.ui.compose.AppIconButton
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DpadHorizontalDirection
import com.v2ray.ang.ui.compose.adjacentDpadFocusTarget
import com.v2ray.ang.ui.compose.colorFabActive
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.dpadPopupHorizontalNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.isTelevisionDevice
import com.v2ray.ang.ui.compose.rememberDpadFocusRequester
import com.v2ray.ang.ui.compose.verticalScrollbar

private enum class MainTopBarMenu { Import, More }

internal class MainTopBarFocusRequesters(val start: FocusRequester) {
    val test = FocusRequester()
    val search = FocusRequester()
    val add = FocusRequester()
    val restart = FocusRequester()
    val more = FocusRequester()
}

@Composable
internal fun rememberMainTopBarFocusRequesters(showSearch: Boolean): MainTopBarFocusRequesters {
    val start = rememberDpadFocusRequester(requestFocus = !showSearch, requestKey = showSearch)
    return remember(start) { MainTopBarFocusRequesters(start) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainTopBar(
    isLoading: Boolean,
    isRunning: Boolean,
    showSearch: Boolean,
    searchQuery: String,
    focusRequesters: MainTopBarFocusRequesters,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onOpenDrawer: (FocusRequester?) -> Unit,
    onMoveDown: () -> Boolean,
    onAction: (MainAction) -> Unit,
    onBulkDelete: (BulkDeleteTarget) -> Unit
) {
    val isTelevision = isTelevisionDevice()
    var openMenu by remember { mutableStateOf<MainTopBarMenu?>(null) }
    val importMenuScrollState = rememberScrollState()
    val moreMenuScrollState = rememberScrollState()
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
        menu: MainTopBarMenu,
        icon: Painter,
        label: String,
        focusRequester: FocusRequester,
        scrollState: ScrollState,
        content: @Composable ColumnScope.() -> Unit
    ) {
        fun closeAndMove(direction: DpadHorizontalDirection) {
            openMenu = null
            adjacentDpadFocusTarget(focusRequester, focusOrder, direction)?.requestFocus()
                ?: focusRequester.requestFocus()
        }

        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
            AppIconButton(
                icon = icon,
                label = label,
                onClick = { openMenu = menu },
                focusRequester = focusRequester,
                modifier = moveDownModifier.dpadOrderedFocusNavigation(focusRequester, focusOrder)
            )
            DropdownMenu(
                expanded = openMenu == menu,
                onDismissRequest = { openMenu = null },
                scrollState = scrollState,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .heightIn(max = maxMenuHeight)
                    .dpadPopupHorizontalNavigation(
                        onMovePrevious = { closeAndMove(DpadHorizontalDirection.Previous) },
                        onMoveNext = { closeAndMove(DpadHorizontalDirection.Next) }
                    )
                    .verticalScrollbar(scrollState),
                content = content
            )
        }
    }

    AppTopBar(
        title = stringResource(R.string.title_server),
        onBackClick = {},
        initialFocus = !isTelevision,
        isLoading = isLoading,
        isSearchActive = showSearch,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onSearchClose = onSearchClose,
        searchPlaceholder = stringResource(R.string.menu_item_search),
        titleContent = if (isTelevision) {
            {
                Box(modifier = Modifier.height(48.dp), contentAlignment = Alignment.CenterStart) {
                    AppBrandTitle(
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 40.sp,
                            lineHeight = 48.sp
                        )
                    )
                }
            }
        } else null,
        // The leading focus target is Start/Stop normally and the standard Back button during search.
        navigationFocusRequester = focusRequesters.start,
        navigationIcon = if (showSearch) null else {
            {
                if (!isTelevision) IconButton(onClick = { onOpenDrawer(null) }) {
                    Icon(painterResource(R.drawable.ic_menu_24dp), stringResource(R.string.acc_open_menu))
                }
            }
        },
        actions = {
            if (isTelevision && !showSearch) {
                AppIconButton(
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
                        onBeforeFirst = { onOpenDrawer(focusRequesters.start) }
                    ),
                    containerColor = if (isRunning) colorFabActive else Color.Transparent,
                    contentColor = if (isRunning) Color.White else null
                )
                if (isRunning) {
                    AppIconButton(
                        icon = painterResource(R.drawable.ic_stopwatch_24dp),
                        label = stringResource(R.string.connection_test_pending),
                        onClick = { onAction(MainAction.TestCurrentServer) },
                        focusRequester = focusRequesters.test,
                        modifier = moveDownModifier.dpadOrderedFocusNavigation(focusRequesters.test, focusOrder)
                    )
                }
                AppIconButton(
                    icon = painterResource(R.drawable.ic_search_24dp),
                    label = stringResource(R.string.menu_item_search),
                    onClick = { onSearchToggle(true) },
                    focusRequester = focusRequesters.search,
                    modifier = moveDownModifier.dpadOrderedFocusNavigation(focusRequesters.search, focusOrder)
                )
            } else if (!showSearch) {
                AppIconButton(
                    icon = painterResource(R.drawable.ic_search_24dp),
                    label = stringResource(R.string.menu_item_search),
                    onClick = { onSearchToggle(true) },
                    modifier = moveDownModifier
                )
            }

            if (!isTelevision || !showSearch) {
                MenuButton(
                    menu = MainTopBarMenu.Import,
                    icon = painterResource(R.drawable.ic_add_24dp),
                    label = stringResource(R.string.menu_item_add_config),
                    focusRequester = focusRequesters.add,
                    scrollState = importMenuScrollState
                ) { ImportMenuContent { action -> openMenu = null; onAction(action) } }
                if (isTelevision) {
                    AppIconButton(
                        icon = painterResource(R.drawable.ic_restore_24dp),
                        label = stringResource(R.string.title_service_restart),
                        onClick = { onAction(MainAction.RestartService) },
                        focusRequester = focusRequesters.restart,
                        modifier = moveDownModifier.dpadOrderedFocusNavigation(focusRequesters.restart, focusOrder)
                    )
                }
                MenuButton(
                    menu = MainTopBarMenu.More,
                    icon = painterResource(R.drawable.ic_more_vert_24dp),
                    label = stringResource(R.string.action_more),
                    focusRequester = focusRequesters.more,
                    scrollState = moreMenuScrollState
                ) {
                    MoreMenuContent(
                        isTelevision = isTelevision,
                        onAction = { action -> openMenu = null; onAction(action) },
                        onBulkDelete = { target -> openMenu = null; onBulkDelete(target) }
                    )
                }
            }
        }
    )
}
