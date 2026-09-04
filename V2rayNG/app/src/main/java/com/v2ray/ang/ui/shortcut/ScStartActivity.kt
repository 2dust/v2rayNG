package com.v2ray.ang.ui.shortcut

import android.net.VpnService
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScStartActivity : BaseComponentActivity() {
    private var permissionPending = false
    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            permissionPending = false
            if (result.resultCode == RESULT_OK) prepareAndStartService() else finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionPending = savedInstanceState?.getBoolean(STATE_PERMISSION_PENDING) ?: false
        if (!permissionPending) prepareAndStartService()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_PERMISSION_PENDING, permissionPending)
        super.onSaveInstanceState(outState)
    }

    @Composable
    override fun ScreenContent() = Unit

    private fun prepareAndStartService() {
        lifecycleScope.launch {
            try {
                val permissionIntent = withContext(Dispatchers.IO) {
                    if (CoreServiceManager.isRunning()) return@withContext null
                    val permission = if (!SettingsManager.isRootMode() && SettingsManager.isVpnMode()) {
                        VpnService.prepare(this@ScStartActivity)
                    } else null
                    if (permission == null) LauncherManager.startServiceFromToggle(this@ScStartActivity)
                    permission
                }
                if (permissionIntent != null) {
                    permissionPending = true
                    requestVpnPermission.launch(permissionIntent)
                } else finish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e("ScStartActivity", "Failed to start service from shortcut or widget", e)
                finish()
            }
        }
    }

    companion object {
        private const val STATE_PERMISSION_PENDING = "permission_pending"
    }
}
