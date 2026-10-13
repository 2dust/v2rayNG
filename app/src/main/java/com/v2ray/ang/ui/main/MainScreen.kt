package com.v2ray.ang.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.compose.QRCodeDialog
import com.v2ray.ang.ui.compose.verticalScrollbar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class ExpressMainTab {
    Connect,
    Locations
}

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val isRunning = uiState.isRunning
    val displayText = mainViewModel.formatStatus(uiState.status)
    val selectedGuid = uiState.selectedGuid
    val doubleColumnDisplay = uiState.doubleColumnDisplay
    val confirmRemove = uiState.confirmRemove
    val shareQRCodeBitmap = uiState.shareQRCodeBitmap

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentTab by rememberSaveable { mutableStateOf(ExpressMainTab.Connect) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showDelAllConfirm by remember { mutableStateOf(false) }
    var showDelDuplicateConfirm by remember { mutableStateOf(false) }
    var showDelInvalidConfirm by remember { mutableStateOf(false) }
    var showRemoveConfirm by rememberSaveable(stateSaver = ServerDeleteTarget.Saver) {
        mutableStateOf<ServerDeleteTarget?>(null)
    }

    var shareTarget by remember { mutableStateOf<Triple<String, ProfileItem, Boolean>?>(null) }
    var showLocationsAddMenu by remember { mutableStateOf(false) }
    val locationsAddMenuScrollState = rememberScrollState()

    // Retrieve active profile & ping info
    val selectedProfile = remember(selectedGuid, groups) {
        selectedGuid?.let { MmkvManager.decodeServerConfig(it) }
    }
    val selectedPing = remember(selectedGuid, uiState.status) {
        selectedGuid?.let { MmkvManager.decodeServerAffiliationInfo(it)?.testDelayMillis } ?: 0L
    }

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

    // Handle back button on secondary tab
    BackHandler(enabled = currentTab == ExpressMainTab.Locations) {
        currentTab = ExpressMainTab.Connect
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
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    onNavigate(route)
                }
            )
        }
    ) {
        Scaffold(
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
            topBar = {
                MainTopBar(
                    title = if (currentTab == ExpressMainTab.Connect) {
                        stringResource(R.string.app_name)
                    } else {
                        "Configurations"
                    },
                    isLoading = isLoading,
                    showSearch = showSearch && currentTab == ExpressMainTab.Locations,
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
            bottomBar = {
                val activeGreen = Color(0xFF05A870)
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    NavigationBarItem(
                        selected = currentTab == ExpressMainTab.Connect,
                        onClick = { currentTab = ExpressMainTab.Connect },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_shield_24dp),
                                contentDescription = "Connect",
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "Connect",
                                fontWeight = if (currentTab == ExpressMainTab.Connect) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = activeGreen,
                            selectedTextColor = activeGreen,
                            indicatorColor = activeGreen.copy(alpha = 0.15f)
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == ExpressMainTab.Locations,
                        onClick = { currentTab = ExpressMainTab.Locations },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_globe_24dp),
                                contentDescription = "Configurations",
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "Configurations",
                                fontWeight = if (currentTab == ExpressMainTab.Locations) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = activeGreen,
                            selectedTextColor = activeGreen,
                            indicatorColor = activeGreen.copy(alpha = 0.15f)
                        )
                    )
                }
            },
            floatingActionButton = {
                if (currentTab == ExpressMainTab.Locations) {
                    Box(modifier = Modifier.wrapContentSize(Alignment.BottomEnd)) {
                        ExtendedFloatingActionButton(
                            onClick = { showLocationsAddMenu = true },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_add_24dp),
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            },
                            text = {
                                Text(
                                    text = "Add Config",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            containerColor = Color(0xFF05A870),
                            shape = RoundedCornerShape(18.dp)
                        )

                        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                        DropdownMenu(
                            expanded = showLocationsAddMenu,
                            onDismissRequest = { showLocationsAddMenu = false },
                            scrollState = locationsAddMenuScrollState,
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .heightIn(max = screenHeight * 0.6f)
                                .verticalScrollbar(locationsAddMenuScrollState)
                        ) {
                            ImportMenuContent(
                                onAction = { action ->
                                    showLocationsAddMenu = false
                                    onAction(action)
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            when (currentTab) {
                ExpressMainTab.Connect -> {
                    ExpressConnectDashboard(
                        isRunning = isRunning,
                        statusText = displayText,
                        selectedGuid = selectedGuid,
                        selectedProfile = selectedProfile,
                        selectedPing = selectedPing,
                        onToggleService = { onAction(MainAction.ToggleService) },
                        onTestPing = { onAction(MainAction.TestCurrentServer) },
                        onOpenLocations = { currentTab = ExpressMainTab.Locations },
                        onAddConfig = { currentTab = ExpressMainTab.Locations; showLocationsAddMenu = true },
                        onRoutingClick = { onNavigate(MainDestination.Routing) },
                        onPerAppClick = { onNavigate(MainDestination.PerAppProxy) },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }

                ExpressMainTab.Locations -> {
                    if (groups.isEmpty()) {
                        // Express Empty State for configurations
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = Color(0xFF05A870).copy(alpha = 0.12f),
                                modifier = Modifier.size(90.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_globe_24dp),
                                        contentDescription = null,
                                        tint = Color(0xFF05A870),
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = "No Configurations Yet",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "behradNG requires you to add your own configs (VLESS, VMess, Shadowsocks, Trojan, Hysteria2, etc.) to establish VPN connection.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = { onAction(MainAction.ImportClipboard) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF05A870)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) {
                                Icon(painterResource(R.drawable.ic_copy), contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Import from Clipboard", fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = { onAction(MainAction.ImportQRcode) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) {
                                Icon(painterResource(R.drawable.ic_scan_24dp), contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scan QR Code", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
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
                                    onSelectServer = { guid ->
                                        onAction(MainAction.SelectServer(guid))
                                    },
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
                                        bottom = 90.dp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
