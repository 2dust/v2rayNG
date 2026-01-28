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
 * Helper for choosing and creating files using Android Storage Access Framework.
 * Supports both file selection (ACTION_GET_CONTENT) and file creation (CreateDocument).
 */
class FileChooserHelper(private val activity: AppCompatActivity) {
    private var fileChooserCallback: ((Uri?) -> Unit)? = null
    private var documentCreateCallback: ((Uri?) -> Unit)? = null

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

    private val documentCreateLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            documentCreateCallback?.invoke(uri)
            documentCreateCallback = null
        }

    /**
     * Launch file chooser with ACTION_GET_CONTENT intent to select an existing file.
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

    /**
     * Launch document creator to create a new file at user-selected location.
     *
     * @param fileName Default file name for the new document
     * @param onResult Callback invoked with the created file URI (null if cancelled)
     */
    fun createDocument(
        fileName: String,
        onResult: (Uri?) -> Unit
    ) {
        documentCreateCallback = onResult
        try {
            documentCreateLauncher.launch(fileName)
        } catch (ex: ActivityNotFoundException) {
            Log.e(AppConfig.TAG, "Document creator activity not found", ex)
            activity.toast(R.string.toast_require_file_manager)
            documentCreateCallback?.invoke(null)
            documentCreateCallback = null
        }
    }
}