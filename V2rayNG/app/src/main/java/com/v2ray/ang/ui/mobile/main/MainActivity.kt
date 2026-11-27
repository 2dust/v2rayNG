package com.v2ray.ang.ui.mobile.main

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.mobile.AboutActivity
import com.v2ray.ang.ui.mobile.BackupActivity
import com.v2ray.ang.ui.mobile.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.mobile.CheckUpdateActivity
import com.v2ray.ang.ui.mobile.LogcatActivity
import com.v2ray.ang.vm.MainViewModel
import com.v2ray.ang.ui.mobile.PerAppProxyActivity
import com.v2ray.ang.ui.mobile.RoutingSettingActivity
import com.v2ray.ang.ui.mobile.server.ProfileEditorResult
import com.v2ray.ang.ui.mobile.server.ServerCustomConfigActivity
import com.v2ray.ang.ui.mobile.server.ServerGroupActivity
import com.v2ray.ang.ui.mobile.server.ServerHttpActivity
import com.v2ray.ang.ui.mobile.server.ServerHysteria2Activity
import com.v2ray.ang.ui.mobile.server.ServerMasqueActivity
import com.v2ray.ang.ui.mobile.server.ServerProxyChainActivity
import com.v2ray.ang.ui.mobile.server.ServerShadowsocksActivity
import com.v2ray.ang.ui.mobile.server.ServerSocksActivity
import com.v2ray.ang.ui.mobile.server.ServerTrojanActivity
import com.v2ray.ang.ui.mobile.server.ServerVlessActivity
import com.v2ray.ang.ui.mobile.server.ServerVmessActivity
import com.v2ray.ang.ui.mobile.server.ServerWireguardActivity
import com.v2ray.ang.ui.mobile.SettingsActivity
import com.v2ray.ang.ui.mobile.main.component.MainDestination
import com.v2ray.ang.ui.mobile.main.component.MainScreen
import com.v2ray.ang.ui.mobile.SubSettingActivity
import com.v2ray.ang.ui.mobile.UserAssetActivity
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils

open class MainActivity : HelperBaseComponentActivity() {

    protected val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application)
    }

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) startV2Ray()
        }

    private val profileEditorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            val action = data.getStringExtra(ProfileEditorResult.EXTRA_ACTION)
                ?: return@registerForActivityResult
            if (action != ProfileEditorResult.ACTION_SAVED &&
                action != ProfileEditorResult.ACTION_DELETED
            ) return@registerForActivityResult
            val restartService = data.getBooleanExtra(
                ProfileEditorResult.EXTRA_RESTART_SERVICE, false
            )
            val selectedProfileSaved = action == ProfileEditorResult.ACTION_SAVED &&
                    data.getStringExtra(ProfileEditorResult.EXTRA_GUID) == mainViewModel.uiState.value.selectedGuid
            mainViewModel.onAction(MainAction.RefreshGroups)
            if (restartService || selectedProfileSaved) LauncherManager.restartService(this)
        }

    private val settingsActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val restartService = SettingsChangeManager.consumeRestartService()
            val refreshGroups = SettingsChangeManager.consumeSetupGroupTab()
            mainViewModel.refreshUiSettings()
            if (refreshGroups) mainViewModel.onAction(MainAction.RefreshGroups)
            if (restartService) LauncherManager.restartService(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel.onAction(MainAction.Initialize)

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(AppConfig.EXTRA_REFRESH_AFTER_IMPORT, false)) {
            mainViewModel.onAction(MainAction.RefreshGroups)
        }
    }

    @Composable
    override fun ScreenContent() {
        BackHandler { moveTaskToBack(false) }
        MainContent(mainViewModel, ::handleMainAction, ::navigateTo)
    }

    @Composable
    protected open fun MainContent(
        mainViewModel: MainViewModel,
        onAction: (MainAction) -> Unit,
        onNavigate: (MainDestination) -> Unit
    ) {
        MainScreen(mainViewModel, onAction, onNavigate)
    }

    private fun handleMainAction(action: MainAction) {
        when (action) {
            MainAction.ToggleService -> handleFabAction()
            MainAction.TestCurrentServer -> handleLayoutTestClick()
            MainAction.ImportQRcode -> importQRcode()
            MainAction.ImportConfigLocal -> importConfigLocal()
            is MainAction.ImportManually -> importManually(action.type)
            MainAction.RestartService -> LauncherManager.restartServiceOrStart(this, ::requestServiceStart)
            MainAction.LocateSelectedServer -> mainViewModel.triggerLocateSelectedServer()
            MainAction.Exit -> {
                LauncherManager.stopService(this)
                finishAndRemoveTask()
            }
            is MainAction.SelectServer -> setSelectServer(action.guid)
            is MainAction.EditServer -> editServer(action.guid, action.profile)
            else -> mainViewModel.onAction(action)
        }
    }

    private fun navigateTo(destination: MainDestination) {
        val intent = when (destination) {
            MainDestination.Subscriptions -> Intent(this, SubSettingActivity::class.java)
            MainDestination.PerAppProxy -> Intent(this, PerAppProxyActivity::class.java)
            MainDestination.Routing -> Intent(this, RoutingSettingActivity::class.java)
            MainDestination.UserAssets -> Intent(this, UserAssetActivity::class.java)
            MainDestination.Settings -> Intent(this, SettingsActivity::class.java)
            MainDestination.Logcat -> Intent(this, LogcatActivity::class.java)
            MainDestination.CheckUpdate -> Intent(this, CheckUpdateActivity::class.java)
            MainDestination.BackupRestore -> Intent(this, BackupActivity::class.java)
            MainDestination.About -> Intent(this, AboutActivity::class.java)
            MainDestination.Promotion -> {
                Utils.openUri(
                    this,
                    "${Utils.decode(AppConfig.APP_PROMOTION_URL)}?t=${System.currentTimeMillis()}"
                )
                return
            }
        }
        settingsActivityLauncher.launch(intent)
    }

    private fun handleFabAction() {
        if (mainViewModel.uiState.value.isRunning) {
            LauncherManager.stopService(this)
        } else {
            requestServiceStart()
        }
    }

    private fun requestServiceStart() {
        if (!SettingsManager.isVpnMode()) {
            startV2Ray()
            return
        }
        val intent = VpnService.prepare(this)
        if (intent == null) startV2Ray() else requestVpnPermission.launch(intent)
    }

    private fun handleLayoutTestClick() {
        if (mainViewModel.uiState.value.isRunning) {
            mainViewModel.testCurrentServerRealPing()
        }
    }

    private fun startV2Ray() {
        if (mainViewModel.uiState.value.selectedGuid.isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN
        ) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }
        LauncherManager.startService(this)
    }

    private fun importManually(createConfigType: Int) {
        val intent = when (createConfigType) {
            EConfigType.POLICYGROUP.value -> Intent(this, ServerGroupActivity::class.java)
            EConfigType.PROXYCHAIN.value -> Intent(this, ServerProxyChainActivity::class.java)
            EConfigType.VMESS.value -> Intent(this, ServerVmessActivity::class.java)
            EConfigType.VLESS.value -> Intent(this, ServerVlessActivity::class.java)
            EConfigType.SHADOWSOCKS.value -> Intent(this, ServerShadowsocksActivity::class.java)
            EConfigType.SOCKS.value -> Intent(this, ServerSocksActivity::class.java)
            EConfigType.HTTP.value -> Intent(this, ServerHttpActivity::class.java)
            EConfigType.TROJAN.value -> Intent(this, ServerTrojanActivity::class.java)
            EConfigType.WIREGUARD.value -> Intent(this, ServerWireguardActivity::class.java)
            EConfigType.HYSTERIA2.value -> Intent(this, ServerHysteria2Activity::class.java)
            EConfigType.MASQUE.value -> Intent(this, ServerMasqueActivity::class.java)
            else -> Intent(this, ServerHttpActivity::class.java).apply {
                putExtra("createConfigType", createConfigType)
            }
        }.apply {
            putExtra("subscriptionId", mainViewModel.uiState.value.selectedGroupId)
        }
        profileEditorLauncher.launch(intent)
    }

    private fun importQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                mainViewModel.onAction(MainAction.ImportBatchConfig(scanResult))
            }
        }
    }

    private fun importConfigLocal() {
        launchFileChooser { uri ->
            if (uri == null) return@launchFileChooser
            try {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    mainViewModel.onAction(MainAction.ImportBatchConfig(reader.readText()))
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to read content from URI", e)
            }
        }
    }

    private fun editServer(guid: String, profile: ProfileItem) {
        val activityClass = when (profile.configType) {
            EConfigType.CUSTOM, EConfigType.CUSTOM_OUTBOUND -> ServerCustomConfigActivity::class.java
            EConfigType.POLICYGROUP -> ServerGroupActivity::class.java
            EConfigType.PROXYCHAIN -> ServerProxyChainActivity::class.java
            EConfigType.VMESS -> ServerVmessActivity::class.java
            EConfigType.VLESS -> ServerVlessActivity::class.java
            EConfigType.SHADOWSOCKS -> ServerShadowsocksActivity::class.java
            EConfigType.SOCKS -> ServerSocksActivity::class.java
            EConfigType.HTTP -> ServerHttpActivity::class.java
            EConfigType.TROJAN -> ServerTrojanActivity::class.java
            EConfigType.WIREGUARD -> ServerWireguardActivity::class.java
            EConfigType.HYSTERIA2 -> ServerHysteria2Activity::class.java
            EConfigType.MASQUE -> ServerMasqueActivity::class.java
            else -> ServerHttpActivity::class.java
        }
        val intent = Intent(this, activityClass).apply {
            putExtra("guid", guid)
            putExtra("isRunning", mainViewModel.uiState.value.isRunning)
            putExtra("createConfigType", profile.configType.value)
            putExtra("subscriptionId", mainViewModel.uiState.value.selectedGroupId)
        }
        profileEditorLauncher.launch(intent)
    }

    private fun setSelectServer(guid: String) {
        val selected = mainViewModel.uiState.value.selectedGuid
        if (guid != selected) {
            mainViewModel.updateSelectedGuid(guid)
            LauncherManager.restartService(this)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
