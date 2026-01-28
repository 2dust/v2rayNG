package com.v2ray.ang.helper

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast

/**
 * Helper for choosing files using ACTION_GET_CONTENT intent.
 *
 */

class FileChooserHelper(private val activity: AppCompatActivity) {
    private var fileChooserCallback: ((Uri?) -> Unit)? = null

    private val fileChooserLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (result.resultCode == AppCompatActivity.RESULT_OK && uri != null) {
                fileChooserCallback?.invoke(uri)
            } else {
                fileChooserCallback?.invoke(null)
            }
            fileChooserCallback = null
        }

    /**
     * Launch file chooser with ACTION_GET_CONTENT intent.
     *
     * @param mimeType MIME type filter for files
     * @param onResult Callback invoked with the selected file URI (null if cancelled)
     */
    fun launch(
        mimeType: String = "*/*",
        onResult: (Uri?) -> Unit
    ) {
        fileChooserCallback = onResult

        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = mimeType
            addCategory(Intent.CATEGORY_OPENABLE)
        }

        try {
            fileChooserLauncher.launch(
                Intent.createChooser(intent, activity.getString(R.string.title_file_chooser))
            )
        } catch (ex: ActivityNotFoundException) {
            Log.e(AppConfig.TAG, "File chooser activity not found", ex)
            activity.toast(R.string.toast_require_file_manager)
            fileChooserCallback?.invoke(null)
            fileChooserCallback = null
        }
    }
}