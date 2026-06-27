package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.util.AppManagerUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

class AppPickerActivity : BaseActivity() {
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
    private val selectedPackages = mutableStateListOf<String>()
    private val appsAll = mutableStateListOf<AppInfo>()
    private var searchQuery = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedPackages.addAll(initialSelectedPackages)

        setContent {
            MaterialTheme {
                AppPickerScreen(
                    title = resolveScreenTitle(),
                    apps = getFilteredApps(),
                    selectedPackages = selectedPackages,
                    searchQuery = searchQuery.value,
                    isLoadingFlow = isLoadingFlow,
                    onBack = { finish() },
                    onSearchQueryChange = { searchQuery.value = it },
                    onToggleApp = { pkg ->
                        if (selectedPackages.contains(pkg)) selectedPackages.remove(pkg)
                        else selectedPackages.add(pkg)
                    },
                    onSelectAll = { selectAllVisible() },
                    onInvertSelection = { invertVisibleSelection() }
                )
            }
        }

        loadApps()
    }

    private fun getFilteredApps(): List<AppInfo> {
        val query = searchQuery.value.uppercase()
        return appsAll.filter { app ->
            query.isEmpty() || app.appName.uppercase().contains(query) || app.packageName.uppercase().contains(query)
        }
    }

    override fun finish() {
        setResult(
            RESULT_OK,
            Intent().apply {
                putStringArrayListExtra(EXTRA_SELECTED_PACKAGES, ArrayList(selectedPackages.sorted()))
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
        showLoading()
        lifecycleScope.launch {
            try {
                val apps = withContext(Dispatchers.IO) {
                    val appsList = AppManagerUtil.loadNetworkAppList(this@AppPickerActivity)
                    listOf(createSpecialItemUnidentified()) + sortApps(appsList)
                }
                appsAll.clear()
                appsAll.addAll(apps)
            } catch (e: Exception) {
                LogUtil.e("AppPickerActivity", "Failed to load app list", e)
            } finally {
                hideLoading()
            }
        }
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

    private fun selectAllVisible() {
        getFilteredApps().forEach { app -> 
            if (!selectedPackages.contains(app.packageName)) {
                selectedPackages.add(app.packageName)
            }
        }
    }

    private fun invertVisibleSelection() {
        getFilteredApps().forEach { app ->
            if (selectedPackages.contains(app.packageName)) {
                selectedPackages.remove(app.packageName)
            } else {
                selectedPackages.add(app.packageName)
            }
        }
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
    selectedPackages: List<String>,
    searchQuery: String,
    isLoadingFlow: kotlinx.coroutines.flow.StateFlow<Boolean>,
    onBack: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleApp: (String) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit
) {
    val isLoading by isLoadingFlow.collectAsStateWithLifecycle()
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
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
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                placeholder = { Text(stringResource(R.string.menu_item_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )
            
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(apps, key = { it.packageName }) { app ->
                    AppItem(
                        app = app,
                        isSelected = selectedPackages.contains(app.packageName),
                        onToggle = { onToggleApp(app.packageName) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
fun AppItem(app: AppInfo, isSelected: Boolean, onToggle: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onToggle),
        headlineContent = { Text(app.appName) },
        supportingContent = { Text(app.packageName, style = MaterialTheme.typography.bodySmall) },
        leadingContent = {
            val bitmap = remember(app.appIcon) { app.appIcon.toBitmap().asImageBitmap() }
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(40.dp))
        },
        trailingContent = {
            Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
        }
    )
}
