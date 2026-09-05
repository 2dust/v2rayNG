package com.v2ray.ang.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import java.io.File

/**
 * Shares a file from the app cache through the system chooser.
 *
 * This is a platform capability, so it is invoked from the UI layer only; ViewModels merely
 * describe the intent through a [com.v2ray.ang.ui.base.BaseEvent.Platform] event and receive
 * the outcome back as an Action, keeping the unified toast pipeline intact.
 */
object ShareUtil {

    private const val CACHE_AUTHORITY_SUFFIX = ".cache"

    /**
     * @return true when a chooser was launched, false when the file could not be exposed.
     */
    fun shareFile(context: Context, file: File, mimeType: String, title: String): Boolean = try {
        val uri = FileProvider.getUriForFile(
            context,
            BuildConfig.APPLICATION_ID + CACHE_AUTHORITY_SUFFIX,
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            putExtra(Intent.EXTRA_TITLE, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
        }
        context.startActivity(Intent.createChooser(intent, title))
        true
    } catch (e: Exception) {
        LogUtil.e(AppConfig.TAG, "Failed to share file: ${file.name}", e)
        false
    }
}
