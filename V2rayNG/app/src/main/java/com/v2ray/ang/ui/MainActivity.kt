package com.v2ray.ang.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppDivider
import com.v2ray.ang.compose.AppTheme
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.compose.ConfirmDialog
import com.v2ray.ang.compose.SelectListDialog
import com.v2ray.ang.compose.LocalDarkTheme
import com.v2ray.ang.compose.QRCodeDialog
import com.v2ray.ang.compose.ReorderableGridItem
import com.v2ray.ang.compose.ReorderableListItem
import com.v2ray.ang.compose.colorConfigType
import com.v2ray.ang.compose.colorFabActive
import com.v2ray.ang.compose.colorFabInactiveDark
import com.v2ray.ang.compose.colorFabInactiveLight
import com.v2ray.ang.compose.colorPing
import com.v2ray.ang.compose.colorPingRed
import com.v2ray.ang.compose.verticalScrollbar
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState

class MainActivity : HelperBaseComponentActivity() {
    val mainViewModel: MainViewModel by viewModels()

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) startV2Ray()
        }
    private val requestActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (SettingsChangeManager.consumeRestartService() && mainViewModel.isRunningFlow.value == true) restartV2Ray()
            if (SettingsChangeManager.consumeSetupGroupTab()) mainViewModel.setupGroupTab(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
        SubscriptionUpdater.sync()
        mainViewModel.setupGroupTab(this)

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
    }

    @Composable
    override fun ScreenContent() {
        MainScreen(
            mainViewModel = mainViewModel,
            onFabClick = ::handleFabAction,
            onTestClick = ::handleLayoutTestClick,
            onNavigate = ::navigateTo,
            onImportManually = ::importManually,
            onImportQRcode = ::importQRcode,
            onImportClipboard = ::importClipboard,
            onImportLocal = ::importConfigLocal,
            onSubUpdate = ::importConfigViaSub,
            onExportAll = ::exportAll,
            onRealPingAll = mainViewModel::testAllRealPing,
            onRestartService = ::restartV2Ray,
            onDelAllConfig = ::delAllConfig,
            onDelDuplicateConfig = ::delDuplicateConfig,
            onDelInvalidConfig = ::delInvalidConfig,
            onSortByTestResults = ::sortByTestResults,
            onEditServer = ::editServer,
            onRemoveServer = ::removeServer,
            onSelectServer = ::setSelectServer,
            onShareQRCode = ::getShareQRCodeBitmap,
            onShareClipboard = ::shareToClipboard,
            onShareFullContent = ::shareFullContentAsync,
            onSubscriptionIdChanged = mainViewModel::subscriptionIdChanged,
            onLocateSelectedServer = mainViewModel::triggerLocateSelectedServer,
            shareMethodEntries = resources.getStringArray(R.array.share_method).toList(),
            shareMethodMoreEntries = resources.getStringArray(R.array.share_method_more).toList()
        )
    }

    fun getShareQRCodeBitmap(guid: String): Bitmap? = AngConfigManager.share2QRCode(guid)
    fun shareToClipboard(guid: String): Boolean =
        AngConfigManager.share2Clipboard(this, guid) == 0

    fun shareFullContentAsync(guid: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = AngConfigManager.shareFullContent2Clipboard(this@MainActivity, guid)
            withContext(Dispatchers.Main) {
                if (result == 0) toastSuccess(R.string.toast_success)
                else toastError(R.string.toast_failure)
            }
        }
    }

    private fun navigateTo(destination: String) {
        val intent = when (destination) {
            "sub_setting" -> Intent(this, SubSettingActivity::class.java)
            "per_app_proxy" -> Intent(this, PerAppProxyActivity::class.java)
            "routing_setting" -> Intent(this, RoutingSettingActivity::class.java)
            "user_asset" -> Intent(this, UserAssetActivity::class.java)
            "settings" -> Intent(this, SettingsActivity::class.java)
            "logcat" -> Intent(this, LogcatActivity::class.java)
            "check_update" -> Intent(this, CheckUpdateActivity::class.java)
            "backup_restore" -> Intent(this, BackupActivity::class.java)
            "about" -> Intent(this, AboutActivity::class.java)
            "promotion" -> {
                Utils.openUri(
                    this,
                    "${Utils.decode(AppConfig.APP_PROMOTION_URL)}?t=${System.currentTimeMillis()}"
                )
                return
            }
            else -> return
        }
        requestActivityLauncher.launch(intent)
    }

    private fun handleFabAction() {
        if (mainViewModel.isRunningFlow.value == true) {
            CoreServiceManager.stopVService(this)
        } else if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent == null) startV2Ray() else requestVpnPermission.launch(intent)
        } else {
            startV2Ray()
        }
    }

    private fun handleLayoutTestClick() {
        if (mainViewModel.isRunningFlow.value == true) mainViewModel.testCurrentServerRealPing()
    }

    private fun startV2Ray() {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            toast(R.string.title_file_chooser); return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN && MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING)) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }
        CoreServiceManager.startVService(this)
    }

    private fun restartV2Ray() {
        if (mainViewModel.isRunningFlow.value == true) CoreServiceManager.stopVService(this)
        lifecycleScope.launch { delay(500); startV2Ray() }
    }

    private fun importManually(createConfigType: Int) {
        val intent = when (createConfigType) {
            EConfigType.POLICYGROUP.value -> Intent(this, ServerGroupActivity::class.java)
            EConfigType.PROXYCHAIN.value -> Intent(this, ServerProxyChainActivity::class.java)
            else -> Intent(this, ServerActivity::class.java).putExtra(
                "createConfigType",
                createConfigType
            )
        }
        intent.putExtra("subscriptionId", mainViewModel.subscriptionId)
        startActivity(intent)
    }

    private fun importQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) importBatchConfig(scanResult)
        }
    }

    private fun importClipboard() {
        try {
            importBatchConfig(Utils.getClipboard(this))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from clipboard", e)
        }
    }

    private fun importBatchConfig(server: String?) {
        mainViewModel.setLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val (count, countSub) = AngConfigManager.importBatchConfig(
                    server,
                    mainViewModel.subscriptionId,
                    true
                )
                delay(500L)
                withContext(Dispatchers.Main) {
                    when {
                        count > 0 -> {
                            toast(getString(R.string.title_import_config_count, count))
                            mainViewModel.setupGroupTab(this@MainActivity)
                        }
                        countSub > 0 -> mainViewModel.setupGroupTab(this@MainActivity)
                        else -> toastError(R.string.toast_failure)
                    }
                    mainViewModel.setLoading(false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    toastError(R.string.toast_failure)
                    mainViewModel.setLoading(false)
                }
                LogUtil.e(AppConfig.TAG, "Failed to import batch config", e)
            }
        }
    }

    private fun importConfigLocal() {
        launchFileChooser { uri ->
            if (uri == null) return@launchFileChooser
            try {
                contentResolver.openInputStream(uri)
                    .use { input -> importBatchConfig(input?.bufferedReader()?.readText()) }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to read content from URI", e)
            }
        }
    }

    private fun importConfigViaSub() {
        mainViewModel.setLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            val result = mainViewModel.updateConfigViaSubAll()
            delay(500L)
            launch(Dispatchers.Main) {
                when {
                    result.successCount + result.failureCount + result.skipCount == 0 ->
                        toast(R.string.title_update_subscription_no_subscription)

                    result.successCount > 0 && result.failureCount + result.skipCount == 0 ->
                        toast(getString(R.string.title_update_config_count, result.configCount))

                    else ->
                        toast(
                            getString(
                                R.string.title_update_subscription_result,
                                result.configCount,
                                result.successCount,
                                result.failureCount,
                                result.skipCount
                            )
                        )
                }
                if (result.configCount > 0) mainViewModel.setupGroupTab(this@MainActivity)
                mainViewModel.setLoading(false)
            }
        }
    }

    private fun exportAll() {
        mainViewModel.setLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            val ret = mainViewModel.exportAllServer()
            launch(Dispatchers.Main) {
                if (ret > 0) toast(getString(R.string.title_export_config_count, ret))
                else toastError(R.string.toast_failure)
                mainViewModel.setLoading(false)
            }
        }
    }

    private fun delAllConfig() {
        mainViewModel.setLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            val ret = mainViewModel.removeAllServer()
            launch(Dispatchers.Main) {
                mainViewModel.setupGroupTab(this@MainActivity)
                toast(getString(R.string.title_del_config_count, ret))
                mainViewModel.setLoading(false)
            }
        }
    }

    private fun delDuplicateConfig() {
        mainViewModel.setLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            val ret = mainViewModel.removeDuplicateServer()
            launch(Dispatchers.Main) {
                mainViewModel.setupGroupTab(this@MainActivity)
                toast(getString(R.string.title_del_duplicate_config_count, ret))
                mainViewModel.setLoading(false)
            }
        }
    }

    private fun delInvalidConfig() {
        mainViewModel.setLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            val ret = mainViewModel.removeInvalidServer()
            launch(Dispatchers.Main) {
                mainViewModel.setupGroupTab(this@MainActivity)
                toast(getString(R.string.title_del_config_count, ret))
                mainViewModel.setLoading(false)
            }
        }
    }

    private fun sortByTestResults() {
        mainViewModel.setLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            mainViewModel.sortByTestResults()
            launch(Dispatchers.Main) {
                mainViewModel.setupGroupTab(this@MainActivity)
                mainViewModel.setLoading(false)
            }
        }
    }

    private fun editServer(guid: String, profile: ProfileItem) {
        val activityClass = when (profile.configType) {
            EConfigType.CUSTOM -> ServerCustomConfigActivity::class.java
            EConfigType.POLICYGROUP -> ServerGroupActivity::class.java
            EConfigType.PROXYCHAIN -> ServerProxyChainActivity::class.java
            else -> ServerActivity::class.java
        }
        startActivity(
            Intent(this, activityClass)
                .putExtra("guid", guid)
                .putExtra("isRunning", mainViewModel.isRunningFlow.value)
                .putExtra("createConfigType", profile.configType.value)
                .putExtra("subscriptionId", mainViewModel.subscriptionId)
        )
    }

    private fun removeServer(guid: String) {
        if (guid == MmkvManager.getSelectServer()) {
            toast(R.string.toast_action_not_allowed); return
        }
        mainViewModel.removeServer(guid)
        mainViewModel.setupGroupTab(this)
    }

    private fun setSelectServer(guid: String) {
        val selected = MmkvManager.getSelectServer()
        if (guid != selected) {
            mainViewModel.updateSelectedGuid(guid)
            if (mainViewModel.isRunningFlow.value == true) restartV2Ray()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onFabClick: () -> Unit,
    onTestClick: () -> Unit,
    onNavigate: (String) -> Unit,
    onImportManually: (Int) -> Unit,
    onImportQRcode: () -> Unit,
    onImportClipboard: () -> Unit,
    onImportLocal: () -> Unit,
    onSubUpdate: () -> Unit,
    onExportAll: () -> Unit,
    onRealPingAll: () -> Unit,
    onRestartService: () -> Unit,
    onDelAllConfig: () -> Unit,
    onDelDuplicateConfig: () -> Unit,
    onDelInvalidConfig: () -> Unit,
    onSortByTestResults: () -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit,
    onSelectServer: (String) -> Unit,
    onShareQRCode: (String) -> Bitmap?,
    onShareClipboard: (String) -> Boolean,
    onShareFullContent: (String) -> Unit,
    onSubscriptionIdChanged: (String) -> Unit,
    onLocateSelectedServer: () -> Unit,
    shareMethodEntries: List<String>,
    shareMethodMoreEntries: List<String>
) {
    val context = LocalContext.current
    val groups by mainViewModel.groups.collectAsStateWithLifecycle()
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val isRunning by mainViewModel.isRunningFlow.collectAsStateWithLifecycle()
    val displayText by mainViewModel.displayText.collectAsStateWithLifecycle()
    val perGroupServers by mainViewModel.perGroupServers.collectAsStateWithLifecycle()
    val selectedGuid by mainViewModel.selectedGuid.collectAsStateWithLifecycle()

    val isDarkTheme = LocalDarkTheme.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showDelAllConfirm by remember { mutableStateOf(false) }
    var showDelDuplicateConfirm by remember { mutableStateOf(false) }
    var showDelInvalidConfirm by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf<String?>(null) }

    var shareTarget by remember { mutableStateOf<Triple<String, ProfileItem, Boolean>?>(null) }
    var showQRCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val doubleColumnDisplay = remember {
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, false)
    }
    val confirmRemove = remember {
        MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE, false)
    }

    val initialPage = remember(groups) {
        (groups.indexOfFirst { it.id == mainViewModel.subscriptionId }
            .takeIf { it >= 0 } ?: (groups.size - 1).coerceAtLeast(0))
            .coerceIn(0, (groups.size - 1).coerceAtLeast(0))
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { groups.size.coerceAtLeast(1) }
    )

    DisposableEffect(pagerState.currentPage) {
        onDispose {
            mainViewModel.cancelAllPing()
        }
    }

    val lazyListStates = remember { mutableStateMapOf<Int, LazyListState>() }
    val lazyGridStates = remember { mutableStateMapOf<Int, LazyGridState>() }

    val drawerScrollState = rememberScrollState()
    val importMenuScrollState = rememberScrollState()
    val moreMenuScrollState = rememberScrollState()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val maxMenuHeight = LocalConfiguration.current.screenHeightDp.dp - statusBarHeight - navBarHeight - 20.dp

    var locateInProgress by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (groups.isNotEmpty() && page in groups.indices && !locateInProgress) {
                onSubscriptionIdChanged(groups[page].id)
            }
        }
    }

    LaunchedEffect(Unit) {
        mainViewModel.locateEvent.collect { target ->
            locateInProgress = true
            try {
                if (pagerState.currentPage != target.groupIndex) {
                    pagerState.animateScrollToPage(
                        target.groupIndex,
                        animationSpec = tween(durationMillis = 400)
                    )
                    snapshotFlow { pagerState.settledPage }
                        .first { it == target.groupIndex }
                }

                onSubscriptionIdChanged(target.groupId)
                delay(100)

                if (doubleColumnDisplay) {
                    val gridState = lazyGridStates[target.groupIndex]
                    if (gridState != null) {
                        scrollToPositionWithOffset(gridState, target.itemPosition)
                    }
                } else {
                    val listState = lazyListStates[target.groupIndex]
                    if (listState != null) {
                        scrollToPositionWithOffset(listState, target.itemPosition)
                    }
                }
            } finally {
                delay(200)
                locateInProgress = false
            }
        }
    }

    if (showDelAllConfirm) {
        ConfirmDialog(
            message = stringResource(R.string.del_config_comfirm),
            confirmText = stringResource(android.R.string.ok),
            dismissText = stringResource(android.R.string.cancel),
            onConfirm = { showDelAllConfirm = false; onDelAllConfig() },
            onDismiss = { showDelAllConfirm = false }
        )
    }
    if (showDelDuplicateConfirm) {
        ConfirmDialog(
            message = stringResource(R.string.del_config_comfirm),
            confirmText = stringResource(android.R.string.ok),
            dismissText = stringResource(android.R.string.cancel),
            onConfirm = { showDelDuplicateConfirm = false; onDelDuplicateConfig() },
            onDismiss = { showDelDuplicateConfirm = false }
        )
    }
    if (showDelInvalidConfirm) {
        ConfirmDialog(
            message = stringResource(R.string.del_invalid_config_comfirm),
            confirmText = stringResource(android.R.string.ok),
            dismissText = stringResource(android.R.string.cancel),
            onConfirm = { showDelInvalidConfirm = false; onDelInvalidConfig() },
            onDismiss = { showDelInvalidConfirm = false }
        )
    }
    if (showRemoveConfirm != null) {
        val guid = showRemoveConfirm!!
        ConfirmDialog(
            message = stringResource(R.string.del_config_comfirm),
            confirmText = stringResource(android.R.string.ok),
            dismissText = stringResource(android.R.string.cancel),
            onConfirm = { showRemoveConfirm = null; onRemoveServer(guid) },
            onDismiss = { showRemoveConfirm = null }
        )
    }
    if (shareTarget != null) {
        val (guid, profile, more) = shareTarget!!
        val isCustom = profile.configType.isComplexType()
        val (shareOptions, skip) = if (more) {
            val options =
                if (isCustom) shareMethodMoreEntries.takeLast(3) else shareMethodMoreEntries
            options to if (isCustom) 2 else 0
        } else {
            val options = if (isCustom) shareMethodEntries.takeLast(1) else shareMethodEntries
            options to if (isCustom) 2 else 0
        }
        SelectListDialog(
            options = shareOptions,
            onSelected = { index, _ ->
                shareTarget = null
                when (index + skip) {
                    0 -> showQRCodeBitmap = onShareQRCode(guid)
                    1 -> onShareClipboard(guid)
                    2 -> onShareFullContent(guid)
                    3 -> onEditServer(guid, profile)
                    4 -> onRemoveServer(guid)
                }
            },
            onDismiss = { shareTarget = null }
        )
    }
    if (showQRCodeBitmap != null) {
        QRCodeDialog(bitmap = showQRCodeBitmap, onDismiss = { showQRCodeBitmap = null })
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .navigationBarsPadding(),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(drawerScrollState)
                        .verticalScrollbar(drawerScrollState)
                        .padding(bottom = 80.dp)
                ) {
                    Surface(modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontFamily = FontFamily(Font(R.font.montserrat_thin)),
                                    fontWeight = FontWeight.Thin
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    listOf(
                        Triple(
                            R.drawable.ic_subscriptions_24dp,
                            R.string.title_sub_setting,
                            "sub_setting"
                        ),
                        Triple(
                            R.drawable.ic_per_apps_24dp,
                            R.string.per_app_proxy_settings,
                            "per_app_proxy"
                        ),
                        Triple(
                            R.drawable.ic_routing_24dp,
                            R.string.routing_settings_title,
                            "routing_setting"
                        ),
                        Triple(
                            R.drawable.ic_file_24dp,
                            R.string.title_user_asset_setting,
                            "user_asset"
                        ),
                        Triple(R.drawable.ic_settings_24dp, R.string.title_settings, "settings"),
                    ).forEach { (iconRes, labelRes, route) ->
                        DrawerMenuItem(
                            icon = painterResource(iconRes),
                            label = stringResource(labelRes),
                            onClick = { scope.launch { drawerState.close() }; onNavigate(route) }
                        )
                    }
                    AppDivider(modifier = Modifier.padding(vertical = 4.dp))
                    listOf(
                        Triple(
                            R.drawable.ic_promotion_24dp,
                            R.string.title_pref_promotion,
                            "promotion"
                        ),
                        Triple(R.drawable.ic_logcat_24dp, R.string.title_logcat, "logcat"),
                        Triple(
                            R.drawable.ic_check_update_24dp,
                            R.string.update_check_for_update,
                            "check_update"
                        ),
                        Triple(
                            R.drawable.ic_restore_24dp,
                            R.string.title_configuration_backup_restore,
                            "backup_restore"
                        ),
                        Triple(R.drawable.ic_about_24dp, R.string.title_about, "about"),
                    ).forEach { (iconRes, labelRes, route) ->
                        DrawerMenuItem(
                            icon = painterResource(iconRes),
                            label = stringResource(labelRes),
                            onClick = { scope.launch { drawerState.close() }; onNavigate(route) }
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
            topBar = {
                AppTopBar(
                    title = stringResource(R.string.title_server),
                    onBackClick = {},
                    isLoading = isLoading,
                    isSearchActive = showSearch,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { query ->
                        searchQuery = query
                        mainViewModel.filterConfig(query)
                    },
                    onSearchClose = {
                        searchQuery = ""
                        mainViewModel.filterConfig("")
                        showSearch = false
                    },
                    searchPlaceholder = stringResource(R.string.menu_item_search),
                    navigationIcon = {
                        if (showSearch) {
                            IconButton(onClick = {
                                searchQuery = ""
                                mainViewModel.filterConfig("")
                                showSearch = false
                            }) {
                                Icon(
                                    painterResource(R.drawable.ic_arrow_back_24dp),
                                    contentDescription = "Back"
                                )
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    painterResource(R.drawable.ic_menu_24dp),
                                    contentDescription = "Menu"
                                )
                            }
                        }
                    },
                    actions = {
                        if (!showSearch) {
                            IconButton(onClick = { showSearch = true }) {
                                Icon(
                                    painterResource(R.drawable.ic_outline_filter_alt_24),
                                    contentDescription = "filter"
                                )
                            }
                        }
                        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                            IconButton(onClick = { showImportMenu = true }) {
                                Icon(
                                    painterResource(R.drawable.ic_add_24dp),
                                    contentDescription = "Add"
                                )
                            }
                            DropdownMenu(
                                expanded = showImportMenu,
                                onDismissRequest = { showImportMenu = false },
                                scrollState = importMenuScrollState,
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .heightIn(max = maxMenuHeight)
                                    .verticalScrollbar(importMenuScrollState)
                            ) {
                                listOf(
                                    R.string.menu_item_import_config_qrcode to {
                                        showImportMenu = false; onImportQRcode()
                                    },
                                    R.string.menu_item_import_config_clipboard to {
                                        showImportMenu = false; onImportClipboard()
                                    },
                                    R.string.menu_item_import_config_local to {
                                        showImportMenu = false; onImportLocal()
                                    },
                                    R.string.menu_item_import_config_policy_group to {
                                        showImportMenu = false; onImportManually(EConfigType.POLICYGROUP.value)
                                    },
                                    R.string.menu_item_import_config_proxy_chain to {
                                        showImportMenu = false; onImportManually(EConfigType.PROXYCHAIN.value)
                                    },
                                    R.string.menu_item_import_config_manually_vmess to {
                                        showImportMenu = false; onImportManually(EConfigType.VMESS.value)
                                    },
                                    R.string.menu_item_import_config_manually_vless to {
                                        showImportMenu = false; onImportManually(EConfigType.VLESS.value)
                                    },
                                    R.string.menu_item_import_config_manually_ss to {
                                        showImportMenu = false; onImportManually(EConfigType.SHADOWSOCKS.value)
                                    },
                                    R.string.menu_item_import_config_manually_socks to {
                                        showImportMenu = false; onImportManually(EConfigType.SOCKS.value)
                                    },
                                    R.string.menu_item_import_config_manually_http to {
                                        showImportMenu = false; onImportManually(EConfigType.HTTP.value)
                                    },
                                    R.string.menu_item_import_config_manually_trojan to {
                                        showImportMenu = false; onImportManually(EConfigType.TROJAN.value)
                                    },
                                    R.string.menu_item_import_config_manually_wireguard to {
                                        showImportMenu = false; onImportManually(EConfigType.WIREGUARD.value)
                                    },
                                    R.string.menu_item_import_config_manually_hysteria2 to {
                                        showImportMenu = false; onImportManually(EConfigType.HYSTERIA2.value)
                                    },
                                ).forEach { (stringRes, action) ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(stringRes)) },
                                        onClick = action
                                    )
                                }
                            }
                        }
                        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    painterResource(R.drawable.ic_more_vert_24dp),
                                    contentDescription = null
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                scrollState = moreMenuScrollState,
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .heightIn(max = maxMenuHeight)
                                    .verticalScrollbar(moreMenuScrollState)
                            ) {
                                listOf(
                                    R.string.title_service_restart to {
                                        showMenu = false; onRestartService()
                                    },
                                    R.string.title_del_all_config to {
                                        showMenu = false; showDelAllConfirm = true
                                    },
                                    R.string.title_del_duplicate_config to {
                                        showMenu = false; showDelDuplicateConfirm = true
                                    },
                                    R.string.title_del_invalid_config to {
                                        showMenu = false; showDelInvalidConfirm = true
                                    },
                                    R.string.title_export_all to {
                                        showMenu = false; onExportAll()
                                    },
                                    R.string.title_real_ping_all_server to {
                                        showMenu = false; onRealPingAll()
                                    },
                                    R.string.title_locate_selected_config to {
                                        showMenu = false; onLocateSelectedServer()
                                    },
                                    R.string.title_sort_by_test_results to {
                                        showMenu = false; onSortByTestResults()
                                    },
                                    R.string.title_sub_update to {
                                        showMenu = false; onSubUpdate()
                                    },
                                ).forEach { (stringRes, action) ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(stringRes)) },
                                        onClick = action
                                    )
                                }
                            }
                        }
                    }
                )
            },
            bottomBar = {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppDivider()
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .height(64.dp)
                                .clickable(onClick = onTestClick),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = displayText,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    FloatingActionButton(
                        onClick = onFabClick,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 24.dp)
                            .offset(y = (-28).dp)
                            .navigationBarsPadding(),
                        containerColor = if (isRunning) colorFabActive
                        else if (isDarkTheme) colorFabInactiveDark
                        else colorFabInactiveLight
                    ) {
                        Icon(
                            painter = if (isRunning) painterResource(R.drawable.ic_stop_24dp)
                            else painterResource(R.drawable.ic_play_24dp),
                            contentDescription = if (isRunning) "Stop" else "Start",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            floatingActionButton = {},
        ) { innerPadding ->
            val layoutDirection = LocalLayoutDirection.current
            val topPadding = innerPadding.calculateTopPadding()
            val startPadding = innerPadding.calculateStartPadding(layoutDirection)
            val endPadding = innerPadding.calculateEndPadding(layoutDirection)

            Column(modifier = Modifier.fillMaxSize()) {
                if (groups.size > 1) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage.coerceIn(
                            0, (groups.size - 1).coerceAtLeast(0)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = topPadding),
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        edgePadding = 0.dp,
                        minTabWidth = 56.dp,
                        indicator = {
                            TabRowDefaults.PrimaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(
                                    selectedTabIndex = pagerState.currentPage,
                                    matchContentSize = true
                                )
                                    .clip(RoundedCornerShape(3.dp)),
                                width = Dp.Unspecified,
                                color = colorFabActive
                            )
                        },
                        divider = { }
                    ) {
                        groups.forEachIndexed { index, group ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                },
                                text = {
                                    val count = perGroupServers[group.id]?.size ?: 0
                                    if (group.id.isEmpty()) {
                                        Text(group.remarks)
                                    } else {
                                        Text("${group.remarks} ($count)")
                                    }
                                }
                            )
                        }
                    }
                }

                if (groups.isNotEmpty()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = true,
                        beyondViewportPageCount = 1,
                        key = { page -> groups.getOrNull(page)?.id ?: page }
                    ) { page ->
                        val groupId = groups.getOrNull(page)?.id ?: ""
                        val servers = perGroupServers[groupId] ?: emptyList()
                        val canReorder = groupId.isNotEmpty() && searchQuery.isEmpty()

                        ServerListPage(
                            servers = servers,
                            selectedGuid = selectedGuid,
                            canReorder = canReorder,
                            doubleColumnDisplay = doubleColumnDisplay,
                            subscriptionId = groupId,
                            confirmRemove = confirmRemove,
                            page = page,
                            lazyListStates = lazyListStates,
                            lazyGridStates = lazyGridStates,
                            onSelectServer = onSelectServer,
                            onEditServer = onEditServer,
                            onShareServer = { guid, profile ->
                                shareTarget = Triple(guid, profile, false)
                            },
                            onMoreServer = { guid, profile ->
                                shareTarget = Triple(guid, profile, true)
                            },
                            onRemoveServer = { guid ->
                                if (confirmRemove) showRemoveConfirm = guid
                                else onRemoveServer(guid)
                            },
                            onSwapServer = mainViewModel::swapServer,
                            contentPadding = PaddingValues(
                                start = startPadding,
                                top = if (groups.size > 1) 0.dp else topPadding,
                                end = endPadding,
                                bottom = 80.dp
                            )
                        )
                    }
                }
            }
        }
    }
}

private suspend fun scrollToPositionWithOffset(listState: LazyListState, targetIndex: Int) {
    listState.scrollToItem(targetIndex, 0)

    val layoutInfo = listState.layoutInfo
    val viewportHeight = layoutInfo.viewportSize.height
    val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }

    if (targetItem != null) {
        val desiredOffset = viewportHeight / 3
        val scrollOffset = -desiredOffset
        listState.scrollToItem(targetIndex, scrollOffset)
    }
}

private suspend fun scrollToPositionWithOffset(gridState: LazyGridState, targetIndex: Int) {
    gridState.scrollToItem(targetIndex, 0)

    val layoutInfo = gridState.layoutInfo
    val viewportHeight = layoutInfo.viewportSize.height
    val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }

    if (targetItem != null) {
        val desiredOffset = viewportHeight / 3
        val scrollOffset = -desiredOffset
        gridState.scrollToItem(targetIndex, scrollOffset)
    }
}

@Composable
private fun ServerListPage(
    servers: List<ServersCache>,
    selectedGuid: String?,
    canReorder: Boolean,
    doubleColumnDisplay: Boolean,
    subscriptionId: String,
    confirmRemove: Boolean,
    page: Int,
    lazyListStates: MutableMap<Int, LazyListState>,
    lazyGridStates: MutableMap<Int, LazyGridState>,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit,
    onSwapServer: (Int, Int) -> Unit,
    contentPadding: PaddingValues
) {
    if (doubleColumnDisplay) {
        val gridState = rememberLazyGridState()
        LaunchedEffect(gridState) { lazyGridStates[page] = gridState }
        DisposableEffect(page) { onDispose { lazyGridStates.remove(page) } }

        val reorderableGridState = if (canReorder) {
            rememberReorderableLazyGridState(gridState) { from, to ->
                onSwapServer(from.index, to.index)
            }
        } else null

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollbar(gridState),
            contentPadding = contentPadding
        ) {
            itemsIndexed(items = servers, key = { _, item -> item.guid }) { _, serverCache ->
                val content: @Composable () -> Unit = {
                    ServerItemColumn(
                        serverCache = serverCache,
                        selectedGuid = selectedGuid,
                        subscriptionId = subscriptionId,
                        doubleColumnDisplay = true,
                        onSelectServer = onSelectServer,
                        onEditServer = onEditServer,
                        onShareServer = onShareServer,
                        onMoreServer = onMoreServer,
                        onRemoveServer = onRemoveServer
                    )
                }
                if (canReorder && reorderableGridState != null) {
                    ReorderableItem(
                        reorderableGridState,
                        key = serverCache.guid
                    ) { isDragging ->
                        ReorderableGridItem(
                            scope = this,
                            isDragging = isDragging
                        ) { content() }
                    }
                } else {
                    content()
                }
            }
        }
    } else {
        val listState = rememberLazyListState()
        LaunchedEffect(listState) { lazyListStates[page] = listState }
        DisposableEffect(page) { onDispose { lazyListStates.remove(page) } }

        val reorderableState = if (canReorder) {
            rememberReorderableLazyListState(listState) { from, to ->
                onSwapServer(from.index, to.index)
            }
        } else null

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollbar(listState),
            contentPadding = contentPadding
        ) {
            itemsIndexed(items = servers, key = { _, item -> item.guid }) { _, serverCache ->
                if (canReorder && reorderableState != null) {
                    ReorderableItem(
                        reorderableState,
                        key = serverCache.guid
                    ) { isDragging ->
                        ReorderableListItem(
                            scope = this,
                            isDragging = isDragging
                        ) {
                            ServerItemRow(
                                serverCache = serverCache,
                                selectedGuid = selectedGuid,
                                subscriptionId = subscriptionId,
                                onSelectServer = onSelectServer,
                                onEditServer = onEditServer,
                                onShareServer = onShareServer,
                                onMoreServer = onMoreServer,
                                onRemoveServer = onRemoveServer
                            )
                        }
                        AppDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    }
                } else {
                    ServerItemRow(
                        serverCache = serverCache,
                        selectedGuid = selectedGuid,
                        subscriptionId = subscriptionId,
                        onSelectServer = onSelectServer,
                        onEditServer = onEditServer,
                        onShareServer = onShareServer,
                        onMoreServer = onMoreServer,
                        onRemoveServer = onRemoveServer
                    )
                    AppDivider(modifier = Modifier.padding(horizontal = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun ServerItemRow(
    serverCache: ServersCache,
    selectedGuid: String?,
    subscriptionId: String,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit
) {
    val profile = serverCache.profile
    val subRemarks = if (subscriptionId.isEmpty()) {
        MmkvManager.decodeSubscription(profile.subscriptionId)?.remarks?.firstOrNull()
            ?.toString() ?: ""
    } else ""

    ServerListItem(
        remarks = profile.remarks,
        statistics = profile.description.nullIfBlank()
            ?: AngConfigManager.generateDescription(profile),
        typeDescription = getProtocolDescription(profile),
        testResult = serverCache.testDelayString,
        testDelayMillis = serverCache.testDelayMillis,
        isSelected = serverCache.guid == selectedGuid,
        subscriptionRemarks = subRemarks,
        doubleColumnDisplay = false,
        onClick = { onSelectServer(serverCache.guid) },
        onShare = { onShareServer(serverCache.guid, profile) },
        onEdit = { onEditServer(serverCache.guid, profile) },
        onRemove = { onRemoveServer(serverCache.guid) },
        onMore = { onMoreServer(serverCache.guid, profile) }
    )
}

@Composable
private fun ServerItemColumn(
    serverCache: ServersCache,
    selectedGuid: String?,
    subscriptionId: String,
    doubleColumnDisplay: Boolean,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit
) {
    val profile = serverCache.profile
    val subRemarks = if (subscriptionId.isEmpty()) {
        MmkvManager.decodeSubscription(profile.subscriptionId)?.remarks?.firstOrNull()?.toString() ?: ""
    } else ""

    Column {
        ServerListItem(
            remarks = profile.remarks,
            statistics = profile.description.nullIfBlank() ?: AngConfigManager.generateDescription(profile),
            typeDescription = getProtocolDescription(profile),
            testResult = serverCache.testDelayString,
            testDelayMillis = serverCache.testDelayMillis,
            isSelected = serverCache.guid == selectedGuid,
            subscriptionRemarks = subRemarks,
            doubleColumnDisplay = doubleColumnDisplay,
            onClick = { onSelectServer(serverCache.guid) },
            onEdit = { onEditServer(serverCache.guid, profile) },
            onShare = { onShareServer(serverCache.guid, profile) },
            onRemove = { onRemoveServer(serverCache.guid) },
            onMore = { onMoreServer(serverCache.guid, profile) }
        )
        AppDivider(modifier = Modifier.padding(horizontal = 12.dp))
    }
}

@Composable
fun ServerListItem(
    remarks: String,
    statistics: String,
    typeDescription: String,
    testResult: String,
    testDelayMillis: Long,
    isSelected: Boolean,
    subscriptionRemarks: String,
    doubleColumnDisplay: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onClick).then(dragModifier)
    ) {
        Box(Modifier.width(10.dp).fillMaxHeight()) {
            if (isSelected) {
                Row {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.width(4.dp).fillMaxHeight().padding(vertical = 10.dp).background(colorFabActive))
                }
            }
        }

        Column(Modifier.weight(1f).padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(remarks, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Paragraph), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (doubleColumnDisplay) {
                    IconButton(onClick = onMore, Modifier.size(36.dp)) {
                        Icon(painterResource(R.drawable.ic_more_vert_24dp), null, Modifier.size(24.dp))
                    }
                } else {
                    IconButton(onClick = onShare, Modifier.size(36.dp)) { Icon(painterResource(R.drawable.ic_share_24dp), null, Modifier.size(24.dp)) }
                    IconButton(onClick = onEdit, Modifier.size(36.dp)) { Icon(painterResource(R.drawable.ic_edit_24dp), null, Modifier.size(24.dp)) }
                    IconButton(onClick = onRemove, Modifier.size(36.dp)) { Icon(painterResource(R.drawable.ic_delete_24dp), null, Modifier.size(24.dp)) }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (subscriptionRemarks.isNotBlank()) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)), Alignment.Center) {
                        Text(subscriptionRemarks.take(1).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(statistics, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(typeDescription, style = MaterialTheme.typography.bodySmall, color = colorConfigType, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(testResult, style = MaterialTheme.typography.bodySmall, color = if (testDelayMillis < 0L) colorPingRed else colorPing, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun DrawerMenuItem(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun getProtocolDescription(profile: ProfileItem): String {
    if (profile.configType.isComplexType()) return profile.configType.name
    val parts = mutableListOf(profile.configType.name)
    profile.network?.let { net ->
        if (net.isNotBlank() && !net.equals("tcp", ignoreCase = true)) parts.add(net)
    }
    profile.security?.let { sec ->
        if (sec.isNotBlank()) {
            if (profile.insecure == true && sec.equals("tls", ignoreCase = true)) {
                parts.add("$sec insecure")
            } else {
                parts.add(sec)
            }
        }
    }
    return parts.joinToString(" / ")
}
