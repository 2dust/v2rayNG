package com.v2ray.ang.ui.backup

import androidx.compose.runtime.Composable
import com.v2ray.ang.R
import com.v2ray.ang.repository.BackupRepository
import com.v2ray.ang.ui.base.BaseHelperActivity
import com.v2ray.ang.ui.base.baseViewModels
import com.v2ray.ang.util.ShareUtil
import java.io.File

/**
 * Platform host of the backup/restore screen.
 */
class BackupActivity : BaseHelperActivity() {

    private val viewModel: BackupViewModel by baseViewModels { app, _ ->
        BackupViewModel(BackupRepository(app))
    }

    @Composable
    override fun ScreenContent() = BackupScreen(
        viewModel = viewModel,
        onPlatformEvent = ::handlePlatformEvent
    )

    private fun handlePlatformEvent(event: BackupEvent): Boolean = when (event) {
        is BackupEvent.CreateDocument -> {
            createDocument(event.fileName) { uri ->
                viewModel.onAction(BackupAction.ExportUriSelected(uri))
            }
            true
        }

        BackupEvent.PickFile -> {
            // Kept at the default "*/*": many file managers report archives as octet-stream.
            pickFile { uri -> viewModel.onAction(BackupAction.ImportUriSelected(uri)) }
            true
        }

        is BackupEvent.ShareFile -> {
            val started = ShareUtil.shareFile(
                context = this,
                file = File(event.path),
                mimeType = MIME_ZIP,
                title = getString(R.string.title_configuration_share)
            )
            viewModel.onAction(BackupAction.ShareResult(event.path, started))
            true
        }

        else -> false
    }

    private companion object {
        const val MIME_ZIP = "application/zip"
    }
}
