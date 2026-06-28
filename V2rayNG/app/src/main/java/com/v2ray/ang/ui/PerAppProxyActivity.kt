package com.v2ray.ang.ui

import android.os.Bundle
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppDivider
import com.v2ray.ang.compose.AppListItem
import com.v2ray.ang.compose.AppTheme
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.compose.colorFabActive
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.extension.v2RayApplication
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.AppManagerUtil
import com.v2ray.ang.util.ComposeToast
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.PerAppProxyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

class PerAppProxyActivity : ComponentActivity() {
    private val viewModel: PerAppProxyViewModel by viewModels()

    private var appsAll: List<AppInfo>? = null
    private val displayedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val isLoadingState = MutableStateFlow(false)

    private val perAppProxyState = MutableStateFlow(false)
    private val bypassAppsState = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        perAppProxyState.value = MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY, false)
        bypassAppsState.value = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS, false)

        setContent {
            AppTheme {
                PerAppProxyScreen(
                    viewModel = viewModel,
                    displayedApps = displayedApps,
                    isLoadingState = isLoadingState,
                    perAppProxyState = perAppProxyState,
                    bypassAppsState = bypassAppsState,
                    onBackClick = { finish() },
                    onPerAppProxyChanged = {
                        perAppProxyState.value = it
                        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, it)
                    },
                    onBypassAppsChanged = {
                        bypassAppsState.value = it
                        MmkvManager.encodeSettings(AppConfig.PREF_BYPASS_APPS, it)
                    },
                    onInfoClick = {
                        ComposeToast.info(
                            this,
                            getString(R.string.summary_pref_per_app_proxy),
                            android.widget.Toast.LENGTH_LONG
                        )
                    },
                    onToggleApp = { pkg ->
                        viewModel.toggle(pkg)
                    },
                    onSearch = { filterProxyApp(it) },
                    onSelectAll = { selectAllApp(); allowPerAppProxy() },
                    onInvertSelection = { invertSelection(); allowPerAppProxy() },
                    onSelectProxyAuto = { selectProxyAppAuto(); allowPerAppProxy() },
                    onImportProxyApp = { importProxyApp(); allowPerAppProxy() },
                    onExportProxyApp = { exportProxyApp() }
                )
            }
        }

        initList()
    }

    private fun initList() {
        isLoadingState.value = true
        lifecycleScope.launch {
            try {
                val apps = withContext(Dispatchers.IO) {
                    val appsList = AppManagerUtil.loadNetworkAppList(this@PerAppProxyActivity)
                    val collator = Collator.getInstance()
                    appsList.sortedWith { p1, p2 ->
                        val s1 = viewModel.contains(p1.packageName)
                        val s2 = viewModel.contains(p2.packageName)
                        when {
                            s1 && !s2 -> -1
                            !s1 && s2 -> 1
                            p1.isSystemApp && !p2.isSystemApp -> 1
                            !p1.isSystemApp && p2.isSystemApp -> -1
                            else -> collator.compare(p1.appName, p2.appName)
                        }
                    }
                }
                appsAll = apps
                displayedApps.value = apps
            } catch (e: Exception) {
                LogUtil.e(ANG_PACKAGE, "Error loading apps", e)
            } finally {
                isLoadingState.value = false
            }
        }
    }

    private fun selectAllApp() {
        val pkgNames = displayedApps.value.map { it.packageName }
        val allSelected = pkgNames.all { viewModel.contains(it) }
        if (allSelected) viewModel.removeAll(pkgNames) else viewModel.addAll(pkgNames)
    }

    private fun invertSelection() {
        displayedApps.value.forEach { app -> viewModel.toggle(app.packageName) }
    }

    private fun selectProxyAppAuto() {
        toast(R.string.msg_downloading_content)
        isLoadingState.value = true
        val url = AppConfig.ANDROID_PACKAGE_NAME_LIST_URL
        lifecycleScope.launch(Dispatchers.IO) {
            var content = HttpUtil.getUrlContent(
                UrlContentRequest(
                    url = url,
                    timeout = 5000
                )
            )
            if (content.isNullOrEmpty()) {
                val proxyUsername = SettingsManager.getSocksUsername()
                val proxyPassword = SettingsManager.getSocksPassword()
                val httpPort = SettingsManager.getHttpPort()
                content = HttpUtil.getUrlContent(
                    UrlContentRequest(
                        url = url,
                        timeout = 5000,
                        httpPort = httpPort,
                        proxyUsername = proxyUsername,
                        proxyPassword = proxyPassword
                    )
                ) ?: ""
            }
            launch(Dispatchers.Main) {
                selectProxyApp(content, true)
                toastSuccess(R.string.toast_success)
                isLoadingState.value = false
            }
        }
    }

    private fun importProxyApp() {
        val content = Utils.getClipboard(applicationContext)
        if (TextUtils.isEmpty(content)) return
        selectProxyApp(content, false)
        toastSuccess(R.string.toast_success)
    }

    private fun exportProxyApp() {
        var lst = bypassAppsState.value.toString()
        viewModel.blacklistFlow.value.forEach { pkg ->
            lst = lst + System.lineSeparator() + pkg
        }
        Utils.setClipboard(applicationContext, lst)
        toastSuccess(R.string.toast_success)
    }

    private fun allowPerAppProxy() {
        perAppProxyState.value = true
        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, true)
        SettingsChangeManager.makeRestartService()
    }

    private fun selectProxyApp(content: String, force: Boolean): Boolean {
        try {
            val proxyApps = if (TextUtils.isEmpty(content)) {
                Utils.readTextFromAssets(v2RayApplication, "proxy_package_name")
            } else content
            if (TextUtils.isEmpty(proxyApps)) return false

            viewModel.clear()
            val apps = displayedApps.value
            if (bypassAppsState.value) {
                apps.forEach { app ->
                    if (!inProxyApps(proxyApps, app.packageName, force))
                        viewModel.add(app.packageName)
                }
            } else {
                apps.forEach { app ->
                    if (inProxyApps(proxyApps, app.packageName, force))
                        viewModel.add(app.packageName)
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Error selecting proxy app", e)
            return false
        }
        return true
    }

    private fun inProxyApps(proxyApps: String, packageName: String, force: Boolean): Boolean {
        if (force) {
            if (packageName == "com.google.android.webview") return false
            if (packageName.startsWith("com.google")) return true
        }
        return proxyApps.indexOf(packageName) >= 0
    }

    private fun filterProxyApp(content: String): Boolean {
        val key = content.uppercase()
        displayedApps.value = if (key.isNotEmpty()) {
            appsAll?.filter {
                it.appName.uppercase().contains(key) || it.packageName.uppercase().contains(key)
            }.orEmpty()
        } else {
            appsAll.orEmpty()
        }
        return true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerAppProxyScreen(
    viewModel: PerAppProxyViewModel,
    displayedApps: MutableStateFlow<List<AppInfo>>,
    isLoadingState: MutableStateFlow<Boolean>,
    perAppProxyState: MutableStateFlow<Boolean>,
    bypassAppsState: MutableStateFlow<Boolean>,
    onBackClick: () -> Unit,
    onPerAppProxyChanged: (Boolean) -> Unit,
    onBypassAppsChanged: (Boolean) -> Unit,
    onInfoClick: () -> Unit,
    onToggleApp: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onSelectProxyAuto: () -> Unit,
    onImportProxyApp: () -> Unit,
    onExportProxyApp: () -> Unit
) {
    val apps by displayedApps.collectAsState()
    val isLoading by isLoadingState.collectAsState()
    val perAppProxy by perAppProxyState.collectAsState()
    val bypassApps by bypassAppsState.collectAsState()
    val blacklist by viewModel.blacklistFlow.collectAsState()

    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.per_app_proxy_settings),
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
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_item_select_proxy_app)) },
                                onClick = { showMenu = false; onSelectProxyAuto() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_item_import_proxy_app)) },
                                onClick = { showMenu = false; onImportProxyApp() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_item_export_proxy_app)) },
                                onClick = { showMenu = false; onExportProxyApp() }
                            )
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.per_app_proxy_settings_enable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = perAppProxy,
                            modifier = Modifier.scale(0.65f),
                            onCheckedChange = onPerAppProxyChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                                checkedTrackColor = colorFabActive
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.switch_bypass_apps_mode),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = bypassApps,
                            modifier = Modifier.scale(0.65f),
                            onCheckedChange = onBypassAppsChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                                checkedTrackColor = colorFabActive
                            )
                        )
                    }
                    IconButton(
                        onClick = onInfoClick
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_about_24dp),
                            contentDescription = stringResource(R.string.summary_pref_per_app_proxy),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            AppDivider()

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = apps, key = { it.packageName }) { app ->
                    val checked = blacklist.contains(app.packageName)
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
}
