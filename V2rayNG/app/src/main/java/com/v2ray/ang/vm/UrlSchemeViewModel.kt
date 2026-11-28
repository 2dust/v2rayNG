package com.v2ray.ang.vm

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

class UrlSchemeViewModel(application: Application) : BaseViewModel(application) {

    sealed interface UrlSchemeViewModelEvent : ViewModelEvent {
        data class ImportFinished(val imported: Boolean) : UrlSchemeViewModelEvent
    }

    fun importFromIntent(source: Intent) {
        viewModelScope.launch {
            val uri = source.data
            val text = when (source.action) {
                Intent.ACTION_SEND -> source.getStringExtra(Intent.EXTRA_TEXT).takeIf { source.type == "text/plain" }
                Intent.ACTION_VIEW -> when (uri?.host) {
                    "install-config", "install-sub" -> uri.getQueryParameter("url")
                    else -> {
                        toastError(R.string.toast_failure)
                        _viewModelEvent.send(UrlSchemeViewModelEvent.ImportFinished(false))
                        return@launch
                    }
                }
                else -> null
            }
            if (text.isNullOrEmpty()) {
                _viewModelEvent.send(UrlSchemeViewModelEvent.ImportFinished(false))
                return@launch
            }

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
            _viewModelEvent.send(UrlSchemeViewModelEvent.ImportFinished(imported))
        }
    }
}
