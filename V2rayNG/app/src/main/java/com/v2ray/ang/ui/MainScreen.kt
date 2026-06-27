package com.v2ray.ang.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.GroupMapItem
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    object Home : Screen("home", R.string.title_server, Icons.Outlined.Home, Icons.Filled.Home)
    object Settings : Screen("settings", R.string.title_settings, Icons.Outlined.Settings, Icons.Filled.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    groups: List<GroupMapItem>,
    isRunning: Boolean,
    onFabClick: () -> Unit,
    onMenuClick: (Int) -> Unit,
    onDrawerItemClick: (Int) -> Unit,
    onSelectServer: (String) -> Unit,
    onMoreClick: (String, com.v2ray.ang.dto.entities.ProfileItem, Int) -> Unit
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    // Move pagerState to HomeContent if we want it to persist, or keep here.
    // For simplicity, let's keep the Scaffold here and swap the content.

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentScreen == Screen.Home,
        drawerContent = {
            ModalDrawerSheet {
                DrawerHeader()
                Spacer(Modifier.height(12.dp))
                DrawerContent(onDrawerItemClick = {
                    onDrawerItemClick(it)
                    scope.launch { drawerState.close() }
                })
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(currentScreen.title)) },
                    navigationIcon = {
                        if (currentScreen == Screen.Home) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = null)
                            }
                        }
                    },
                    actions = {
                        if (currentScreen == Screen.Home) {
                            IconButton(onClick = { onMenuClick(R.id.import_qrcode) }) {
                                Icon(painterResource(R.drawable.ic_add_24dp), contentDescription = null)
                            }
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    val items = listOf(Screen.Home, Screen.Settings)
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(if (currentScreen == screen) screen.selectedIcon else screen.icon, contentDescription = null) },
                            label = { Text(stringResource(screen.title)) },
                            selected = currentScreen == screen,
                            onClick = { currentScreen = screen }
                        )
                    }
                }
            },
            floatingActionButton = {
                if (currentScreen == Screen.Home) {
                    ExtendedFloatingActionButton(
                        onClick = onFabClick,
                        icon = { 
                            Icon(
                                painter = painterResource(if (isRunning) R.drawable.ic_stop_24dp else R.drawable.ic_play_24dp),
                                contentDescription = null
                            ) 
                        },
                        text = { 
                            Text(stringResource(if (isRunning) R.string.notification_action_stop_v2ray else R.string.tasker_start_service)) 
                        },
                        containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        contentColor = if (isRunning) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                    )
                }
            },
            floatingActionButtonPosition = FabPosition.Center
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (currentScreen) {
                    Screen.Home -> HomeContent(
                        mainViewModel = mainViewModel,
                        groups = groups,
                        onSelectServer = onSelectServer,
                        onMoreClick = onMoreClick
                    )
                    Screen.Settings -> SettingsContent()
                }
            }
        }
    }
}

@Composable
fun HomeContent(
    mainViewModel: MainViewModel,
    groups: List<GroupMapItem>,
    onSelectServer: (String) -> Unit,
    onMoreClick: (String, com.v2ray.ang.dto.entities.ProfileItem, Int) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { groups.size })
    val scope = rememberCoroutineScope()

    // Sync pager with selected subscription
    LaunchedEffect(mainViewModel.subscriptionId, groups) {
        val targetIndex = groups.indexOfFirst { it.id == mainViewModel.subscriptionId }.takeIf { it >= 0 }
        if (targetIndex != null && targetIndex != pagerState.currentPage && targetIndex < groups.size) {
            pagerState.scrollToPage(targetIndex)
        }
    }

    LaunchedEffect(pagerState.currentPage, groups) {
        if (groups.isNotEmpty() && pagerState.currentPage < groups.size) {
            val subId = groups[pagerState.currentPage].id
            if (subId != mainViewModel.subscriptionId) {
                mainViewModel.subscriptionIdChanged(subId)
            }
        }
    }

    Column {
        if (groups.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 16.dp,
                divider = {},
                indicator = { tabPositions ->
                    if (pagerState.currentPage < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage])
                        )
                    }
                }
            ) {
                groups.forEachIndexed { index, group ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(group.remarks) }
                    )
                }
            }
        }

        // Progress Indicator
        val isLoading by mainViewModel.isLoadingFlow.collectAsStateWithLifecycle()
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ServerListScreen(
                mainViewModel = mainViewModel,
                onSelect = onSelectServer,
                onMoreClick = onMoreClick
            )
        }
    }
}

@Composable
fun SettingsContent() {
    AndroidView(
        factory = { ctx ->
            FragmentContainerView(ctx).apply {
                id = R.id.fragment_settings
                val fragmentActivity = ctx as? FragmentActivity
                fragmentActivity?.supportFragmentManager?.beginTransaction()
                    ?.replace(id, SettingsFragment())
                    ?.commit()
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun DrawerHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(MaterialTheme.colorScheme.primary)
            .padding(24.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Text(
            text = "Secure Proxy Client",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun DrawerContent(onDrawerItemClick: (Int) -> Unit) {
    val items = listOf(
        DrawerItemData(R.id.sub_setting, R.drawable.ic_subscriptions_24dp, R.string.title_sub_setting),
        DrawerItemData(R.id.per_app_proxy_settings, R.drawable.ic_per_apps_24dp, R.string.per_app_proxy_settings),
        DrawerItemData(R.id.routing_setting, R.drawable.ic_routing_24dp, R.string.routing_settings_title),
        DrawerItemData(R.id.user_asset_setting, R.drawable.ic_file_24dp, R.string.title_user_asset_setting),
        null, // Divider
        DrawerItemData(R.id.promotion, R.drawable.ic_promotion_24dp, R.string.title_pref_promotion),
        DrawerItemData(R.id.logcat, R.drawable.ic_logcat_24dp, R.string.title_logcat),
        DrawerItemData(R.id.check_for_update, R.drawable.ic_check_update_24dp, R.string.update_check_for_update),
        DrawerItemData(R.id.backup_restore, R.drawable.ic_restore_24dp, R.string.title_configuration_backup_restore),
        DrawerItemData(R.id.about, R.drawable.ic_about_24dp, R.string.title_about)
    )

    Column(modifier = Modifier.padding(12.dp)) {
        items.forEach { item ->
            if (item == null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            } else {
                NavigationDrawerItem(
                    icon = { Icon(painterResource(item.icon), contentDescription = null) },
                    label = { Text(stringResource(item.title)) },
                    selected = false,
                    onClick = { onDrawerItemClick(item.id) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    }
}

data class DrawerItemData(val id: Int, val icon: Int, val title: Int)
