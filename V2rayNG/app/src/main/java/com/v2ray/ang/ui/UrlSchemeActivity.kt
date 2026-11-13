package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

class UrlSchemeActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val imported = importFromIntent(intent)
            startActivity(Intent(this@UrlSchemeActivity, MainActivity::class.java).apply {
                if (imported) putExtra(MainActivity.EXTRA_REFRESH_AFTER_IMPORT, true)
            })
            finish()
        }
    }

    @Composable
    override fun ScreenContent() {
    }

    private suspend fun importFromIntent(source: Intent): Boolean {
        val uri = source.data
        val text = when (source.action) {
            Intent.ACTION_SEND -> source.getStringExtra(Intent.EXTRA_TEXT).takeIf { source.type == "text/plain" }
            Intent.ACTION_VIEW -> when (uri?.host) {
                "install-config", "install-sub" -> uri.getQueryParameter("url")
                else -> {
                    toastError(R.string.toast_failure)
                    return false
                }
            }
            else -> null
        }
        if (text.isNullOrEmpty()) return false

        val imported = withContext(Dispatchers.IO) {
            val decoded = try {
                URLDecoder.decode(text, "UTF-8")
            } catch (e: IllegalArgumentException) {
                LogUtil.e(AppConfig.TAG, "Malformed encoding in external import", e)
                return@withContext false
            }
            val fragment = uri?.fragment.takeIf { source.action == Intent.ACTION_VIEW }
            val config = if (!fragment.isNullOrEmpty() && Uri.parse(decoded).fragment.isNullOrEmpty()) {
                "$decoded#$fragment"
            } else decoded
            val (profiles, subscriptions) = AngConfigManager.importBatchConfig(config, "", false)
            profiles > 0 || subscriptions > 0
        }
        toast(if (imported) R.string.import_subscription_success else R.string.import_subscription_failure)
        return imported
    }
}
