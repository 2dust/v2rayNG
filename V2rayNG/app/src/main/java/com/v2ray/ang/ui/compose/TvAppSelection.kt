package com.v2ray.ang.ui.compose

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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

internal enum class TvAppToolbarControl {
    Back, Search, Input, Clear, More;

    companion object {
        fun visible(searchOpen: Boolean, hasQuery: Boolean): List<TvAppToolbarControl> = when {
            !searchOpen -> listOf(Back, Search, More)
            !hasQuery -> listOf(Back, Input, More)
            else -> listOf(Back, Input, Clear, More)
        }

        fun restore(saved: TvAppToolbarControl?, searchOpen: Boolean, hasQuery: Boolean): TvAppToolbarControl =
            saved?.takeIf { it in visible(searchOpen, hasQuery) } ?: if (searchOpen) Input else Back
    }
}

internal class TvAppSearchState(
    private val expanded: MutableState<Boolean>,
    private val text: MutableState<String>,
    private val filter: State<(String) -> Unit>,
    private val focusedControl: MutableState<TvAppToolbarControl?>
) {
    private val targets = TvAppToolbarControl.entries.associateWith { FocusRequester() }
    val back get() = requester(TvAppToolbarControl.Back)
    val input get() = requester(TvAppToolbarControl.Input)
    val isOpen get() = expanded.value
    val query get() = text.value
    val toolbarEntry get() = if (isOpen) input else back
    val toolbarOrder get() = TvAppToolbarControl.visible(isOpen, query.isNotEmpty()).map(::requester)
    val toolbarRestoreTarget get() = requester(TvAppToolbarControl.restore(focusedControl.value, isOpen, query.isNotEmpty()))

    fun requester(control: TvAppToolbarControl): FocusRequester = targets.getValue(control)

    fun onToolbarFocused(requester: FocusRequester) {
        focusedControl.value = targets.entries.firstOrNull { it.value === requester }?.key
    }

    fun open() {
        expanded.value = true
    }

    fun close() {
        updateQuery("")
        expanded.value = false
        back.requestFocus()
    }

    fun updateQuery(value: String) {
        text.value = value
        filter.value(value)
    }
}

@Composable
internal fun rememberTvAppSearch(onSearch: (String) -> Unit): TvAppSearchState {
    val expanded = rememberSaveable { mutableStateOf(false) }
    val text = rememberSaveable { mutableStateOf("") }
    val filter = rememberUpdatedState(onSearch)
    val focusedControl = rememberSaveable { mutableStateOf<TvAppToolbarControl?>(null) }
    val state = remember { TvAppSearchState(expanded, text, filter, focusedControl) }
    LaunchedEffect(state) { filter.value(state.query) }
    BackHandler(enabled = state.isOpen, onBack = state::close)
    return state
}

internal class TvAppMenuAction(@StringRes val label: Int, val onClick: () -> Unit)

@Composable
internal fun TvAppSelectionToolbar(
    title: String, isLoading: Boolean, search: TvAppSearchState, menu: List<TvAppMenuAction>,
    onBack: () -> Unit, onMoveDown: () -> Boolean, modifier: Modifier = Modifier
) {
    val searchButton = search.requester(TvAppToolbarControl.Search)
    val clear = search.requester(TvAppToolbarControl.Clear)
    val more = search.requester(TvAppToolbarControl.More)
    val order = search.toolbarOrder

    fun trackFocus(target: FocusRequester) = Modifier.onFocusChanged {
        if (it.isFocused) search.onToolbarFocused(target)
    }

    Box(modifier.padding(horizontal = 48.dp).focusGroup()) {
        AppTopBar(
            title = title, onBackClick = onBack, isLoading = isLoading,
            titleContent = if (search.isOpen) ({
                TvTopBarSearchField(search.query, search::updateQuery, search.input, clear, order, onMoveDown,
                    focusTarget = { trackFocus(it) })
            }) else null,
            navigationIcon = {
                TvIconButton(
                    painterResource(R.drawable.ic_tv_arrow_back_24dp), stringResource(R.string.action_back),
                    if (search.isOpen) search::close else onBack,
                    modifier = trackFocus(search.back).dpadTopBarFocusNavigation(search.back, order, onMoveDown),
                    focusRequester = search.back, contentDescription = stringResource(R.string.acc_back)
                )
            },
            actions = {
                if (!search.isOpen) {
                    TvIconButton(
                        painterResource(R.drawable.ic_search_24dp), stringResource(R.string.menu_item_search), search::open,
                        modifier = trackFocus(searchButton).dpadTopBarFocusNavigation(searchButton, order, onMoveDown),
                        focusRequester = searchButton, contentDescription = stringResource(R.string.acc_search)
                    )
                }
                TvAppSelectionMenu(more, order, onMoveDown, menu, trackFocus(more))
            }
        )
    }
}

@Composable
private fun TvAppSelectionMenu(
    more: FocusRequester, order: List<FocusRequester>, onMoveDown: () -> Boolean, actions: List<TvAppMenuAction>,
    buttonModifier: Modifier
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
            modifier = buttonModifier.dpadTopBarFocusNavigation(more, order, onMoveDown), focusRequester = more,
            contentDescription = stringResource(R.string.acc_more)
        )
        DropdownMenu(
            expanded = expanded, onDismissRequest = { close() }, containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.dpadPopupHorizontalNavigation(
                onMovePrevious = { close(adjacentDpadFocusTarget(more, order, DpadHorizontalDirection.Previous) ?: more) },
                onMoveNext = { close() }
            )
        ) {
            actions.forEachIndexed { index, action ->
                DropdownMenuItem(
                    text = { Text(stringResource(action.label)) },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        .dpadFocusOutline(focusRequester = firstAction.takeIf { index == 0 }),
                    onClick = { close(); action.onClick() }
                )
            }
            LaunchedEffect(Unit) { requestFocusWhenReady(firstAction) }
        }
    }
}

internal class TvAppListFocus(
    val scroll: LazyListState,
    private val packages: List<String>,
    val targets: Map<String, FocusRequester>,
    private val request: (FocusRequester) -> Boolean
) {
    fun focus(packageName: String): Boolean = targets[packageName]?.let(request) == true
    fun focusAt(index: Int): Boolean = packages.getOrNull(index)?.let(::focus) == true
}

// Capture the last user target before the recreated window assigns its own initial focus.
@Composable
internal fun TvAppListFocus.RestoreFocus(
    packageName: String?, fallback: FocusRequester, loading: Boolean, dialogOpen: Boolean = false
) {
    val initialPackage = remember { packageName }
    val initialFallback = remember { fallback }
    // An open dialog owns focus and will return to its own opener.
    var pending by remember { mutableStateOf(!dialogOpen) }
    LaunchedEffect(this, loading, dialogOpen) {
        if (dialogOpen) pending = false
        if (pending && (initialPackage == null || !loading || initialPackage in targets)) {
            if (initialPackage?.let(::focus) != true) requestFocusWhenReady(initialFallback)
            pending = false
        }
    }
}

@Composable
internal fun rememberTvAppListFocus(apps: List<AppInfo>): TvAppListFocus {
    val scroll = rememberLazyListState()
    val packages = remember(apps) { apps.map { it.packageName } }
    val targets = rememberDpadFocusTargets(packages) { FocusRequester() }
    val rows = remember(packages) { packages.map { listOf(targets.getValue(it)) } }
    val request = rememberLazyDpadFocus(rows) { scroll.scrollToItem(it) }
    return remember(scroll, packages, targets, request) { TvAppListFocus(scroll, packages, targets, request) }
}

@Composable
internal fun TvAppSelectionList(
    apps: List<AppInfo>, selectedPackages: Set<String>, focus: TvAppListFocus, back: FocusRequester,
    onToggleApp: (String) -> Unit, onAppFocused: (String) -> Unit, onMoveAboveList: () -> Boolean,
    modifier: Modifier = Modifier
) {
    LazyColumn(state = focus.scroll, modifier = modifier.verticalScrollbar(focus.scroll)) {
        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
            val packageName = app.packageName
            AppListItem(
                appName = app.appName, packageName = packageName, icon = null,
                checked = packageName in selectedPackages, onCheckedChange = { onToggleApp(packageName) },
                modifier = Modifier.padding(vertical = 3.dp)
                    .onFocusChanged { if (it.isFocused) onAppFocused(packageName) }
                    .focusProperties { start = back; end = FocusRequester.Cancel }
                    .dpadVerticalFocusNavigation(
                        onMoveUp = { if (index == 0) onMoveAboveList() else focus.focusAt(index - 1) },
                        onMoveDown = { index == apps.lastIndex || focus.focusAt(index + 1) }
                    )
                    .dpadFocusOutline(focus.targets.getValue(packageName)),
                singleToggle = true
            )
            ItemDivider()
        }
    }
}
