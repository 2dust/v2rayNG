package com.v2ray.ang.ui.main

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.extension.delay
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.repository.MainRepository
import com.v2ray.ang.ui.base.BaseHelperActivity
import com.v2ray.ang.ui.base.baseViewModels
import kotlinx.coroutines.launch

class MainActivity : BaseHelperActivity() {

    private val viewModel: MainViewModel by baseViewModels { app, _ ->
        MainViewModel(MainRepository(app))
    }

    private var pendingLocalNetwork = false

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) launchCore(pendingLocalNetwork)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermission(PermissionType.POST_NOTIFICATIONS) {}
        viewModel.onAction(MainAction.Initialize)
    }

    @Composable
    override fun ScreenContent() = MainScreen(
        viewModel = viewModel,
        onPlatformEvent = ::handlePlatformEvent,
    )

    private fun handlePlatformEvent(event: MainEvent): Boolean = when (event) {
        is MainEvent.StartService -> {
            startCore(event.requireVpnPermission, event.requireLocalNetwork); true
        }
        MainEvent.StopService -> {
            LauncherManager.stopService(this); true
        }
        is MainEvent.RestartService -> {
            if (event.stopFirst) LauncherManager.stopService(this)
            lifecycleScope.launch {
                delay(500)
                startCore(event.requireVpnPermission, event.requireLocalNetwork)
            }
            true
        }
        MainEvent.ScanQrCode -> {
            scanQrCode { text ->
                if (!text.isNullOrBlank()) viewModel.onAction(MainAction.ImportBatchConfig(text))
            }
            true
        }
        MainEvent.PickConfigFile -> {
            pickFile { uri -> uri?.let { viewModel.onAction(MainAction.ConfigFileSelected(it)) } }
            true
        }
        is MainEvent.ShowQrCode -> false
        is MainEvent.LocateProfile -> false
    }

    private fun startCore(requireVpnPermission: Boolean, requireLocalNetwork: Boolean) {
        if (!requireVpnPermission) return launchCore(requireLocalNetwork)
        val intent = VpnService.prepare(this)
        if (intent == null) {
            launchCore(requireLocalNetwork)
        } else {
            pendingLocalNetwork = requireLocalNetwork
            vpnPermission.launch(intent)
        }
    }

    private fun launchCore(requireLocalNetwork: Boolean) {
        if (requireLocalNetwork && Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            requestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }
        LauncherManager.startService(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.onAction(MainAction.RefreshGroups)
    }
}
