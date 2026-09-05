package com.v2ray.ang.ui.urlscheme

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import com.v2ray.ang.repository.UrlSchemeRepository
import com.v2ray.ang.ui.base.BaseActivity
import com.v2ray.ang.ui.base.baseViewModels

class UrlSchemeActivity : BaseActivity() {

    private val viewModel: UrlSchemeViewModel by baseViewModels { _, handle ->
        UrlSchemeViewModel(handle, UrlSchemeRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dispatch(intent, fresh = false)
    }

    /** A second Intent on the same instance is a genuinely new request, never a replay. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatch(intent, fresh = true)
    }

    @Composable
    override fun ScreenContent() = UrlSchemeScreen(viewModel)

    private fun dispatch(intent: Intent?, fresh: Boolean) {
        viewModel.onAction(UrlSchemeAction.IntentReceived(intent.toUrlSchemeRequest(), fresh))
    }
}

private const val QUERY_URL = "url"

private val UNSUPPORTED_REQUEST = UrlSchemeRequest(UrlSchemeSource.UNSUPPORTED)

private fun Intent?.toUrlSchemeRequest(): UrlSchemeRequest {
    val intent = this ?: return UNSUPPORTED_REQUEST

    if (intent.action == Intent.ACTION_SEND) {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        return UrlSchemeRequest(UrlSchemeSource.SHARE, text)
    }

    if (intent.action == Intent.ACTION_VIEW) {
        val uri = intent.data ?: return UNSUPPORTED_REQUEST
        val source = urlSchemeSourceOf(uri.host)
        if (source == UrlSchemeSource.UNSUPPORTED) return UNSUPPORTED_REQUEST
        val payload = runCatching { uri.getQueryParameter(QUERY_URL) }.getOrNull().orEmpty()
        return UrlSchemeRequest(source, payload, uri.fragment.orEmpty())
    }

    return UNSUPPORTED_REQUEST
}
