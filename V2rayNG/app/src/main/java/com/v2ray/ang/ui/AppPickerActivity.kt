package com.v2ray.ang.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppDivider
import com.v2ray.ang.compose.AppListItem
import com.v2ray.ang.compose.AppScaffold
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.viewmodel.AppPickerViewModel

class AppPickerActivity : BaseComponentActivity() {

    companion object {
        private const val EXTRA_SELECTED_PACKAGES = "selected_packages"
        private const val EXTRA_PICKER_TITLE = "picker_title"

        fun createIntent(
            context: Context,
            selectedPackages: Collection<String> = emptyList(),
            title: String? = null
        ): Intent = Intent(context, AppPickerActivity::class.java).apply {
            putStringArrayListExtra(EXTRA_SELECTED_PACKAGES, ArrayList(selectedPackages))
            title?.let { putExtra(EXTRA_PICKER_TITLE, it) }
        }

        fun getSelectedPackages(intent: Intent?): List<String> {
            return intent?.getStringArrayListExtra(EXTRA_SELECTED_PACKAGES).orEmpty()
        }
    }

    private val viewModel: AppPickerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    override fun ScreenContent() {
        val apps by viewModel.displayedApps.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val selectedPackages by viewModel.selectedPackages.collectAsStateWithLifecycle()

        AppPickerScreen(
            title = resolveScreenTitle(),
            apps = apps,
            isLoading = isLoading,
            selectedPackages = selectedPackages,
            onBackClick = { finish() },
            onToggleApp = { viewModel.toggleApp(it) },
            onSearch = { viewModel.filterApps(it) },
            onSelectAll = { viewModel.selectAll() },
            onInvertSelection = { viewModel.invertSelection() }
        )
    }

    private fun resolveScreenTitle(): String {
        return intent.getStringExtra(EXTRA_PICKER_TITLE) ?: getString(R.string.per_app_proxy_settings)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(
    title: String,
    apps: List<AppInfo>,
    isLoading: Boolean,
    selectedPackages: Set<String>,
    onBackClick: () -> Unit,
    onToggleApp: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit
) {
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        onSearch(searchQuery)
    }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = title,
                onBackClick = onBackClick,
                isLoading = isLoading,
                isSearchActive = showSearch,
                searchQuery = searchQuery,
                onSearchQueryChange = { query ->
                    searchQuery = query
                },
                onSearchClose = {
                    searchQuery = ""
                    showSearch = false
                },
                searchPlaceholder = stringResource(R.string.menu_item_search),
                actions = {
                    if (!showSearch) {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(
                                painterResource(R.drawable.ic_search_24dp),
                                contentDescription = stringResource(R.string.menu_item_search)
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
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_item_select_all)) },
                                onClick = { showMenu = false; onSelectAll() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_item_invert_selection)) },
                                onClick = { showMenu = false; onInvertSelection() }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            items(items = apps, key = { it.packageName }) { app ->
                val checked = selectedPackages.contains(app.packageName)
                AppListItem(
                    appName = app.appName,
                    packageName = app.packageName,
                    icon = app.appIcon,
                    checked = checked,
                    onCheckedChange = { onToggleApp(app.packageName) }
                )
                AppDivider()
            }
        }
    }
}
