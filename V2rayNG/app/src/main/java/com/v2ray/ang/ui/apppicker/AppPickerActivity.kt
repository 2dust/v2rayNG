package com.v2ray.ang.ui.apppicker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.base.televisionResultDestination
import com.v2ray.ang.ui.compose.AppDropdownMenuItems
import com.v2ray.ang.ui.compose.AppListItem
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.verticalScrollbar

internal enum class AppPickerMenuAction(@StringRes val labelRes: Int) {
    SelectAll(R.string.menu_item_select_all),
    InvertSelection(R.string.menu_item_invert_selection)
}

private const val EXTRA_SELECTED_PACKAGES = "selected_packages"
private const val EXTRA_PICKER_TITLE = "picker_title"

class AppPickerActivity : BaseAppPickerActivity() {
    override fun televisionDestination() = televisionResultDestination(TvAppPickerActivity::class.java)

    companion object {
        fun createIntent(
            context: Context,
            selectedPackages: Collection<String> = emptyList(),
            title: String? = null
        ): Intent {
            return Intent(context, AppPickerActivity::class.java).apply {
                putStringArrayListExtra(EXTRA_SELECTED_PACKAGES, ArrayList(selectedPackages))
                title?.let { putExtra(EXTRA_PICKER_TITLE, it) }
            }
        }

        fun getSelectedPackages(intent: Intent?): List<String> {
            return intent?.getStringArrayListExtra(EXTRA_SELECTED_PACKAGES).orEmpty()
        }
    }

    @Composable
    override fun PickerContent(title: String, apps: List<AppInfo>, isLoading: Boolean, selectedPackages: Set<String>, actions: AppPickerActions) {
        AppPickerScreen(title, apps, isLoading, selectedPackages, actions.onBack, actions.onToggleApp, actions.onSearch, actions.onMenuAction)
    }
}

internal class AppPickerActions(
    val onBack: () -> Unit,
    val onToggleApp: (String) -> Unit,
    val onSearch: (String) -> Unit,
    val onMenuAction: (AppPickerMenuAction) -> Unit
)

abstract class BaseAppPickerActivity : BaseComponentActivity() {
    private var forwardingToTv = false

    protected open fun televisionDestination(): Intent? = null

    private val viewModel: AppPickerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val destination = televisionDestination()
        forwardingToTv = destination != null
        super.onCreate(savedInstanceState)
        if (destination != null) {
            startActivity(destination)
            super.finish()
            return
        }
        val initial = intent.getStringArrayListExtra(EXTRA_SELECTED_PACKAGES).orEmpty()
        viewModel.initialize(initial)
        viewModel.loadApps(this)
    }

    override fun finish() {
        setResult(
            RESULT_OK,
            Intent().apply {
                putStringArrayListExtra(EXTRA_SELECTED_PACKAGES, ArrayList(viewModel.getSelectedPackages()))
            }
        )
        super.finish()
    }

    @Composable
    final override fun ScreenContent() {
        if (forwardingToTv) return
        val apps by viewModel.displayedApps.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val selectedPackages by viewModel.selectedPackages.collectAsStateWithLifecycle()

        PickerContent(
            title = resolveScreenTitle(),
            apps = apps,
            isLoading = isLoading,
            selectedPackages = selectedPackages,
            actions = remember { AppPickerActions(::finish, viewModel::toggleApp, viewModel::filterApps, ::handleMenuAction) }
        )
    }

    @Composable
    internal abstract fun PickerContent(title: String, apps: List<AppInfo>, isLoading: Boolean, selectedPackages: Set<String>, actions: AppPickerActions)

    private fun handleMenuAction(action: AppPickerMenuAction) {
        when (action) {
            AppPickerMenuAction.SelectAll -> viewModel.selectAll()
            AppPickerMenuAction.InvertSelection -> viewModel.invertSelection()
        }
    }

    private fun resolveScreenTitle(): String {
        return intent.getStringExtra(EXTRA_PICKER_TITLE) ?: getString(R.string.per_app_proxy_settings)
    }
}

@Composable
private fun AppPickerScreen(
    title: String,
    apps: List<AppInfo>,
    isLoading: Boolean,
    selectedPackages: Set<String>,
    onBackClick: () -> Unit,
    onToggleApp: (String) -> Unit,
    onSearch: (String) -> Unit,
    onMenuAction: (AppPickerMenuAction) -> Unit
) {
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        onSearch(searchQuery)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = title,
                onBackClick = onBackClick,
                isLoading = isLoading,
                isSearchActive = showSearch,
                searchQuery = searchQuery,
                onSearchQueryChange = { query ->
                    searchQuery = query
                    onSearch(query)
                },
                onSearchClose = {
                    searchQuery = ""
                    onSearch("")
                    showSearch = false
                },
                searchPlaceholder = stringResource(R.string.menu_item_search),
                actions = {
                    if (!showSearch) {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(
                                painterResource(R.drawable.ic_search_24dp),
                                contentDescription = stringResource(R.string.acc_search)
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                painterResource(R.drawable.ic_more_vert_24dp),
                                contentDescription = null
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            AppDropdownMenuItems(AppPickerMenuAction.entries, { it.labelRes }) { action ->
                                showMenu = false
                                onMenuAction(action)
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScrollbar(listState),
            contentPadding = NavigationBarsBottomPadding()
        ) {
            items(items = apps, key = { it.packageName }) { app ->
                val checked = selectedPackages.contains(app.packageName)
                AppListItem(
                    appName = app.appName,
                    packageName = app.packageName,
                    icon = null,
                    checked = checked,
                    onCheckedChange = { onToggleApp(app.packageName) }
                )
                ItemDivider()
            }
        }
    }
}
