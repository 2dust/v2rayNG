package com.v2ray.ang.ui.logcat

import androidx.compose.runtime.Composable
import com.v2ray.ang.R
import com.v2ray.ang.repository.LogcatRepository
import com.v2ray.ang.ui.base.BaseActivity
import com.v2ray.ang.ui.base.baseViewModels
import com.v2ray.ang.util.ShareUtil
import java.io.File

class LogcatActivity : BaseActivity() {

    private val viewModel: LogcatViewModel by baseViewModels { app, _ ->
        LogcatViewModel(LogcatRepository(app))
    }

    @Composable
    override fun ScreenContent() = LogcatScreen(
        viewModel = viewModel,
        onPlatformEvent = ::handlePlatformEvent
    )

    /**
     * Translates the only Activity-bound capability of this screen.
     */
    private fun handlePlatformEvent(event: LogcatEvent): Boolean = when (event) {
        is LogcatEvent.ShareFile -> {
            val ok = ShareUtil.shareFile(
                context = this,
                file = File(event.path),
                mimeType = SHARE_MIME_TYPE,
                title = getString(R.string.logcat_share)
            )
            viewModel.onAction(LogcatAction.ShareFinished(ok))
            true
        }
    }

    private companion object {
        const val SHARE_MIME_TYPE = "text/plain"
    }
}
