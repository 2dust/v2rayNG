package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.QRCodeDialog
import com.v2ray.ang.ui.compose.SelectListDialog
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val selectedGroupIndex = mainSelectedGroupIndex(groups, uiState.selectedGroupId)
    val selectGroup: (String) -> Unit = { onAction(MainAction.SelectGroup(it)) }
    val pagerState = rememberMainPhonePagerState(groups, uiState.selectedGroupId, selectGroup)
    val scrollStates = rememberMainGroupScrollStates(groups.map { it.id })
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val bottomBarHeight = with(LocalDensity.current) { bottomBarHeightPx.toDp() }
    var showSearch by rememberSaveable { mutableStateOf(uiState.searchQuery.isNotEmpty()) }
    fun closeSearch() {
        onAction(MainAction.Search(""))
        showSearch = false
    }

    MainDialogs(
        uiState.confirmRemove, uiState.shareQRCodeBitmap, onAction,
        deleteDialog = { message, name, confirm, dismiss -> DeleteConfirmDialog(message, confirm, dismiss, name) },
        shareDialog = { options, select, dismiss -> SelectListDialog(options, { stringResource(it.labelRes) }, select, dismiss) },
        qrCodeDialog = { bitmap, dismiss -> QRCodeDialog(bitmap, dismiss) }
    ) { dialogs ->
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                MainDrawerContent(drawerState) { destination ->
                    drawerScope.launch { drawerState.close() }
                    onNavigate(destination)
                }
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
                    topBar = {
                        MainTopBar(
                            isLoading = isLoading,
                            showSearch = showSearch,
                            searchQuery = uiState.searchQuery,
                            onSearchQueryChange = { onAction(MainAction.Search(it)) },
                            onSearchClose = ::closeSearch,
                            onSearchOpen = { showSearch = true },
                            onOpenDrawer = { drawerScope.launch { drawerState.open() } },
                            onAction = onAction,
                            onBulkDelete = dialogs.bulkDelete
                        )
                    }
                ) { innerPadding ->
                    if (groups.isNotEmpty()) {
                        val direction = LocalLayoutDirection.current
                        val contentInsets = PaddingValues(
                            start = innerPadding.calculateStartPadding(direction),
                            top = innerPadding.calculateTopPadding(),
                            end = innerPadding.calculateEndPadding(direction)
                        )
                        Column(modifier = Modifier.fillMaxSize().padding(contentInsets)) {
                            if (groups.size > 1) GroupTabBar(groups, selectedGroupIndex, selectGroup)
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                beyondViewportPageCount = 1,
                                key = { page -> groups.getOrNull(page)?.id ?: "group-page-$page" }
                            ) { page ->
                                groups.getOrNull(page)?.let { group ->
                                    GroupPagerPage(
                                        group = group,
                                        mainViewModel = mainViewModel,
                                        uiState = uiState,
                                        scrollStates = scrollStates,
                                        onAction = onAction,
                                        dialogs = dialogs,
                                        contentPadding = PaddingValues(bottom = bottomBarHeight + 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                MainBottomBar(
                    displayText = mainViewModel.formatStatus(uiState.status),
                    isRunning = uiState.isRunning,
                    isDarkTheme = LocalDarkTheme.current,
                    onAction = onAction,
                    modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { bottomBarHeightPx = it.height }
                )
            }
        }
    }
}
