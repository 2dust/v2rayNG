package com.v2ray.ang.ui.apppicker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.ui.compose.AppListItem
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DpadHorizontalDirection
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.TvIconButton
import com.v2ray.ang.ui.compose.TvTopBarSearchField
import com.v2ray.ang.ui.compose.adjacentDpadFocusTarget
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.dpadPopupHorizontalNavigation
import com.v2ray.ang.ui.compose.dpadTopBarFocusNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.rememberDpadFocusTargets
import com.v2ray.ang.ui.compose.rememberLazyDpadFocus
import com.v2ray.ang.ui.compose.requestFocusWhenReady
import com.v2ray.ang.ui.compose.verticalScrollbar

class TvAppPickerActivity : BaseAppPickerActivity() {
    @Composable
    override fun PickerContent(title: String, apps: List<AppInfo>, isLoading: Boolean, selectedPackages: Set<String>, actions: AppPickerActions) {
        TvAppPickerScreen(title, apps, isLoading, selectedPackages, actions)
    }
}

@Composable
private fun TvAppPickerScreen(title: String, apps: List<AppInfo>, isLoading: Boolean, selectedPackages: Set<String>, actions: AppPickerActions) {
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var focusedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var editOnSearchOpen by remember { mutableStateOf(false) }
    val back = remember { FocusRequester() }
    val search = remember { FocusRequester() }
    val searchInput = remember { FocusRequester() }
    val clear = remember { FocusRequester() }
    val more = remember { FocusRequester() }
    val focusOrder = if (!showSearch) listOf(back, search, more)
    else if (query.isEmpty()) listOf(back, searchInput, more)
    else listOf(back, searchInput, clear, more)

    val listState = rememberLazyListState()
    val packages = remember(apps) { apps.map { it.packageName } }
    val rows = rememberDpadFocusTargets(packages) { FocusRequester() }
    val rowTargets = remember(rows) { rows.values.map { listOf(it) } }
    val focusRow = rememberLazyDpadFocus(rowTargets) { listState.scrollToItem(it) }
    val focusFirstApp = { packages.firstOrNull()?.let(rows::get)?.let(focusRow) == true }
    val listEntry = if (showSearch) searchInput else back
    val initialPackage = remember { focusedPackage }
    var needsInitialFocus by remember { mutableStateOf(true) }

    fun updateQuery(value: String) {
        query = value
        actions.onSearch(value)
    }
    fun closeSearch() {
        updateQuery("")
        showSearch = false
        back.requestFocus()
    }
    BackHandler(enabled = showSearch, onBack = ::closeSearch)
    LaunchedEffect(Unit) { actions.onSearch(query) }
    LaunchedEffect(isLoading, rows, showSearch) {
        if (needsInitialFocus && (initialPackage == null || !isLoading || initialPackage in rows)) {
            needsInitialFocus = false
            if (initialPackage?.let(rows::get)?.let(focusRow) != true) requestFocusWhenReady(listEntry)
        }
    }
    Scaffold(
        topBar = {
            Box(Modifier.padding(horizontal = 48.dp).onFocusChanged { if (it.hasFocus) focusedPackage = null }.focusGroup()) {
                AppTopBar(
                    title = title,
                    onBackClick = actions.onBack,
                    isLoading = isLoading,
                    titleContent = if (showSearch) ({
                        TvTopBarSearchField(query, ::updateQuery, searchInput, clear, focusOrder, focusFirstApp,
                            editOnEntry = editOnSearchOpen)
                    }) else null,
                    navigationIcon = {
                        TvIconButton(
                            painterResource(R.drawable.ic_tv_arrow_back_24dp), stringResource(R.string.action_back),
                            if (showSearch) ::closeSearch else actions.onBack,
                            modifier = Modifier.dpadTopBarFocusNavigation(back, focusOrder, focusFirstApp),
                            focusRequester = back, contentDescription = stringResource(R.string.acc_back)
                        )
                    },
                    actions = {
                        if (!showSearch) {
                            TvIconButton(
                                painterResource(R.drawable.ic_search_24dp), stringResource(R.string.menu_item_search),
                                onClick = { editOnSearchOpen = true; showSearch = true },
                                modifier = Modifier.dpadTopBarFocusNavigation(search, focusOrder, focusFirstApp),
                                focusRequester = search, contentDescription = stringResource(R.string.acc_search)
                            )
                        }
                        TvAppPickerMenu(more, focusOrder, focusFirstApp, actions)
                    }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)
                .imePadding().padding(horizontal = 48.dp).verticalScrollbar(listState)
        ) {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                val packageName = app.packageName
                AppListItem(
                    appName = app.appName, packageName = packageName, icon = null,
                    checked = packageName in selectedPackages, onCheckedChange = { actions.onToggleApp(packageName) },
                    modifier = Modifier.padding(vertical = 3.dp)
                        .onFocusChanged { if (it.isFocused) focusedPackage = packageName }
                        .focusProperties { start = back; end = FocusRequester.Cancel }
                        .dpadVerticalFocusNavigation(
                            onMoveUp = { packages.getOrNull(index - 1)?.let(rows::get)?.let(focusRow) ?: listEntry.requestFocus() },
                            onMoveDown = { packages.getOrNull(index + 1)?.let(rows::get)?.let(focusRow) ?: true }
                        )
                        .dpadFocusOutline(rows.getValue(packageName)),
                    singleToggle = true
                )
                ItemDivider()
            }
        }
    }
}

@Composable
private fun TvAppPickerMenu(
    more: FocusRequester, order: List<FocusRequester>, onMoveDown: () -> Boolean, actions: AppPickerActions
) {
    var expanded by remember { mutableStateOf(false) }
    var returnTarget by remember { mutableStateOf<FocusRequester?>(null) }
    val firstAction = remember { FocusRequester() }
    fun close(target: FocusRequester = more) {
        returnTarget = target
        expanded = false
    }
    LaunchedEffect(expanded, returnTarget) {
        if (!expanded) returnTarget?.let { requestFocusWhenReady(it); returnTarget = null }
    }
    Box {
        TvIconButton(
            painterResource(R.drawable.ic_more_vert_24dp), stringResource(R.string.action_more), { expanded = true },
            modifier = Modifier.dpadTopBarFocusNavigation(more, order, onMoveDown), focusRequester = more,
            contentDescription = stringResource(R.string.acc_more)
        )
        DropdownMenu(
            expanded = expanded, onDismissRequest = { close() }, containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.dpadPopupHorizontalNavigation(
                onMovePrevious = { close(adjacentDpadFocusTarget(more, order, DpadHorizontalDirection.Previous) ?: more) },
                onMoveNext = { close() }
            )
        ) {
            AppPickerMenuAction.entries.forEach { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(action.labelRes)) },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        .dpadFocusOutline(focusRequester = firstAction.takeIf { action == AppPickerMenuAction.SelectAll }),
                    onClick = {
                        close()
                        when (action) {
                            AppPickerMenuAction.SelectAll -> actions.onSelectAll()
                            AppPickerMenuAction.InvertSelection -> actions.onInvertSelection()
                        }
                    }
                )
            }
            LaunchedEffect(Unit) { requestFocusWhenReady(firstAction) }
        }
    }
}
