package com.v2ray.ang.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.QRCodeDialog
import com.v2ray.ang.ui.compose.isTelevisionDevice
import com.v2ray.ang.ui.compose.rememberDpadFocusTargets
import com.v2ray.ang.ui.compose.rememberLazyDpadFocus
import com.v2ray.ang.ui.compose.requestFocusWhenReady
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val isTelevision = isTelevisionDevice()
    val isRunning = uiState.isRunning
    val displayText = if (isTelevision && uiState.status == MainStatus.Connected) {
        stringResource(R.string.connection_connected_tv)
    } else {
        mainViewModel.formatStatus(uiState.status)
    }
    val selectedGuid = uiState.selectedGuid
    val testDisplayText = uiState.testStatus
        ?.let(mainViewModel::formatStatus)
        .orEmpty()
    val doubleColumnDisplay = uiState.doubleColumnDisplay
    val confirmRemove = isTelevision || uiState.confirmRemove
    val shareQRCodeBitmap = uiState.shareQRCodeBitmap

    val isDarkTheme = LocalDarkTheme.current
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val bottomBarHeight = with(LocalDensity.current) { bottomBarHeightPx.toDp() }
    val drawerState = if (!isTelevision) rememberDrawerState(DrawerValue.Closed) else null
    val drawerScope = rememberCoroutineScope()
    val tvDrawerCoordinator = if (isTelevision) rememberMainTvDrawerCoordinator() else null
    val isDrawerOpen = tvDrawerCoordinator?.isOpen == true
    val openDrawer: (FocusRequester?) -> Unit = { requester ->
        if (tvDrawerCoordinator != null) tvDrawerCoordinator.openFrom { requester?.requestFocus() ?: false }
        else drawerScope.launch { drawerState?.open() }
    }
    var dialog by remember { mutableStateOf<MainDialog?>(null) }
    var deleteTarget by rememberSaveable(stateSaver = ServerDeleteTarget.Saver) {
        mutableStateOf<ServerDeleteTarget?>(null)
    }
    var dialogFocusToRestore by remember { mutableStateOf<(() -> Boolean)?>(null) }
    LaunchedEffect(dialogFocusToRestore, isTelevision) {
        val restore = dialogFocusToRestore ?: return@LaunchedEffect
        if (isTelevision) restore()
        dialogFocusToRestore = null
    }
    val requestRemoveServer: (String, String) -> Unit = { guid, name ->
        if (confirmRemove) {
            deleteTarget = ServerDeleteTarget(guid, name)
        } else {
            onAction(MainAction.RemoveServer(guid))
        }
    }
    var showSearch by rememberSaveable { mutableStateOf(uiState.searchQuery.isNotEmpty()) }
    fun closeSearch() {
        onAction(MainAction.Search(""))
        showSearch = false
    }
    val topBarFocus = rememberMainTopBarFocusRequesters(showSearch)

    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeFocusGeneration by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner, isTelevision) {
        if (!isTelevision) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeFocusGeneration++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(tvDrawerCoordinator, isDrawerOpen) {
        if (isDrawerOpen) return@LaunchedEffect
        val restore = tvDrawerCoordinator?.takeFocusToRestore() ?: return@LaunchedEffect
        if (!restore()) requestFocusWhenReady(topBarFocus.start)
    }

    BackHandler(enabled = isTelevision && showSearch, onBack = ::closeSearch)
    BackHandler(enabled = isDrawerOpen) {
        tvDrawerCoordinator?.close()
    }

    val groupIds = groups.map { it.id }
    val selectedGroupIndex = mainSelectedGroupIndex(groups, uiState.selectedGroupId)
    val scrollStates = rememberMainGroupScrollStates(groupIds)
    val selectGroup: (String) -> Unit = { onAction(MainAction.SelectGroup(it)) }
    val phonePager = if (!isTelevision) rememberMainPhonePagerState(groups, uiState.selectedGroupId, selectGroup) else null
    val tvTabState = if (isTelevision && groups.size > 1) rememberLazyListState(selectedGroupIndex) else null
    val tvTabTargets = if (tvTabState != null) rememberDpadFocusTargets(groupIds) { FocusRequester() } else emptyMap()
    val tvTabOrder = if (tvTabState != null) groupIds.map(tvTabTargets::getValue) else emptyList()
    val requestTabFocus = if (tvTabState != null) {
        rememberLazyDpadFocus(tvTabOrder.map { listOf(it) }) { tvTabState.scrollToItem(it) }
    } else null
    fun focusGroupTab(groupId: String): Boolean = tvTabTargets[groupId]?.let { requestTabFocus?.invoke(it) } ?: false

    (dialog as? MainDialog.BulkDelete)?.target?.let { target ->
        DeleteConfirmDialog(
            message = stringResource(target.messageRes),
            onConfirm = { onAction(target.action) },
            onDismiss = { dialog = null }
        )
    }

    deleteTarget?.let { target ->
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_profile),
            itemName = target.profileName,
            onConfirm = { onAction(MainAction.RemoveServer(target.guid)) },
            onDismiss = { deleteTarget = null }
        )
    }

    (dialog as? MainDialog.Share)?.target?.let { target ->
        ShareMethodDialog(
            guid = target.guid,
            profile = target.profile,
            more = target.more,
            onDismiss = {
                dialog = null
                dialogFocusToRestore = target.restoreFocus
            },
            onActionSelected = { dialog = null },
            onAction = onAction,
            onRemove = requestRemoveServer
        )
    }
    if (shareQRCodeBitmap != null) {
        QRCodeDialog(bitmap = shareQRCodeBitmap, onDismiss = { onAction(MainAction.DismissQRCodeDialog) })
    }

    val mainContent: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
                topBar = {
                    MainTopBar(
                        isLoading = isLoading,
                        isRunning = isRunning,
                        showSearch = showSearch,
                        searchQuery = uiState.searchQuery,
                        focusRequesters = topBarFocus,
                        onSearchQueryChange = { query ->
                            onAction(MainAction.Search(query))
                        },
                        onSearchClose = ::closeSearch,
                        onSearchToggle = { showSearch = it },
                        onOpenDrawer = openDrawer,
                        onMoveDown = { groups.getOrNull(selectedGroupIndex)?.let { focusGroupTab(it.id) } ?: false },
                        onAction = onAction,
                        onBulkDelete = { dialog = MainDialog.BulkDelete(it) }
                    )
                },
                bottomBar = {
                    if (isTelevision) {
                        MainTvStatusBar(displayText, testDisplayText.replace('\n', ' '))
                    }
                }
            ) { innerPadding ->
                if (groups.isNotEmpty()) {
                    val layoutDirection = LocalLayoutDirection.current
                    val contentInsets = if (isTelevision) innerPadding else PaddingValues(
                        start = innerPadding.calculateStartPadding(layoutDirection),
                        top = innerPadding.calculateTopPadding(),
                        end = innerPadding.calculateEndPadding(layoutDirection)
                    )
                    Column(modifier = Modifier.fillMaxSize().padding(contentInsets)) {
                        if (tvTabState != null && requestTabFocus != null) {
                            TvGroupTabBar(groups, selectedGroupIndex, tvTabState, tvTabOrder, requestTabFocus,
                                onOpenDrawer = { target -> tvDrawerCoordinator?.openFrom { requestTabFocus(target) } },
                                onMoveUp = { topBarFocus.start.requestFocus() },
                                onSelectGroup = selectGroup)
                        } else if (phonePager != null && groups.size > 1) {
                            GroupTabBar(groups, selectedGroupIndex, selectGroup)
                        }

                        val groupPage: @Composable (GroupMapItem) -> Unit = { group ->
                            GroupPagerPage(
                                groupId = group.id,
                                mainViewModel = mainViewModel,
                                selectedGuid = selectedGuid,
                                locateTarget = uiState.locateTarget,
                                doubleColumnDisplay = doubleColumnDisplay,
                                revealSelectedGeneration = resumeFocusGeneration,
                                searchQuery = uiState.searchQuery,
                                scrollStates = scrollStates,
                                onSelectServer = { guid -> onAction(MainAction.SelectServer(guid)) },
                                onEditServer = { guid, profile ->
                                    onAction(MainAction.EditServer(guid, profile))
                                },
                                onShareServer = { guid, profile, more, restore ->
                                    dialog = MainDialog.Share(MainShareTarget(guid, profile, more, restore))
                                },
                                onRemoveServer = requestRemoveServer,
                                usageInfo = group.usageInfo,
                                groupTitle = group.remarks,
                                onOpenLink = { url -> onAction(MainAction.OpenSubscriptionLink(url)) },
                                onOpenDrawer = { restore -> tvDrawerCoordinator?.openFrom(restore) },
                                onBackFromList = { topBarFocus.start.requestFocus() },
                                onMoveUpFromFirstRow = if (tvTabState != null) {
                                    { focusGroupTab(group.id) }
                                } else null,
                                contentPadding = PaddingValues(
                                    start = if (isTelevision) 48.dp else 0.dp,
                                    top = if (isTelevision) 16.dp else 0.dp,
                                    end = if (isTelevision) 48.dp else 0.dp,
                                    bottom = if (isTelevision) 80.dp else bottomBarHeight + 8.dp
                                )
                            )
                        }
                        if (phonePager != null) {
                            HorizontalPager(
                                state = phonePager,
                                modifier = Modifier.fillMaxSize(),
                                beyondViewportPageCount = 1,
                                key = { page -> groups.getOrNull(page)?.id ?: "group-page-$page" }
                            ) { page ->
                                groups.getOrNull(page)?.let { groupPage(it) }
                            }
                        } else {
                            val group = groups[selectedGroupIndex]
                            key(group.id) { groupPage(group) }
                        }
                    }
                }
            }
            if (!isTelevision) {
                MainBottomBar(
                    displayText = testDisplayText.ifBlank { displayText },
                    isRunning = isRunning,
                    isDarkTheme = isDarkTheme,
                    onAction = onAction,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { bottomBarHeightPx = it.height }
                )
            }
        }
    }

    if (tvDrawerCoordinator != null) {
        TvMainNavigationDrawer(
            drawerState = tvDrawerCoordinator.state,
            focusGeneration = resumeFocusGeneration,
            onClose = tvDrawerCoordinator::close,
            onNavigate = onNavigate,
            content = mainContent
        )
    } else {
        val phoneDrawerState = requireNotNull(drawerState)
        ModalNavigationDrawer(
            drawerState = phoneDrawerState,
            drawerContent = {
                MainDrawerContent(
                    drawerState = phoneDrawerState,
                    onNavigate = { destination ->
                        drawerScope.launch { phoneDrawerState.close() }
                        onNavigate(destination)
                    }
                )
            },
            content = mainContent
        )
    }
}
