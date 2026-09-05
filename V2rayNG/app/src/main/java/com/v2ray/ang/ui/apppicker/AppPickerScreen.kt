package com.v2ray.ang.ui.apppicker

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseScreen
import com.v2ray.ang.ui.compose.AppDropdownMenuItems
import com.v2ray.ang.ui.compose.AppListItem
import com.v2ray.ang.ui.compose.AppSearchState
import com.v2ray.ang.ui.compose.AppTheme
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.verticalScrollbar

private val EmptyStatePad = 24.dp
private const val RowContentType = "app_row"

@Composable
fun AppPickerScreen(viewModel: AppPickerViewModel) {
    val stateHolder = viewModel.uiState.collectAsStateWithLifecycle()
    val loadingHolder = viewModel.isLoading.collectAsStateWithLifecycle()

    val onAction = remember(viewModel) { viewModel::onAction }
    val onBack = remember(onAction) { { onAction(AppPickerAction.Back) } }

    // Narrow slices: every consumer reads exactly one field, through derivedStateOf on its own
    // side, so ticking a row cannot invalidate the top bar or the list container.
    val titleResProvider = remember(stateHolder) { { stateHolder.value.titleRes } }
    val loadingProvider = remember(loadingHolder) { { loadingHolder.value } }
    val searchActiveProvider = remember(stateHolder) { { stateHolder.value.searchActive } }
    val queryProvider = remember(stateHolder) { { stateHolder.value.query } }
    val appsProvider = remember(stateHolder) { { stateHolder.value.apps } }
    val selectedProvider = remember(stateHolder) { { stateHolder.value.selected } }
    val emptyResultProvider = remember(stateHolder) { { stateHolder.value.isEmptyResult } }

    BackHandler(onBack = onBack)

    BaseScreen(
        viewModel = viewModel,
        topBar = {
            AppPickerTopBar(
                titleResProvider = titleResProvider,
                isLoadingProvider = loadingProvider,
                searchActiveProvider = searchActiveProvider,
                queryProvider = queryProvider,
                onAction = onAction
            )
        }
    ) { _, action ->
        AppPickerList(
            appsProvider = appsProvider,
            selectedProvider = selectedProvider,
            emptyResultProvider = emptyResultProvider,
            onAction = action
        )
    }
}

@Composable
private fun AppPickerTopBar(
    titleResProvider: () -> Int,
    isLoadingProvider: () -> Boolean,
    searchActiveProvider: () -> Boolean,
    queryProvider: () -> String,
    onAction: (AppPickerAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val titleRes by remember(titleResProvider) { derivedStateOf(titleResProvider) }
    val isLoading by remember(isLoadingProvider) { derivedStateOf(isLoadingProvider) }
    val searchActive by remember(searchActiveProvider) { derivedStateOf(searchActiveProvider) }
    val query by remember(queryProvider) { derivedStateOf(queryProvider) }

    AppTopBar(
        title = stringResource(titleRes),
        onBackClick = { onAction(AppPickerAction.Back) },
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
        onSearchQueryChange = { onAction(AppPickerAction.QueryChanged(it)) },
        onSearchClose = { onAction(AppPickerAction.SearchClose) },
        actions = {
            if (!searchActive) {
                IconButton(onClick = { onAction(AppPickerAction.SearchOpen) }) {
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
                        items = AppPickerMenu.entries,
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
private fun AppPickerList(
    appsProvider: () -> List<AppRow>,
    selectedProvider: () -> Set<String>,
    emptyResultProvider: () -> Boolean,
    onAction: (AppPickerAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val onToggle = remember(onAction) {
        { packageName: String -> onAction(AppPickerAction.ToggleApp(packageName)) }
    }

    val apps by remember(appsProvider) { derivedStateOf(appsProvider) }
    val isEmptyResult by remember(emptyResultProvider) { derivedStateOf(emptyResultProvider) }

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
            AppPickerRow(
                appRow = appRow,
                selectedProvider = selectedProvider,
                onToggle = onToggle
            )
            ItemDivider()
        }
    }
}

@Composable
private fun AppPickerRow(
    appRow: AppRow,
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
        appName = if (appRow.isUnidentified) {
            stringResource(R.string.app_picker_unknown_app)
        } else {
            appRow.appName
        },
        packageName = packageName,
        icon = null,
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier
    )
}

// ===== Preview =====

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppPickerListPreview() {
    val rows = listOf(
        AppRow(packageName = "com.v2ray.ang", appName = "v2rayNG"),
        AppRow(
            packageName = "com.example.a.very.long.package.name.that.gets.truncated",
            appName = "An application with a very long label that must be truncated"
        ),
        AppRow(
            packageName = AppConfig.UNIDENTIFIED_PACKAGE,
            appName = "",
            isUnidentified = true
        )
    )
    val selected = setOf("com.v2ray.ang")
    AppTheme {
        AppPickerList(
            appsProvider = { rows },
            selectedProvider = { selected },
            emptyResultProvider = { false },
            onAction = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AppPickerListEmptyPreview() {
    AppTheme {
        AppPickerList(
            appsProvider = { emptyList() },
            selectedProvider = { emptySet() },
            emptyResultProvider = { true },
            onAction = {}
        )
    }
}
