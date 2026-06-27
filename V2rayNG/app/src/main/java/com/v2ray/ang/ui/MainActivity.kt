package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog as ComposeAlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.GravityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.isComplexType
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : HelperBaseActivity() {
    val mainViewModel: MainViewModel by viewModels()
    private val groupsState = MutableStateFlow<List<com.v2ray.ang.dto.GroupMapItem>>(emptyList())

    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        } else {
            applyRunningState(isLoading = false, isRunning = mainViewModel.isRunning.value == true)
        }
    }
    private val requestActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (SettingsChangeManager.consumeRestartService() && mainViewModel.isRunning.value == true) {
            restartV2Ray()
        }
        if (SettingsChangeManager.consumeSetupGroupTab()) {
            setupGroupTab()
        }
    }

    private var qrCodeGuid = mutableStateOf<String?>(null)

    fun showQRCode(guid: String) {
        qrCodeGuid.value = guid
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val groups by groupsState.collectAsStateWithLifecycle()
                val isRunning by mainViewModel.isRunningFlow.collectAsStateWithLifecycle()
                val qrGuid by qrCodeGuid
                
                MainScreen(
                    mainViewModel = mainViewModel,
                    groups = groups,
                    isRunning = isRunning,
                    onFabClick = { handleFabAction() },
                    onMenuClick = { id -> handleMenuClick(id) },
                    onDrawerItemClick = { id -> onDrawerItemClick(id) },
                    onSelectServer = { guid -> setSelectServer(guid) },
                    onMoreClick = { guid, profile, position ->
                        val isCustom = profile.configType.isComplexType()
                        val more = true 

                        val (shareOptions, skip) = if (more) {
                            val options = if (isCustom) share_method_more.asList().takeLast(3) else share_method_more.asList()
                            options to if (isCustom) 2 else 0
                        } else {
                            val options = if (isCustom) share_method.asList().takeLast(1) else share_method.asList()
                            options to if (isCustom) 2 else 0
                        }

                        shareServer(guid, profile, position, shareOptions, skip)
                    }
                )

                if (qrGuid != null) {
                    ComposeAlertDialog(
                        onDismissRequest = { qrCodeGuid.value = null },
                        confirmButton = { TextButton(onClick = { qrCodeGuid.value = null }) { Text("OK") } },
                        title = { Text("QR Code") },
                        text = {
                            val bitmap = remember(qrGuid) { AngConfigManager.share2QRCode(qrGuid!!) }
                            bitmap?.let {
                                androidx.compose.foundation.Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(300.dp).padding(16.dp)
                                )
                            }
                        }
                    )
                }
            }
        }

        setupViewModel()
        SubscriptionUpdater.sync()
        mainViewModel.reloadServerList()

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_PING_ON_START, true)) {
            mainViewModel.testAllRealPing()
        }

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {
        }
        
        setupGroupTab()
    }

    private fun handleMenuClick(id: Int) {
        when (id) {
            R.id.import_qrcode -> importQRcode()
            R.id.import_clipboard -> importClipboard()
            R.id.import_local -> importConfigLocal()
            R.id.import_manually_policy_group -> importManually(EConfigType.POLICYGROUP.value)
            R.id.import_manually_proxy_chain -> importManually(EConfigType.PROXYCHAIN.value)
            R.id.import_manually_vmess -> importManually(EConfigType.VMESS.value)
            R.id.import_manually_vless -> importManually(EConfigType.VLESS.value)
            R.id.import_manually_ss -> importManually(EConfigType.SHADOWSOCKS.value)
            R.id.import_manually_socks -> importManually(EConfigType.SOCKS.value)
            R.id.import_manually_http -> importManually(EConfigType.HTTP.value)
            R.id.import_manually_trojan -> importManually(EConfigType.TROJAN.value)
            R.id.import_manually_wireguard -> importManually(EConfigType.WIREGUARD.value)
            R.id.import_manually_hysteria2 -> importManually(EConfigType.HYSTERIA2.value)
            R.id.export_all -> exportAll()
            R.id.real_ping_all -> {
                toast(getString(R.string.connection_test_testing_count, mainViewModel.serversCache.count()))
                mainViewModel.testAllRealPing()
            }
            R.id.service_restart -> restartV2Ray()
            R.id.del_all_config -> delAllConfig()
            R.id.del_duplicate_config -> delDuplicateConfig()
            R.id.del_invalid_config -> delInvalidConfig()
            R.id.sort_by_test_results -> sortByTestResults()
            R.id.sub_update -> importConfigViaSub()
            R.id.locate_selected_config -> locateSelectedServer()
        }
    }

    private fun onDrawerItemClick(id: Int) {
        when (id) {
            R.id.sub_setting -> requestActivityLauncher.launch(Intent(this, SubSettingActivity::class.java))
            R.id.per_app_proxy_settings -> requestActivityLauncher.launch(Intent(this, PerAppProxyActivity::class.java))
            R.id.routing_setting -> requestActivityLauncher.launch(Intent(this, RoutingSettingActivity::class.java))
            R.id.user_asset_setting -> requestActivityLauncher.launch(Intent(this, UserAssetActivity::class.java))
            R.id.promotion -> Utils.openUri(this, "${Utils.decode(AppConfig.APP_PROMOTION_URL)}?t=${System.currentTimeMillis()}")
            R.id.logcat -> startActivity(Intent(this, LogcatActivity::class.java))
            R.id.check_for_update -> startActivity(Intent(this, CheckUpdateActivity::class.java))
            R.id.backup_restore -> requestActivityLauncher.launch(Intent(this, BackupActivity::class.java))
            R.id.about -> startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    private fun setupViewModel() {
        mainViewModel.isRunning.observe(this) { isRunning ->
            applyRunningState(false, isRunning)
        }
        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
    }

    private fun setupGroupTab() {
        val groups = mainViewModel.getSubscriptions(this)
        groupsState.value = groups
        refreshGroupTabTitles()
    }

    fun refreshGroupTabTitles() {
        val groups = groupsState.value
        val updatedGroups = groups.map { group ->
            if (group.id.isEmpty()) {
                group
            } else {
                val count = MmkvManager.decodeServerList(group.id).size
                group.copy(remarks = "${group.remarks} ($count)")
            }
        }
        groupsState.value = updatedGroups
    }

    private val share_method: Array<out String> by lazy {
        resources.getStringArray(R.array.share_method)
    }
    private val share_method_more: Array<out String> by lazy {
        resources.getStringArray(R.array.share_method_more)
    }

    private fun shareServer(guid: String, profile: ProfileItem, position: Int, shareOptions: List<String>, skip: Int) {
        androidx.appcompat.app.AlertDialog.Builder(this).setItems(shareOptions.toTypedArray()) { _, i ->
            try {
                when (i + skip) {
                    0 -> qrCodeGuid.value = guid
                    1 -> share2Clipboard(guid)
                    2 -> shareFullContent(guid)
                    3 -> editServer(guid, profile)
                    4 -> removeServer(guid, position)
                    else -> toast("else")
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Error when sharing server", e)
            }
        }.show()
    }

    private fun share2Clipboard(guid: String) {
        if (AngConfigManager.share2Clipboard(this, guid) == 0) {
            toastSuccess(R.string.toast_success)
        } else {
            toastError(R.string.toast_failure)
        }
    }

    private fun shareFullContent(guid: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = AngConfigManager.shareFullContent2Clipboard(this@MainActivity, guid)
            launch(Dispatchers.Main) {
                if (result == 0) {
                    toastSuccess(R.string.toast_success)
                } else {
                    toastError(R.string.toast_failure)
                }
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

        val intent = Intent(this, activityClass)
            .putExtra("guid", guid)
            .putExtra("isRunning", mainViewModel.isRunning.value)
            .putExtra("createConfigType", profile.configType.value)
            .putExtra("subscriptionId", mainViewModel.subscriptionId)

        requestActivityLauncher.launch(intent)
    }

    private fun removeServer(guid: String, position: Int) {
        if (guid == MmkvManager.getSelectServer()) {
            toast(R.string.toast_action_not_allowed)
            return
        }

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE)) {
            androidx.appcompat.app.AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    removeServerSub(guid)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            removeServerSub(guid)
        }
    }

    private fun removeServerSub(guid: String) {
        mainViewModel.removeServer(guid)
        mainViewModel.updateSelectedGuid()
        refreshGroupTabTitles()
    }

    private fun setSelectServer(guid: String) {
        val selected = MmkvManager.getSelectServer()
        if (guid != selected) {
            MmkvManager.setSelectServer(guid)
            mainViewModel.updateSelectedGuid()

            if (mainViewModel.isRunning.value == true) {
                restartV2Ray()
            }
        }
    }

    private fun handleFabAction() {
        if (mainViewModel.isRunning.value != true && MmkvManager.getSelectServer().isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }

        applyRunningState(isLoading = true, isRunning = mainViewModel.isRunning.value == true)

        if (mainViewModel.isRunning.value == true) {
            CoreServiceManager.stopVService(this)
        } else if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent == null) {
                startV2Ray()
            } else {
                requestVpnPermission.launch(intent)
            }
        } else {
            startV2Ray()
        }
    }

    private fun startV2Ray() {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }
        CoreServiceManager.startVService(this)
    }

    fun restartV2Ray() {
        if (mainViewModel.isRunning.value == true) {
            CoreServiceManager.stopVService(this)
        }
        lifecycleScope.launch {
            delay(500)
            startV2Ray()
        }
    }

    private fun applyRunningState(isLoading: Boolean, isRunning: Boolean) {
        mainViewModel.setLoading(isLoading)
    }

    private fun importManually(createConfigType: Int) {
        if (createConfigType == EConfigType.POLICYGROUP.value) {
            startActivity(
                Intent()
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerGroupActivity::class.java)
            )
        } else if (createConfigType == EConfigType.PROXYCHAIN.value) {
            startActivity(
                Intent()
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerProxyChainActivity::class.java)
            )
        } else {
            startActivity(
                Intent()
                    .putExtra("createConfigType", createConfigType)
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerActivity::class.java)
            )
        }
    }

    private fun importQRcode(): Boolean {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                importBatchConfig(scanResult)
            }
        }
        return true
    }

    private fun importClipboard(): Boolean {
        try {
            val clipboard = Utils.getClipboard(this)
            importBatchConfig(clipboard)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from clipboard", e)
            return false
        }
        return true
    }

    private fun importBatchConfig(server: String?) {
        mainViewModel.setLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val (count, countSub) = AngConfigManager.importBatchConfig(server, mainViewModel.subscriptionId, true)
                delay(500L)
                withContext(Dispatchers.Main) {
                    when {
                        count > 0 -> {
                            toast(getString(R.string.title_import_config_count, count))
                            mainViewModel.reloadServerList()
                            refreshGroupTabTitles()
                        }
                        countSub > 0 -> setupGroupTab()
                        else -> toastError(R.string.toast_failure)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    toastError(R.string.toast_failure)
                }
                LogUtil.e(AppConfig.TAG, "Failed to import batch config", e)
            } finally {
                withContext(Dispatchers.Main) {
                    mainViewModel.setLoading(false)
                }
            }
        }
    }

    private fun importConfigLocal(): Boolean {
        try {
            showFileChooser()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from local file", e)
            return false
        }
        return true
    }

    fun importConfigViaSub(): Boolean {
        mainViewModel.setLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = mainViewModel.updateConfigViaSubAll()
                delay(500L)
                withContext(Dispatchers.Main) {
                    if (result.successCount + result.failureCount + result.skipCount == 0) {
                        toast(R.string.title_update_subscription_no_subscription)
                    } else if (result.successCount > 0 && result.failureCount + result.skipCount == 0) {
                        toast(getString(R.string.title_update_config_count, result.configCount))
                    } else {
                        toast(getString(R.string.title_update_subscription_result,
                            result.configCount, result.successCount, result.failureCount, result.skipCount))
                    }
                    if (result.configCount > 0) {
                        mainViewModel.reloadServerList()
                        refreshGroupTabTitles()
                        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_PING_ON_START, true)) {
                            mainViewModel.testAllRealPing()
                        }
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "importConfigViaSub", e)
            } finally {
                withContext(Dispatchers.Main) {
                    mainViewModel.setLoading(false)
                }
            }
        }
        return true
    }

    private fun exportAll() {
        mainViewModel.setLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ret = mainViewModel.exportAllServer()
                withContext(Dispatchers.Main) {
                    if (ret > 0)
                        toast(getString(R.string.title_export_config_count, ret))
                    else
                        toastError(R.string.toast_failure)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "exportAll", e)
            } finally {
                withContext(Dispatchers.Main) {
                    mainViewModel.setLoading(false)
                }
            }
        }
    }

    private fun delAllConfig() {
        androidx.appcompat.app.AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                mainViewModel.setLoading(true)
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val ret = mainViewModel.removeAllServer()
                        withContext(Dispatchers.Main) {
                            mainViewModel.reloadServerList()
                            refreshGroupTabTitles()
                            toast(getString(R.string.title_del_config_count, ret))
                        }
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "delAllConfig", e)
                    } finally {
                        withContext(Dispatchers.Main) {
                            mainViewModel.setLoading(false)
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .show()
    }

    private fun delDuplicateConfig() {
        androidx.appcompat.app.AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                mainViewModel.setLoading(true)
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val ret = mainViewModel.removeDuplicateServer()
                        withContext(Dispatchers.Main) {
                            mainViewModel.reloadServerList()
                            refreshGroupTabTitles()
                            toast(getString(R.string.title_del_duplicate_config_count, ret))
                        }
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "delDuplicateConfig", e)
                    } finally {
                        withContext(Dispatchers.Main) {
                            mainViewModel.setLoading(false)
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .show()
    }

    private fun delInvalidConfig() {
        androidx.appcompat.app.AlertDialog.Builder(this).setMessage(R.string.del_invalid_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                mainViewModel.setLoading(true)
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val ret = mainViewModel.removeInvalidServer()
                        withContext(Dispatchers.Main) {
                            mainViewModel.reloadServerList()
                            refreshGroupTabTitles()
                            toast(getString(R.string.title_del_config_count, ret))
                        }
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "delInvalidConfig", e)
                    } finally {
                        withContext(Dispatchers.Main) {
                            mainViewModel.setLoading(false)
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .show()
    }

    private fun sortByTestResults() {
        mainViewModel.setLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                mainViewModel.sortByTestResults()
                withContext(Dispatchers.Main) {
                    mainViewModel.reloadServerList()
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "sortByTestResults", e)
            } finally {
                withContext(Dispatchers.Main) {
                    mainViewModel.setLoading(false)
                }
            }
        }
    }

    private fun showFileChooser() {
        launchFileChooser { uri ->
            if (uri == null) return@launchFileChooser
            readContentFromUri(uri)
        }
    }

    private fun readContentFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri).use { input ->
                importBatchConfig(input?.bufferedReader()?.readText())
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to read content from URI", e)
        }
    }

    private fun locateSelectedServer() {
        val targetSubscriptionId = mainViewModel.findSubscriptionIdBySelect()
        if (targetSubscriptionId.isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }
        mainViewModel.subscriptionIdChanged(targetSubscriptionId)
        // Auto scrolls via State sync in MainScreen
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setupGroupTab()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
