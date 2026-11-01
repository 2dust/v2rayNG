package com.v2ray.ang.ui.perappproxy

import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.v2ray.ang.R
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppDropdownMenuItems
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.RxGlassPanel
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.AppIconFetcher
import com.v2ray.ang.util.Utils

private enum class PerAppMenuAction(@StringRes val labelRes: Int) {
    SelectAll(R.string.menu_item_select_all),
    InvertSelection(R.string.menu_item_invert_selection),
    SelectProxyApps(R.string.menu_item_select_proxy_app),
    ImportSelection(R.string.menu_item_import_proxy_app),
    ExportSelection(R.string.menu_item_export_proxy_app)
}

class PerAppProxyActivity : BaseComponentActivity() {
    private val viewModel: PerAppProxyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.loadApps(this)
    }

    @Composable
    override fun ScreenContent() {
        val apps by viewModel.displayedApps.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val selectedPackages by viewModel.blacklist.collectAsStateWithLifecycle()
        val routingMode by viewModel.routingMode.collectAsStateWithLifecycle()
        PerAppProxyScreen(
            apps = apps,
            isLoading = isLoading,
            selectedPackages = selectedPackages,
            routingMode = routingMode,
            onBackClick = { finish() },
            onModeChange = {
                if (!viewModel.setRoutingMode(it)) toastError(R.string.rx_routing_save_failed)
            },
            onToggleApp = { viewModel.toggle(it) },
            onSearch = { viewModel.filterApps(it) },
            onSelectAll = { viewModel.selectAll() },
            onInvertSelection = { viewModel.invertSelection() },
            onSelectProxyAuto = { viewModel.selectProxyAppAuto(this) },
            onImportProxyApp = {
                viewModel.importProxyApp(Utils.getClipboard(applicationContext), this)
            },
            onExportProxyApp = {
                Utils.setClipboard(applicationContext, viewModel.exportProxyApp())
                toastSuccess(R.string.toast_success)
            },
        )
    }
}

@Composable
fun PerAppProxyScreen(
    apps: List<AppInfo>,
    isLoading: Boolean,
    selectedPackages: Set<String>,
    routingMode: PerAppRoutingMode,
    onBackClick: () -> Unit,
    onModeChange: (PerAppRoutingMode) -> Unit,
    onToggleApp: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onSelectProxyAuto: () -> Unit,
    onImportProxyApp: () -> Unit,
    onExportProxyApp: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(query) { onSearch(query) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.rx_app_routing),
                onBackClick = onBackClick,
                isLoading = isLoading,
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                painterResource(R.drawable.ic_more_vert_24dp),
                                contentDescription = stringResource(R.string.acc_more),
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            AppDropdownMenuItems(PerAppMenuAction.entries, { it.labelRes }) { action ->
                                showMenu = false
                                when (action) {
                                    PerAppMenuAction.SelectAll -> onSelectAll()
                                    PerAppMenuAction.InvertSelection -> onInvertSelection()
                                    PerAppMenuAction.SelectProxyApps -> onSelectProxyAuto()
                                    PerAppMenuAction.ImportSelection -> onImportProxyApp()
                                    PerAppMenuAction.ExportSelection -> onExportProxyApp()
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScrollbar(listState),
            contentPadding = NavigationBarsBottomPadding(),
        ) {
            item(key = "routing-modes") {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp).selectableGroup()
                ) {
                    PerAppRoutingMode.entries.forEach { mode ->
                        RoutingModeRow(
                            mode = mode,
                            selected = routingMode == mode,
                            onClick = { onModeChange(mode) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        )
                    }
                    if (routingMode == PerAppRoutingMode.ONLY_SELECTED && selectedPackages.isEmpty()) {
                        Text(
                            text = stringResource(R.string.rx_select_apps_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.rx_routing_change_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (routingMode != PerAppRoutingMode.ALL) {
                item(key = "app-search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.rx_search_apps)) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_search_24dp),
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(items = apps, key = { it.packageName }) { app ->
                    RoutingAppRow(
                        app = app,
                        checked = app.packageName in selectedPackages,
                        onClick = { onToggleApp(app.packageName) },
                    )
                }
                if (!isLoading && apps.isEmpty()) {
                    item(key = "empty-apps") {
                        Text(
                            text = stringResource(R.string.rx_no_apps),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutingModeRow(
    mode: PerAppRoutingMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(mode.titleRes)
    val hint = stringResource(mode.hintRes)
    RxGlassPanel(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = title; stateDescription = hint }
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clearAndSetSemantics {}) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun RoutingAppRow(app: AppInfo, checked: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val model = remember(app.packageName) {
        ImageRequest.Builder(context)
            .data("appicon:${app.packageName}")
            .fetcherFactory(AppIconFetcher.Factory(context))
            .build()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = checked, role = Role.Checkbox, onClick = onClick)
            .semantics { contentDescription = app.appName }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            contentScale = ContentScale.Fit,
            error = painterResource(R.drawable.ic_image_24dp),
            fallback = painterResource(R.drawable.ic_image_24dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = app.appName,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).clearAndSetSemantics {},
        )
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

private val PerAppRoutingMode.titleRes: Int
    @StringRes get() = when (this) {
        PerAppRoutingMode.ALL -> R.string.rx_routing_all
        PerAppRoutingMode.ONLY_SELECTED -> R.string.rx_routing_only
        PerAppRoutingMode.EXCEPT_SELECTED -> R.string.rx_routing_except
    }

private val PerAppRoutingMode.hintRes: Int
    @StringRes get() = when (this) {
        PerAppRoutingMode.ALL -> R.string.rx_routing_all_hint
        PerAppRoutingMode.ONLY_SELECTED -> R.string.rx_routing_only_hint
        PerAppRoutingMode.EXCEPT_SELECTED -> R.string.rx_routing_except_hint
    }
