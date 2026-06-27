package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
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
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.R
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.extension.v2RayApplication
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.AppManagerUtil
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.PerAppProxyViewModel
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

class PerAppProxyActivity : BaseActivity() {
    private val viewModel: PerAppProxyViewModel by viewModels()
    private val appsAll = mutableStateListOf<AppInfo>()
    private var searchQuery = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                PerAppProxyScreen(
                    viewModel = viewModel,
                    apps = getFilteredApps(),
                    searchQuery = searchQuery.value,
                    isLoadingFlow = isLoadingFlow,
                    onBack = { finish() },
                    onSearchQueryChange = { searchQuery.value = it },
                    onSelectAll = { selectAllApp() },
                    onInvertSelection = { invertSelection() },
                    onAutoSelect = { selectProxyAppAuto() },
                    onImportClipboard = { importProxyApp() },
                    onExportClipboard = { exportProxyApp() }
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

    private fun loadApps() {
        showLoading()
        lifecycleScope.launch {
            try {
                val apps = withContext(Dispatchers.IO) {
                    val appsList = AppManagerUtil.loadNetworkAppList(this@PerAppProxyActivity)
                    val blacklistSet = viewModel.getAll()
                    if (blacklistSet.isNotEmpty()) {
                        appsList.sortedWith { p1, p2 ->
                            val p1Selected = blacklistSet.contains(p1.packageName)
                            val p2Selected = blacklistSet.contains(p2.packageName)
                            when {
                                p1Selected && !p2Selected -> -1
                                !p1Selected && p2Selected -> 1
                                p1.isSystemApp && !p2.isSystemApp -> 1
                                !p1.isSystemApp && p2.isSystemApp -> -1
                                else -> p1.appName.lowercase().compareTo(p2.appName.lowercase())
                            }
                        }
                    } else {
                        val collator = Collator.getInstance()
                        appsList.sortedWith(compareBy(collator) { it.appName })
                    }
                }
                appsAll.clear()
                appsAll.addAll(apps)
            } catch (e: Exception) {
                LogUtil.e(ANG_PACKAGE, "Error loading apps", e)
            } finally {
                hideLoading()
            }
        }
    }

    private fun selectAllApp() {
        val pkgNames = appsAll.map { it.packageName }
        val allSelected = pkgNames.all { viewModel.contains(it) }
        if (allSelected) viewModel.removeAll(pkgNames) else viewModel.addAll(pkgNames)
        allowPerAppProxy()
    }

    private fun invertSelection() {
        appsAll.forEach { viewModel.toggle(it.packageName) }
        allowPerAppProxy()
    }

    private fun selectProxyAppAuto() {
        toast(R.string.msg_downloading_content)
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            val url = AppConfig.ANDROID_PACKAGE_NAME_LIST_URL
            var content = HttpUtil.getUrlContent(UrlContentRequest(url = url, timeout = 5000))
            if (content.isNullOrEmpty()) {
                content = HttpUtil.getUrlContent(UrlContentRequest(
                    url = url, timeout = 5000, 
                    httpPort = SettingsManager.getHttpPort(), 
                    proxyUsername = SettingsManager.getSocksUsername(), 
                    proxyPassword = SettingsManager.getSocksPassword()
                )) ?: ""
            }
            withContext(Dispatchers.Main) {
                selectProxyApp(content, true)
                toastSuccess(R.string.toast_success)
                hideLoading()
            }
        }
    }

    private fun importProxyApp() {
        val content = Utils.getClipboard(this)
        if (content.isNotEmpty()) {
            selectProxyApp(content, false)
            toastSuccess(R.string.toast_success)
        }
    }

    private fun exportProxyApp() {
        val bypass = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS, false)
        val lst = (listOf(bypass.toString()) + viewModel.getAll()).joinToString(System.lineSeparator())
        Utils.setClipboard(this, lst)
        toastSuccess(R.string.toast_success)
    }

    private fun allowPerAppProxy() {
        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, true)
        SettingsChangeManager.makeRestartService()
    }

    private fun selectProxyApp(content: String, force: Boolean) {
        val proxyApps = content.ifEmpty { Utils.readTextFromAssets(v2RayApplication, "proxy_package_name") }
        if (proxyApps.isEmpty()) return

        viewModel.clear()
        val bypass = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS, false)
        appsAll.forEach { app ->
            val pkg = app.packageName
            val isIn = proxyApps.contains(pkg) || (force && pkg.startsWith("com.google") && pkg != "com.google.android.webview")
            if (bypass != isIn) viewModel.add(pkg)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerAppProxyScreen(
    viewModel: PerAppProxyViewModel,
    apps: List<AppInfo>,
    searchQuery: String,
    isLoadingFlow: kotlinx.coroutines.flow.StateFlow<Boolean>,
    onBack: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onAutoSelect: () -> Unit,
    onImportClipboard: () -> Unit,
    onExportClipboard: () -> Unit
) {
    val isLoading by isLoadingFlow.collectAsStateWithLifecycle()
    val blacklist by viewModel.blacklistFlow.collectAsStateWithLifecycle()
    var showMenu by remember { mutableStateOf(false) }
    
    var perAppProxy by remember { mutableStateOf(MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY, false)) }
    var bypassApps by remember { mutableStateOf(MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS, false)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.per_app_proxy_settings)) },
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
                            DropdownMenuItem(text = { Text(stringResource(R.string.menu_item_select_all)) }, onClick = { showMenu = false; onSelectAll() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.menu_item_invert_selection)) }, onClick = { showMenu = false; onInvertSelection() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.menu_item_select_proxy_app)) }, onClick = { showMenu = false; onAutoSelect() })
                            DropdownMenuItem(text = { Text("Import from clipboard") }, onClick = { showMenu = false; onImportClipboard() })
                            DropdownMenuItem(text = { Text("Export to clipboard") }, onClick = { showMenu = false; onExportClipboard() })
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Enable Per-app Proxy", modifier = Modifier.weight(1f))
                    Switch(checked = perAppProxy, onCheckedChange = { 
                        perAppProxy = it
                        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, it)
                        SettingsChangeManager.makeRestartService()
                    })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(R.string.switch_bypass_apps_mode), modifier = Modifier.weight(1f))
                    IconButton(onClick = { /* Show tips toast */ }) {
                        Icon(Icons.Default.Info, contentDescription = null)
                    }
                    Switch(checked = bypassApps, onCheckedChange = { 
                        bypassApps = it
                        MmkvManager.encodeSettings(AppConfig.PREF_BYPASS_APPS, it)
                        SettingsChangeManager.makeRestartService()
                    })
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                placeholder = { Text(stringResource(R.string.menu_item_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )
            
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(apps, key = { it.packageName }) { app ->
                    AppProxyItem(
                        app = app,
                        isSelected = blacklist.contains(app.packageName),
                        onToggle = { viewModel.toggle(app.packageName) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
fun AppProxyItem(app: AppInfo, isSelected: Boolean, onToggle: () -> Unit) {
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
