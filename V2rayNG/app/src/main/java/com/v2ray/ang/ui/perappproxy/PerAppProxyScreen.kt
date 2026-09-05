package com.v2ray.ang.ui.perappproxy

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseScreen
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.AppDropdownMenuItems
import com.v2ray.ang.ui.compose.AppListItem
import com.v2ray.ang.ui.compose.AppSearchState
import com.v2ray.ang.ui.compose.AppTheme
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.verticalScrollbar

private val HeaderHorizontalPad = 16.dp
private val HeaderVerticalPad = 8.dp
private val HeaderSwitchGap = 16.dp
private val SwitchLabelGap = 8.dp
private val SwitchRowVerticalPad = 4.dp
private val EmptyStatePad = 24.dp

/** Accessibility minimum */
private val MinTouchTarget = 48.dp

/** Two switches share one row here */
private const val HeaderSwitchScale = 0.65f

private const val RowContentType = "per_app_proxy_row"
private const val HeaderSwitchMaxLines = 2

@Composable
fun PerAppProxyScreen(viewModel: PerAppProxyViewModel) {
    val stateHolder = viewModel.uiState.collectAsStateWithLifecycle()
    val loadingHolder = viewModel.isLoading.collectAsStateWithLifecycle()

    val onAction = remember(viewModel) { viewModel::onAction }
    val onBack = remember(onAction) { { onAction(PerAppProxyAction.Back) } }
    val dialogs = remember { PerAppProxyDialogHost() }

    // every consumer reads exactly one field, through derivedStateOf on its own side.
    val loadingProvider = remember(loadingHolder) { { loadingHolder.value } }
    val searchActiveProvider = remember(stateHolder) { { stateHolder.value.searchActive } }
    val queryProvider = remember(stateHolder) { { stateHolder.value.query } }
    val enabledProvider = remember(stateHolder) { { stateHolder.value.perAppProxyEnabled } }
    val bypassProvider = remember(stateHolder) { { stateHolder.value.bypassMode } }
    val appsProvider = remember(stateHolder) { { stateHolder.value.apps } }
    val selectedProvider = remember(stateHolder) { { stateHolder.value.selected } }
    val emptyResultProvider = remember(stateHolder) { { stateHolder.value.isEmptyResult } }

    BackHandler(onBack = onBack)

    BaseScreen(
        viewModel = viewModel,
        topBar = {
            PerAppProxyTopBar(
                isLoadingProvider = loadingProvider,
                searchActiveProvider = searchActiveProvider,
                queryProvider = queryProvider,
                onAction = onAction
            )
        }
    ) { _, action ->
        Column(modifier = Modifier.fillMaxSize()) {
            PerAppProxyHeader(
                enabledProvider = enabledProvider,
                bypassProvider = bypassProvider,
                onAction = action,
                onInfoClick = dialogs.showInfo
            )
            AppDivider()
            PerAppProxyList(
                appsProvider = appsProvider,
                selectedProvider = selectedProvider,
                emptyResultProvider = emptyResultProvider,
                onAction = action
            )
        }
        PerAppProxyDialogs(dialog = dialogs.current, onDismiss = dialogs.dismiss)
    }
}

@Composable
private fun PerAppProxyTopBar(
    isLoadingProvider: () -> Boolean,
    searchActiveProvider: () -> Boolean,
    queryProvider: () -> String,
    onAction: (PerAppProxyAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val isLoading by remember(isLoadingProvider) { derivedStateOf(isLoadingProvider) }
    val searchActive by remember(searchActiveProvider) { derivedStateOf(searchActiveProvider) }
    val query by remember(queryProvider) { derivedStateOf(queryProvider) }

    AppTopBar(
        title = stringResource(R.string.per_app_proxy_settings),
        onBackClick = { onAction(PerAppProxyAction.Back) },
        modifier = modifier,
        isLoading = isLoading,
        searchState = if (searchActive) {
            AppSearchState(
                isActive = true,
                query = query,
                placeholder = stringResource(R.string.menu_item_search)
            )
        } else {
            null
        },
        onSearchQueryChange = { onAction(PerAppProxyAction.QueryChanged(it)) },
        onSearchClose = { onAction(PerAppProxyAction.SearchClose) },
        actions = {
            if (!searchActive) {
                IconButton(onClick = { onAction(PerAppProxyAction.SearchOpen) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search_24dp),
                        contentDescription = stringResource(R.string.acc_search)
                    )
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert_24dp),
                        contentDescription = stringResource(R.string.acc_more)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    AppDropdownMenuItems(
                        items = PerAppProxyMenu.entries,
                        labelRes = { it.labelRes },
                        onSelected = { menu ->
                            showMenu = false
                            onAction(menu.action)
                        }
                    )
                }
            }
        }
    )
}

@Composable
private fun PerAppProxyHeader(
    enabledProvider: () -> Boolean,
    bypassProvider: () -> Boolean,
    onAction: (PerAppProxyAction) -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled by remember(enabledProvider) { derivedStateOf(enabledProvider) }
    val bypassMode by remember(bypassProvider) { derivedStateOf(bypassProvider) }

    // Kept out of the composition body.
    val onEnabledChange = remember(onAction) {
        { value: Boolean -> onAction(PerAppProxyAction.PerAppProxyChanged(value)) }
    }
    val onBypassChange = remember(onAction) {
        { value: Boolean -> onAction(PerAppProxyAction.BypassModeChanged(value)) }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HeaderHorizontalPad, vertical = HeaderVerticalPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            HeaderSwitch(
                label = stringResource(R.string.per_app_proxy_settings_enable),
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(HeaderSwitchGap))
            HeaderSwitch(
                label = stringResource(R.string.switch_bypass_apps_mode),
                checked = bypassMode,
                onCheckedChange = onBypassChange,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onInfoClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_about_24dp),
                    contentDescription = stringResource(R.string.acc_per_app_proxy_information),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HeaderSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .heightIn(min = MinTouchTarget)
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(vertical = SwitchRowVerticalPad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = HeaderSwitchMaxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(SwitchLabelGap))
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.scale(HeaderSwitchScale),
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                checkedTrackColor = MaterialTheme.colorScheme.secondary
            )
        )
    }
}

@Composable
private fun PerAppProxyList(
    appsProvider: () -> List<ProxyAppRow>,
    selectedProvider: () -> Set<String>,
    emptyResultProvider: () -> Boolean,
    onAction: (PerAppProxyAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val onToggle = remember(onAction) {
        { packageName: String -> onAction(PerAppProxyAction.ToggleApp(packageName)) }
    }
    val isEmptyResult by remember(emptyResultProvider) { derivedStateOf(emptyResultProvider) }
    val apps by remember(appsProvider) { derivedStateOf(appsProvider) }

    if (isEmptyResult) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(EmptyStatePad),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.toast_none_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .verticalScrollbar(listState),
        contentPadding = NavigationBarsBottomPadding()
    ) {
        items(
            items = apps,
            key = { it.packageName },
            contentType = { RowContentType }
        ) { appRow ->
            PerAppProxyRow(
                appRow = appRow,
                selectedProvider = selectedProvider,
                onToggle = onToggle
            )
            ItemDivider()
        }
    }
}

@Composable
private fun PerAppProxyRow(
    appRow: ProxyAppRow,
    selectedProvider: () -> Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val packageName = appRow.packageName
    val checked by remember(packageName, selectedProvider) {
        derivedStateOf { packageName in selectedProvider() }
    }
    val onCheckedChange = remember(packageName, onToggle) {
        { _: Boolean -> onToggle(packageName) }
    }

    AppListItem(
        appName = appRow.appName,
        packageName = packageName,
        icon = null,
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier
    )
}

// ===== previews =====

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PerAppProxyContentPreview() {
    val rows = listOf(
        ProxyAppRow(packageName = "com.v2ray.ang", appName = "v2rayNG"),
        ProxyAppRow(
            packageName = "com.example.a.very.long.package.name.that.gets.truncated",
            appName = "An application with a very long label that must be truncated"
        )
    )
    val selected = setOf("com.v2ray.ang")
    AppTheme {
        Column {
            PerAppProxyHeader(
                enabledProvider = { true },
                bypassProvider = { false },
                onAction = {},
                onInfoClick = {}
            )
            AppDivider()
            PerAppProxyList(
                appsProvider = { rows },
                selectedProvider = { selected },
                emptyResultProvider = { false },
                onAction = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PerAppProxyEmptyPreview() = AppTheme {
    PerAppProxyList(
        appsProvider = { emptyList() },
        selectedProvider = { emptySet() },
        emptyResultProvider = { true },
        onAction = {}
    )
}
