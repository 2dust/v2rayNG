package com.v2ray.ang.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.DrawerValue as TvDrawerValue
import androidx.tv.material3.rememberDrawerState as rememberTvDrawerState
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.rememberDpadFocusTargets
import com.v2ray.ang.ui.compose.rememberLazyDpadFocus
import com.v2ray.ang.ui.compose.requestFocusWhenReady
import kotlinx.coroutines.flow.first

internal sealed interface TvMainFocusTarget {
    sealed interface DrawerOrigin : TvMainFocusTarget {
        companion object {
            val Saver = listSaver<DrawerOrigin?, String>(
                save = { target ->
                    when (target) {
                        null -> emptyList()
                        Toolbar -> listOf("toolbar")
                        is GroupTab -> listOf("tab", target.id)
                        is Profile -> listOf("profile", target.guid, target.action?.name.orEmpty())
                    }
                },
                restore = {
                    when (it[0]) {
                        "toolbar" -> Toolbar
                        "tab" -> GroupTab(it[1])
                        "profile" -> Profile(it[1], it[2].takeIf(String::isNotEmpty)?.let(ProfileRowAction::valueOf))
                        else -> null
                    }
                }
            )
        }
    }

    data object Toolbar : DrawerOrigin
    data class GroupTab(val id: String) : DrawerOrigin
    data class Profile(val guid: String, val action: ProfileRowAction? = null) : DrawerOrigin {
        companion object {
            val Saver = listSaver<Profile?, String>(
                save = { target -> target?.let { listOf(it.guid, it.action?.name.orEmpty()) }.orEmpty() },
                restore = { Profile(it[0], it[1].takeIf(String::isNotEmpty)?.let(ProfileRowAction::valueOf)) }
            )
        }
    }
    data class AfterRemoval(val origin: Profile, val neighbor: String?) : TvMainFocusTarget
}

@Composable
internal fun TvMainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val groupIds = groups.map { it.id }
    val selectedGroupIndex = groups.indexOfFirst { it.id == uiState.selectedGroupId }.coerceAtLeast(0)
    val selectedGroup = groups.getOrNull(selectedGroupIndex)
    val groupScrollStates = rememberSaveableStateHolder()
    val knownGroupIds = remember { mutableSetOf<String>() }
    val lastReveals = remember { mutableMapOf<String, ProfileReveal>() }
    LaunchedEffect(groupIds) {
        val ids = groupIds.toSet()
        (knownGroupIds - ids).forEach(groupScrollStates::removeState)
        knownGroupIds.clear()
        knownGroupIds.addAll(ids)
        lastReveals.keys.retainAll(ids)
    }
    val drawerState = rememberTvDrawerState(TvDrawerValue.Closed)
    val isDrawerOpen = drawerState.currentValue == TvDrawerValue.Open
    var drawerOrigin by rememberSaveable(stateSaver = TvMainFocusTarget.DrawerOrigin.Saver) {
        mutableStateOf<TvMainFocusTarget.DrawerOrigin?>(null)
    }
    var focusToRestore by remember { mutableStateOf<TvMainFocusTarget?>(null) }
    fun openDrawer(origin: TvMainFocusTarget.DrawerOrigin) {
        drawerOrigin = origin
        drawerState.setValue(TvDrawerValue.Open)
    }
    var showSearch by rememberSaveable { mutableStateOf(uiState.searchQuery.isNotEmpty()) }
    fun closeSearch() {
        onAction(MainAction.Search(""))
        showSearch = false
    }

    val topBarFocus = remember { TvMainTopBarFocus() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeFocusGeneration by remember { mutableIntStateOf(0) }
    var profileRevealGeneration by remember { mutableIntStateOf(0) }
    var editorOrigin by rememberSaveable(stateSaver = TvMainFocusTarget.Profile.Saver) {
        mutableStateOf<TvMainFocusTarget.Profile?>(null)
    }
    fun openEditor(action: MainAction.EditServer, origin: TvMainFocusTarget.Profile) {
        editorOrigin = origin
        onAction(action)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeFocusGeneration++
                // Restore the editor's row instead of scrolling it away to the active profile.
                val origin = editorOrigin
                if (origin == null) profileRevealGeneration++
                else focusToRestore = origin
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun closeDrawer() {
        drawerState.setValue(TvDrawerValue.Closed)
    }
    BackHandler(enabled = showSearch, onBack = ::closeSearch)
    BackHandler(enabled = isDrawerOpen, onBack = ::closeDrawer)

    val tabState = if (groups.size > 1) rememberLazyListState(selectedGroupIndex) else null
    val tabTargets = if (tabState != null) rememberDpadFocusTargets(groupIds) { FocusRequester() } else emptyMap()
    val tabOrder = if (tabState != null) groupIds.map(tabTargets::getValue) else emptyList()
    val requestTabFocus = if (tabState != null) {
        rememberLazyDpadFocus(tabOrder.map { listOf(it) }) { tabState.scrollToItem(it) }
    } else null
    fun focusGroupTab(groupId: String): Boolean = tabTargets[groupId]?.let { requestTabFocus?.invoke(it) } ?: false

    var drawerWasOpen by remember { mutableStateOf(isDrawerOpen) }
    LaunchedEffect(isDrawerOpen, showSearch) {
        val justClosed = drawerWasOpen && !isDrawerOpen
        drawerWasOpen = isDrawerOpen
        if (justClosed) {
            focusToRestore = drawerOrigin ?: TvMainFocusTarget.Toolbar
            drawerOrigin = null
        } else if (!isDrawerOpen && !showSearch && editorOrigin == null && focusToRestore == null) {
            focusToRestore = TvMainFocusTarget.Toolbar
        }
    }
    val windowInfo = LocalWindowInfo.current
    val latestTabFocus by rememberUpdatedState(::focusGroupTab)
    LaunchedEffect(isDrawerOpen, focusToRestore) {
        val target = focusToRestore ?: return@LaunchedEffect
        if (isDrawerOpen || target is TvMainFocusTarget.Profile || target is TvMainFocusTarget.AfterRemoval) return@LaunchedEffect
        snapshotFlow { windowInfo.isWindowFocused }.first { it }
        withFrameNanos { }
        if (target !is TvMainFocusTarget.GroupTab || !latestTabFocus(target.id)) requestFocusWhenReady(topBarFocus.start)
        focusToRestore = null
    }

    TvMainDialogs(
        shareQRCodeBitmap = uiState.shareQRCodeBitmap,
        selectedGuid = uiState.selectedGuid,
        onAction = onAction,
        onEdit = ::openEditor,
        onRestoreFocus = { focusToRestore = it }
    ) { dialogs ->
        TvMainNavigationDrawer(
            drawerState = drawerState,
            focusGeneration = resumeFocusGeneration,
            onClose = ::closeDrawer,
            onNavigate = onNavigate
        ) {
            Scaffold(
                contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
                topBar = {
                    TvMainTopBar(
                        isLoading = isLoading,
                        isRunning = uiState.isRunning,
                        showSearch = showSearch,
                        searchQuery = uiState.searchQuery,
                        focusRequesters = topBarFocus,
                        onSearchQueryChange = { onAction(MainAction.Search(it)) },
                        onSearchClose = ::closeSearch,
                        onSearchOpen = { showSearch = true },
                        onOpenDrawer = { openDrawer(TvMainFocusTarget.Toolbar) },
                        onMoveDown = { selectedGroup?.let { focusGroupTab(it.id) } ?: false },
                        onAction = onAction,
                        onBulkDelete = dialogs.bulkDelete
                    )
                },
                bottomBar = {
                    val connection = stringResource(if (uiState.isRunning) R.string.connection_connected_tv else R.string.connection_not_connected)
                    val details = when (uiState.status) {
                        MainStatus.Connected, MainStatus.Disconnected -> ""
                        else -> mainViewModel.formatStatus(uiState.status).replace('\n', ' ')
                    }
                    MainTvStatusBar(connection, details)
                }
            ) { innerPadding ->
                if (selectedGroup != null) {
                    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        if (tabState != null && requestTabFocus != null) {
                            TvGroupTabBar(
                                groups, mainViewModel, selectedGroupIndex, tabState, tabOrder, requestTabFocus,
                                onOpenDrawer = { id -> openDrawer(TvMainFocusTarget.GroupTab(id)) },
                                onMoveUp = { topBarFocus.start.requestFocus() },
                                onSelectGroup = { onAction(MainAction.SelectGroup(it)) }
                            )
                        }
                        groupScrollStates.SaveableStateProvider(selectedGroup.id) {
                            TvProfileGroup(
                                group = selectedGroup,
                                mainViewModel = mainViewModel,
                                uiState = uiState,
                                scroll = rememberTvProfileScrollState(),
                                onAction = onAction,
                                focusToRestore = focusToRestore.takeUnless { isDrawerOpen },
                                onFocusRestored = { target, restored ->
                                    if (editorOrigin == target) editorOrigin = null
                                    if (focusToRestore == target) {
                                        focusToRestore = if (restored) null else TvMainFocusTarget.Toolbar
                                    }
                                },
                                revealActiveProfile = editorOrigin == null && drawerOrigin !is TvMainFocusTarget.Profile,
                                dialogs = dialogs,
                                revealGeneration = profileRevealGeneration,
                                lastReveals = lastReveals,
                                onOpenDrawer = ::openDrawer,
                                onBackToToolbar = { topBarFocus.start.requestFocus() },
                                onUpFromFirstRow = if (tabState != null) { { focusGroupTab(selectedGroup.id) } } else null
                            )
                        }
                    }
                }
            }
        }
    }
}
