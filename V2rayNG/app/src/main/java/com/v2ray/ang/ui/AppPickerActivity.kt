package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppDivider
import com.v2ray.ang.compose.AppListItem
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.compose.AppTheme
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.util.AppManagerUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

class AppPickerActivity : ComponentActivity() {
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

    private val initialSelectedPackages by lazy {
        intent.getStringArrayListExtra(EXTRA_SELECTED_PACKAGES).orEmpty()
    }
    private val selectedPackages = LinkedHashSet<String>()
    private var appsAll: List<AppInfo> = emptyList()

    private val displayedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val selectionVersion = MutableStateFlow(0)
    private val isLoadingState = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        selectedPackages.addAll(initialSelectedPackages)

        setContent {
            AppTheme {
                AppPickerScreen(
                    title = resolveScreenTitle(),
                    displayedApps = displayedApps,
                    selectionVersion = selectionVersion,
                    isLoadingState = isLoadingState,
                    isSelected = { selectedPackages.contains(it) },
                    onBackClick = { finish() },
                    onToggleApp = { pkg ->
                        if (selectedPackages.contains(pkg)) selectedPackages.remove(pkg)
                        else selectedPackages.add(pkg)
                        selectionVersion.value++
                    },
                    onSearch = { filterApps(it) },
                    onSelectAll = { selectAllVisible() },
                    onInvertSelection = { invertVisibleSelection() }
                )
            }
        }
        loadApps()
    }

    override fun finish() {
        setResult(
            RESULT_OK,
            Intent().apply {
                putStringArrayListExtra(EXTRA_SELECTED_PACKAGES, getSelectedPackages())
            }
        )
        super.finish()
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun createSpecialItemUnidentified(): AppInfo {
        val icon = requireNotNull(
            getDrawable(android.R.drawable.ic_menu_help)
                ?: getDrawable(android.R.drawable.sym_def_app_icon)
        ) { "No fallback drawable available" }
        return AppInfo(
            appName = getString(R.string.app_picker_unknown_app),
            packageName = AppConfig.UNIDENTIFIED_PACKAGE,
            appIcon = icon,
            isSystemApp = false,
            isSelected = 0
        )
    }

    private fun loadApps() {
        isLoadingState.value = true
        lifecycleScope.launch {
            try {
                val apps = withContext(Dispatchers.IO) {
                    val appsList = AppManagerUtil.loadNetworkAppList(this@AppPickerActivity)
                    val sortedApps = sortApps(appsList)
                    listOf(createSpecialItemUnidentified()) + sortedApps
                }
                appsAll = apps
                updateDisplayedApps(apps)
            } catch (e: Exception) {
                LogUtil.e("AppPickerActivity", "Failed to load app list", e)
            } finally {
                isLoadingState.value = false
            }
        }
    }

    private fun filterApps(content: String) {
        val key = content.uppercase()
        val filteredApps = appsAll.filter { app ->
            key.isEmpty() || matchesSearch(app, key)
        }
        updateDisplayedApps(filteredApps)
    }

    private fun sortApps(apps: List<AppInfo>): List<AppInfo> {
        val collator = Collator.getInstance()
        return apps.sortedWith { p1, p2 ->
            val p1Selected = selectedPackages.contains(p1.packageName)
            val p2Selected = selectedPackages.contains(p2.packageName)
            when {
                p1Selected && !p2Selected -> -1
                !p1Selected && p2Selected -> 1
                p1.isSystemApp && !p2.isSystemApp -> 1
                !p1.isSystemApp && p2.isSystemApp -> -1
                else -> collator.compare(p1.appName, p2.appName)
            }
        }
    }

    private fun matchesSearch(app: AppInfo, keyword: String): Boolean {
        return app.appName.uppercase().contains(keyword) || app.packageName.uppercase().contains(keyword)
    }

    private fun updateDisplayedApps(apps: List<AppInfo>) {
        displayedApps.value = apps
    }

    private fun selectAllVisible() {
        displayedApps.value.forEach { app -> selectedPackages.add(app.packageName) }
        selectionVersion.value++
    }

    private fun invertVisibleSelection() {
        displayedApps.value.forEach { app ->
            if (selectedPackages.contains(app.packageName)) {
                selectedPackages.remove(app.packageName)
            } else {
                selectedPackages.add(app.packageName)
            }
        }
        selectionVersion.value++
    }

    private fun getSelectedPackages(): ArrayList<String> = ArrayList(selectedPackages.sorted())

    private fun resolveScreenTitle(): String {
        return intent.getStringExtra(EXTRA_PICKER_TITLE) ?: getString(R.string.per_app_proxy_settings)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(
    title: String,
    displayedApps: MutableStateFlow<List<AppInfo>>,
    selectionVersion: MutableStateFlow<Int>,
    isLoadingState: MutableStateFlow<Boolean>,
    isSelected: (String) -> Boolean,
    onBackClick: () -> Unit,
    onToggleApp: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit
) {
    val apps by displayedApps.collectAsState()
    val version by selectionVersion.collectAsState()
    val isLoading by isLoadingState.collectAsState()

    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
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
                                contentDescription = stringResource(R.string.menu_item_search)
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert_24dp), contentDescription = null)
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
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            items(items = apps, key = { it.packageName }) { app ->
                val checked = remember(app.packageName, version) { isSelected(app.packageName) }
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
