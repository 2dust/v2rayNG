package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.ui.base.BaseScreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val ListBottomPadding = 80.dp
private const val LocateViewportDivisor = 3
private const val LocateLayoutTimeoutMs = 600L
private const val LocateDataTimeoutMs = 500L

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onPlatformEvent: (MainEvent) -> Boolean
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val dispatch = remember(viewModel) { viewModel::onAction }
    val dialogs = remember(dispatch) { MainDialogHost(dispatch) }
    val handles = remember(viewModel, dialogs) {
        MainScreenHandles(
            dispatch = dispatch,
            slices = MainSlices(viewModel::servers, viewModel::serverCount),
            scrollStates = GroupScrollStates(),
            showDialog = dialogs.show,
            requestRemove = dialogs.requestRemove
        )
    }
    var locateTarget by remember { mutableStateOf<LocateTarget?>(null) }

    MainDialogs(
        dialog = dialogs.current,
        onDismiss = dialogs.dismiss,
        onAction = dispatch,
        onRequestRemove = dialogs.requestRemove
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainDrawerContent(
                onAction = { action ->
                    scope.launch { drawerState.close() }
                    dispatch(action)
                }
            )
        }
    ) {
        BaseScreen(
            viewModel = viewModel,
            showLoading = false,
            onEvent = { event ->
                when (event) {
                    is MainEvent.ShowQrCode -> { dialogs.show(MainDialog.QrCode(event.bitmap)); true }
                    is MainEvent.LocateProfile -> { locateTarget = event.target; true }
                    is MainEvent -> onPlatformEvent(event)
                    else -> false
                }
            },
            onResult = { result -> dispatch(MainAction.ResultReceived(result)) },
            topBar = {
                val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
                val search by rememberSearchState(viewModel)
                MainTopBar(
                    isLoading = isLoading,
                    isSearchActive = search.isActive,
                    query = search.query,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onAction = dispatch,
                    onShowDialog = dialogs.show
                )
            },
            bottomBar = {
                val bottom by rememberBottomState(viewModel)
                MainBottomBar(
                    statusText = bottom.status.asText(),
                    isRunning = bottom.isRunning,
                    onAction = dispatch
                )
            }
        ) { state, _ ->
            SideEffect { dialogs.confirmRemove = state.confirmRemove }
            MainContent(
                args = MainPagerArgs(
                    groups = state.groups,
                    selectedGroupId = state.selectedGroupId,
                    selectedGuid = state.selectedGuid,
                    doubleColumnDisplay = state.doubleColumnDisplay,
                    isFiltering = state.isFiltering
                ),
                handles = handles,
                locateTarget = locateTarget,
                onLocateHandled = { locateTarget = null }
            )
        }
    }
}

@Immutable
private data class MainBottomState(
    val status: MainStatus = MainStatus.Disconnected,
    val isRunning: Boolean = false
)

@Composable
private fun rememberBottomState(viewModel: MainViewModel): State<MainBottomState> {
    val flow = remember(viewModel) {
        viewModel.uiState
            .map { MainBottomState(it.status, it.isRunning) }
            .distinctUntilChanged()
    }
    val initial = remember(viewModel) {
        viewModel.uiState.value.let { MainBottomState(it.status, it.isRunning) }
    }
    return flow.collectAsStateWithLifecycle(initialValue = initial)
}

@Immutable
private data class MainSearchState(
    val isActive: Boolean = false,
    val query: String = ""
)

@Composable
private fun rememberSearchState(viewModel: MainViewModel): State<MainSearchState> {
    val flow = remember(viewModel) {
        viewModel.uiState
            .map { MainSearchState(it.isSearchActive, it.searchQuery) }
            .distinctUntilChanged()
    }
    val initial = remember(viewModel) {
        viewModel.uiState.value.let { MainSearchState(it.isSearchActive, it.searchQuery) }
    }
    return flow.collectAsStateWithLifecycle(initialValue = initial)
}

@Immutable
private data class MainPagerArgs(
    val groups: List<GroupMapItem>,
    val selectedGroupId: String,
    val selectedGuid: String?,
    val doubleColumnDisplay: Boolean,
    val isFiltering: Boolean
)

@Composable
private fun MainContent(
    args: MainPagerArgs,
    handles: MainScreenHandles,
    locateTarget: LocateTarget?,
    onLocateHandled: () -> Unit,
    modifier: Modifier = Modifier
) {
    val groups = args.groups
    if (groups.isEmpty()) return
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { groups.size })
    val scrollStates = handles.scrollStates

    LaunchedEffect(groups) {
        scrollStates.retain(groups.mapTo(HashSet()) { it.id })
        val index = groups.indexOfFirst { it.id == args.selectedGroupId }
        if (index >= 0 && index != pagerState.currentPage) pagerState.scrollToPage(index)
    }

    LaunchedEffect(pagerState, groups) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                groups.getOrNull(page)?.let { handles.dispatch(MainAction.SelectGroup(it.id)) }
            }
    }

    LaunchedEffect(locateTarget) {
        val target = locateTarget ?: return@LaunchedEffect
        try {
            val groupId = target.groupId
            val serverGuid = target.serverGuid

            val index = groups.indexOfFirst { it.id == groupId }
            if (index !in 0 until pagerState.pageCount) return@LaunchedEffect
            if (pagerState.settledPage != index) {
                pagerState.scrollToPage(index)
            }

            val rows = withTimeoutOrNull(LocateDataTimeoutMs) {
                handles.slices.servers(groupId).first { it.isNotEmpty() }
            } ?: handles.slices.servers(groupId).value

            val position = rows.indexOfFirst { it.guid == serverGuid }
            if (position < 0) {
                handles.dispatch(MainAction.LocateFailed)
                return@LaunchedEffect
            }

            if (args.doubleColumnDisplay) {
                val grid = scrollStates.grid(groupId)
                withTimeoutOrNull(LocateLayoutTimeoutMs) {
                    snapshotFlow { grid.layoutInfo.viewportSize.height }.first { it > 0 }
                }
                grid.scrollToItem(
                    position,
                    -grid.layoutInfo.viewportSize.height / LocateViewportDivisor
                )
            } else {
                val list = scrollStates.list(groupId)
                withTimeoutOrNull(LocateLayoutTimeoutMs) {
                    snapshotFlow { list.layoutInfo.viewportSize.height }.first { it > 0 }
                }
                list.scrollToItem(
                    position,
                    -list.layoutInfo.viewportSize.height / LocateViewportDivisor
                )
            }
        } finally {
            onLocateHandled()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (groups.size > 1) {
            GroupTabBar(
                groups = groups,
                selectedTabIndex = pagerState.currentPage.coerceIn(0, groups.lastIndex),
                counts = handles.slices.counts,
                onTabClick = { index ->
                    scope.launch { pagerState.scrollToPage(index) }
                }
            )
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0,
            key = { page -> groups.getOrNull(page)?.id ?: "group-page-$page" }
        ) { page ->
            val group = groups.getOrNull(page) ?: return@HorizontalPager
            GroupPagerPage(
                groupId = group.id,
                selectedGuid = args.selectedGuid,
                doubleColumnDisplay = args.doubleColumnDisplay,
                canReorder = group.id.isNotEmpty() && !args.isFiltering,
                handles = handles,
                contentPadding = PaddingValues(bottom = ListBottomPadding)
            )
        }
    }
}

@Composable
private fun MainStatus.asText(): String = when (this) {
    MainStatus.Disconnected -> stringResource(R.string.connection_not_connected)
    MainStatus.Connected -> stringResource(R.string.connection_connected)
    MainStatus.Testing -> stringResource(R.string.connection_test_testing)
    is MainStatus.TestProgress -> stringResource(R.string.connection_running_task_left, progress)
    is MainStatus.ConnectionTest -> formatConnectionTestResult(result)
}

@Composable
private fun formatConnectionTestResult(result: ConnectionTestResult): String {
    val status = if (result.delayMillis >= 0) {
        val delay = stringResource(R.string.server_test_delay_value, result.delayMillis)
        stringResource(R.string.connection_test_available, delay)
    } else {
        val detail = result.errorMessage.ifBlank {
            stringResource(R.string.connection_test_empty_message)
        }
        stringResource(R.string.connection_test_error, detail)
    }

    if (result.delayMillis < 0 || (result.country == null && result.ipAddress == null)) {
        return status
    }

    val unknown = stringResource(R.string.value_unknown)
    return "$status\n(${result.country ?: unknown}) ${result.ipAddress ?: unknown}"
}
