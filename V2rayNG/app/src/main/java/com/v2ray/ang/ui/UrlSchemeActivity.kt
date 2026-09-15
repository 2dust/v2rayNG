package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil

class UrlSchemeActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mainIntent = Intent(this, MainActivity::class.java)
        try {
            intent.importConfigText()?.let {
                mainIntent.putExtra(MainActivity.EXTRA_IMPORT_CONFIG, it)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Error processing URL scheme", e)
            toastError(R.string.toast_failure)
        }
        startActivity(mainIntent)
        finish()
    }

    @Composable
    override fun ScreenContent() {
    }
}

internal fun Intent.importConfigText(): String? = when (action) {
    Intent.ACTION_SEND -> if (type == "text/plain") getStringExtra(Intent.EXTRA_TEXT) else null
    Intent.ACTION_VIEW -> {
        val uri = requireNotNull(data)
        require(uri.host == "install-config" || uri.host == "install-sub")
        // getQueryParameter already decodes the outer URL. Preserve the contained URL's escapes.
        val url = uri.getQueryParameter("url")
        val fragment = uri.encodedFragment
        when {
            url.isNullOrEmpty() -> null
            url.substringAfter('#', "").isEmpty() && !fragment.isNullOrEmpty() ->
                "${url.substringBefore('#')}#$fragment"
            else -> url
        }
    }
    else -> null
}
