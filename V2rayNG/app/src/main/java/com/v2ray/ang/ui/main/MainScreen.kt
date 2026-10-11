package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.AutoConnectButton
import com.v2ray.ang.ui.compose.XfOrange
import com.v2ray.ang.ui.compose.XfOrangeLight
import com.v2ray.ang.ui.compose.QRCodeDialog
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val psiphonActive by mainViewModel.psiphonActive.collectAsStateWithLifecycle()
    val speed by mainViewModel.trafficSpeed.collectAsStateWithLifecycle()
    val autoSwitch by mainViewModel.autoSwitch.collectAsStateWithLifecycle()
    val updateAvailable by mainViewModel.updateAvailable.collectAsStateWithLifecycle()
    // Home has two pages side by side, Matixray and Psiphon, moved by a swipe or a tap on the titles; the bottom bar
    // switches between Home (0) and the server list (1).
    val homePager = rememberPagerState(initialPage = 0, pageCount = { 2 })
    var bottomTab by rememberSaveable { mutableStateOf(0) }
    androidx.activity.compose.BackHandler(enabled = bottomTab != 0) { bottomTab = 0 }
    val isRunning = uiState.isRunning

    // One timer for the connection, kept here so it does not restart when the section changes. The start time is
    // saved across rotation; the tick is aligned to the second boundary so the digits never skip. The seconds are
    // read through a lambda, so only the timer text is recomposed each second, not the screen.
    var startedAt by rememberSaveable { mutableStateOf(0L) }
    val elapsedSec = remember { mutableStateOf(0L) }
    LaunchedEffect(isRunning) {
        if (isRunning) {
            // 0 = not started; a start time in the future is stale (the device rebooted), so it restarts too
            if (startedAt == 0L || startedAt > android.os.SystemClock.elapsedRealtime()) startedAt = android.os.SystemClock.elapsedRealtime()
            while (true) {
                val spent = android.os.SystemClock.elapsedRealtime() - startedAt
                elapsedSec.value = spent / 1000L
                kotlinx.coroutines.delay(1000L - spent % 1000L)
            }
        } else {
            startedAt = 0L
            elapsedSec.value = 0L
        }
    }
    val displayText = mainViewModel.formatStatus(uiState.status)
    val selectedGuid = uiState.selectedGuid
    val doubleColumnDisplay = uiState.doubleColumnDisplay
    val confirmRemove = uiState.confirmRemove
    val shareQRCodeBitmap = uiState.shareQRCodeBitmap

    // Matixray: a move of a profile the storage refused is told once, each refusal by its number, so that one set again
    // right after the last was told is told too; the groups are shown anew, as stored.
    val context = LocalContext.current
    val failureText = stringResource(R.string.toast_failure)
    val moveRefusal = uiState.moveRefusal
    LaunchedEffect(moveRefusal) {
        if (moveRefusal != null) {
            context.toastError(failureText)
            onAction(MainAction.MoveRefusalShown(moveRefusal))
        }
    }

    val isDarkTheme = LocalDarkTheme.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showDelAllConfirm by remember { mutableStateOf(false) }
    var showDelDuplicateConfirm by remember { mutableStateOf(false) }
    var showDelInvalidConfirm by remember { mutableStateOf(false) }
    var showRemoveConfirm by rememberSaveable(stateSaver = ServerDeleteTarget.Saver) {
        mutableStateOf<ServerDeleteTarget?>(null)
    }

    var shareTarget by remember { mutableStateOf<Triple<String, ProfileItem, Boolean>?>(null) }
    val removeServer: (String, String) -> Unit = { guid, profileName ->
        if (confirmRemove) {
            showRemoveConfirm = ServerDeleteTarget(guid, profileName)
        } else {
            onAction(MainAction.RemoveServer(guid))
        }
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { groups.size.coerceAtLeast(1) }
    )

    val lazyListStates = remember { mutableStateMapOf<String, LazyListState>() }
    val lazyGridStates = remember { mutableStateMapOf<String, LazyGridState>() }

    LaunchedEffect(groups) {
        val validGroupIds = groups.map { it.id }.toSet()
        lazyListStates.keys.retainAll(validGroupIds)
        lazyGridStates.keys.retainAll(validGroupIds)
    }

    LaunchedEffect(groups, uiState.selectedGroupId) {
        if (groups.isEmpty()) return@LaunchedEffect
        val selectedIndex = groups.indexOfFirst { it.id == uiState.selectedGroupId }
            .takeIf { it >= 0 } ?: 0
        if (!pagerState.isScrollInProgress && pagerState.settledPage != selectedIndex) {
            pagerState.scrollToPage(selectedIndex)
        }
    }

    val latestGroups by rememberUpdatedState(groups)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val currentGroups = latestGroups
                if (page in currentGroups.indices) {
                    onAction(MainAction.SelectGroup(currentGroups[page].id))
                }
            }
    }

    uiState.updateNotice?.let { notice ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { onAction(MainAction.DismissUpdateNotice) },
            title = { Text(stringResource(R.string.mx_update_notice_title)) },
            text = { Text(stringResource(R.string.mx_update_notice_body, notice.latestVersion.orEmpty())) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onAction(MainAction.DismissUpdateNotice)
                    notice.downloadUrl?.let { com.v2ray.ang.util.Utils.openUri(context, it) }
                }) { Text(stringResource(R.string.update_now)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { onAction(MainAction.DismissUpdateNotice) }) {
                    Text(stringResource(R.string.mx_update_later))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    MainDialogs(
        showDelAllConfirm = showDelAllConfirm,
        onDismissDelAll = { showDelAllConfirm = false },
        onConfirmDelAll = { showDelAllConfirm = false; onAction(MainAction.RemoveAllServers) },
        showDelDuplicateConfirm = showDelDuplicateConfirm,
        onDismissDelDuplicate = { showDelDuplicateConfirm = false },
        onConfirmDelDuplicate = { showDelDuplicateConfirm = false; onAction(MainAction.RemoveDuplicateServers) },
        showDelInvalidConfirm = showDelInvalidConfirm,
        onDismissDelInvalid = { showDelInvalidConfirm = false },
        onConfirmDelInvalid = { showDelInvalidConfirm = false; onAction(MainAction.RemoveInvalidServers) },
        showRemoveConfirm = showRemoveConfirm,
        onDismissRemove = { showRemoveConfirm = null },
        onConfirmRemove = { guid -> showRemoveConfirm = null; onAction(MainAction.RemoveServer(guid)) }
    )

    if (shareTarget != null) {
        val (guid, profile, more) = shareTarget!!
        ShareMethodDialog(
            guid = guid,
            profile = profile,
            more = more,
            onDismiss = { shareTarget = null },
            onAction = onAction,
            onRemove = removeServer,
        )
    }
    if (shareQRCodeBitmap != null) {
        QRCodeDialog(bitmap = shareQRCodeBitmap, onDismiss = { onAction(MainAction.DismissQRCodeDialog) })
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainDrawerContent(
                drawerState = drawerState,
                updateAvailable = updateAvailable,
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    onNavigate(route)
                }
            )
        }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
            topBar = {
                MainTopBar(
                    isLoading = isLoading,
                    showSearch = showSearch,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { query: String ->
                        searchQuery = query
                        onAction(MainAction.Search(query))
                    },
                    onSearchClose = {
                        searchQuery = ""
                        onAction(MainAction.Search(""))
                        showSearch = false
                    },
                    onSearchToggle = { show: Boolean -> showSearch = show },
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onAction = onAction,
                    onMoreMenuAction = { action ->
                        when (action) {
                            MainMoreMenuAction.RestartService -> onAction(MainAction.RestartService)
                            MainMoreMenuAction.DeleteAll -> showDelAllConfirm = true
                            MainMoreMenuAction.DeleteDuplicate -> showDelDuplicateConfirm = true
                            MainMoreMenuAction.DeleteInvalid -> showDelInvalidConfirm = true
                            MainMoreMenuAction.ExportAll -> onAction(MainAction.ExportAll)
                            MainMoreMenuAction.LocateSelected -> onAction(MainAction.LocateSelectedServer)
                            MainMoreMenuAction.SortByTestResults -> onAction(MainAction.SortByTestResults)
                            MainMoreMenuAction.TestAll -> onAction(MainAction.TestAllServers)
                            MainMoreMenuAction.TestAllRealPing -> onAction(MainAction.TestRealAllServers)
                            MainMoreMenuAction.UpdateSubscriptions -> onAction(MainAction.UpdateSubscriptions)
                        }
                    }
                )
            },
            bottomBar = { MainBottomBar(selected = bottomTab, onSelect = { bottomTab = it }) },
            floatingActionButton = {},
        ) { innerPadding ->
            if (groups.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (bottomTab == 0) {
                        val section = if (homePager.currentPage == 1) MainSection.Psiphon else MainSection.Main
                        MainSectionTabs(
                            selected = section,
                            onSelect = { target -> scope.launch { homePager.animateScrollToPage(target.ordinal) } },
                        )
                        HorizontalPager(
                            state = homePager,
                            modifier = Modifier.fillMaxSize(),
                            beyondViewportPageCount = 1,
                        ) { page ->
                            if (page == MainSection.Psiphon.ordinal) {
                                PsiphonSection(
                                    connected = psiphonActive,
                                    elapsedSec = { elapsedSec.value },
                                    speed = speed,
                                    pingMillis = uiState.pingMillis,
                                    onToggle = { onAction(MainAction.TogglePsiphon) },
                                    onStatusClick = { onAction(MainAction.TestCurrentServer) },
                                )
                            } else {
                                val selectedName = remember(selectedGuid) {
                                    selectedGuid?.let { com.v2ray.ang.handler.MmkvManager.decodeServerConfig(it)?.remarks }
                                }
                                MainConnectHero(
                                    isRunning = isRunning,
                                    elapsedSec = { elapsedSec.value },
                                    statusText = displayText,
                                    onToggle = { onAction(MainAction.ToggleService) },
                                    speed = speed,
                                    pingMillis = uiState.pingMillis,
                                    onStatusClick = { onAction(MainAction.TestCurrentServer) },
                                    bottomExtra = {
                                        AutoSwitchRow(checked = autoSwitch, onCheckedChange = { mainViewModel.setAutoSwitch(it) })
                                        SelectedServerCard(name = selectedName, onClick = { bottomTab = 1 })
                                    },
                                )
                            }
                        }
                    } else {
                        AutoConnectButton(
                            testing = uiState.isTesting,
                            onClick = { onAction(MainAction.AutoConnectBest) }
                        )
                        // Subscriptions also update by themselves every 15 minutes; this is the manual update.
                        com.v2ray.ang.ui.compose.SlidingPillButton(
                            text = stringResource(R.string.title_sub_update),
                            onClick = { onAction(MainAction.UpdateSubscriptions) },
                            container = MaterialTheme.colorScheme.surfaceContainerHigh,
                            content = MaterialTheme.colorScheme.onSurface,
                        )
                        if (groups.size > 1) {
                            GroupTabBar(
                                groups = groups,
                                selectedTabIndex = pagerState.currentPage.coerceIn(0, groups.lastIndex),
                                mainViewModel = mainViewModel,
                                onTabClick = { targetIndex ->
                                    scope.launch {
                                        pagerState.navigateToPageOptimized(
                                            targetPage = targetIndex,
                                            animateAdjacentPage = true
                                        )
                                    }
                                }
                            )
                        }

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = true,
                            beyondViewportPageCount = 1,
                            key = { page -> groups.getOrNull(page)?.id ?: "group-page-$page" }
                        ) { page ->
                            val group = groups.getOrNull(page) ?: return@HorizontalPager

                            GroupPagerPage(
                                groupId = group.id,
                                mainViewModel = mainViewModel,
                                selectedGuid = selectedGuid,
                                locateTarget = uiState.locateTarget,
                                doubleColumnDisplay = doubleColumnDisplay,
                                searchQuery = searchQuery,
                                lazyListStates = lazyListStates,
                                lazyGridStates = lazyGridStates,
                                onSelectServer = { guid -> onAction(MainAction.SelectServer(guid)) },
                                onEditServer = { guid, profile -> onAction(MainAction.EditServer(guid, profile)) },
                                onShareServer = { guid, profile ->
                                    shareTarget = Triple(guid, profile, false)
                                },
                                onMoreServer = { guid, profile ->
                                    shareTarget = Triple(guid, profile, true)
                                },
                                onRemoveServer = removeServer,
                                contentPadding = PaddingValues(
                                    start = 0.dp,
                                    top = 0.dp,
                                    end = 0.dp,
                                    bottom = 24.dp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The Psiphon section: its own page in orange, with the same big connect button and live numbers as the main section.
 * The button connects through a Psiphon profile (made on the spot when there is none) or disconnects.
 */
@Composable
private fun PsiphonSection(
    connected: Boolean,
    elapsedSec: () -> Long,
    speed: TrafficSpeed,
    pingMillis: Long?,
    onToggle: () -> Unit,
    onStatusClick: () -> Unit,
) {
    MainConnectHero(
        isRunning = connected,
        elapsedSec = elapsedSec,
        statusText = stringResource(if (connected) R.string.mx_psiphon_connected else R.string.mx_psiphon_connect),
        onToggle = onToggle,
        speed = speed,
        pingMillis = pingMillis,
        accent = XfOrange,
        accentLight = XfOrangeLight,
        onStatusClick = onStatusClick,
        bottomExtra = {
            Text(
                text = stringResource(R.string.mx_psiphon_section_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            )
        },
    )
}
