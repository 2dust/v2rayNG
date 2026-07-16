package com.v2ray.ang.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.WEBDAV_BACKUP_FILE_NAME
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityBackupBinding
import com.v2ray.ang.databinding.DialogWebdavBinding
import com.v2ray.ang.dto.entities.WebDavConfig
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.WebDavManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.ZipUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class BackupActivity : HelperBaseActivity() {
    private val binding by lazy { ActivityBackupBinding.inflate(layoutInflater) }

    private val config_backup_options: Array<out String> by lazy {
        resources.getStringArray(R.array.config_backup_options)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(binding.root)
        setContentViewWithToolbar(binding.root, showHomeAsUp = true, title = getString(R.string.title_configuration_backup_restore))

        binding.layoutBackup.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.title_configuration_backup)
                .setItems(config_backup_options) { dialog, which ->
                    when (which) {
                        0 -> backupViaLocal()
                        1 -> backupViaWebDav()
                    }
                }
                .show()
        }

        binding.layoutShare.setOnClickListener {
            val ret = backupConfigurationToCache()
            if (ret.first) {
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("application/zip")
                            .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            .putExtra(
                                Intent.EXTRA_STREAM,
                                FileProvider.getUriForFile(
                                    this, BuildConfig.APPLICATION_ID + ".cache", File(ret.second)
                                )
                            ), getString(R.string.title_configuration_share)
                    )
                )
            } else {
                toastError(R.string.toast_failure)
            }
        }

        binding.layoutRestore.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.title_configuration_restore)
                .setItems(config_backup_options) { dialog, which ->
                    when (which) {
                        0 -> restoreViaLocal()
                        1 -> restoreViaWebDav()
                    }
                }
                .show()
        }

        binding.layoutWebdavConfigSetting.setOnClickListener {
            showWebDavSettingsDialog()
        }
    }

    /**
     * Backup configuration to cache directory
     * Returns Pair<success, zipFilePath>
     */
    private fun backupConfigurationToCache(): Pair<Boolean, String> {
        val dateFormatted = SimpleDateFormat(
            "yyyy-MM-dd-HH-mm-ss",
            Locale.getDefault()
        ).format(System.currentTimeMillis())
        val folderName = "${getString(R.string.app_name)}_${dateFormatted}"
        val backupDir = this.cacheDir.absolutePath + "/$folderName"
        val outputZipFilePath = "${this.cacheDir.absolutePath}/$folderName.zip"

        val count = MMKV.backupAllToDirectory(backupDir)
        if (count <= 0) {
            return Pair(false, "")
        }

        if (ZipUtil.zipFromFolder(backupDir, outputZipFilePath)) {
            return Pair(true, outputZipFilePath)
        } else {
            return Pair(false, "")
        }
    }

    private fun restoreConfiguration(zipFile: File): Boolean {
        val backupDir = this.cacheDir.absolutePath + "/${System.currentTimeMillis()}"

        if (!ZipUtil.unzipToFolder(zipFile, backupDir)) {
            return false
        }

        val count = MMKV.restoreAllFromDirectory(backupDir)
        SettingsChangeManager.makeSetupGroupTab()
        SettingsChangeManager.makeRestartService()

        SettingsManager.initApp(this)
        return count > 0
    }

    private fun showFileChooser() {
        launchFileChooser { uri ->
            if (uri == null) {
                return@launchFileChooser
            }
            try {
                val targetFile =
                    File(this.cacheDir.absolutePath, "${System.currentTimeMillis()}.zip")
                contentResolver.openInputStream(uri).use { input ->
                    targetFile.outputStream().use { fileOut ->
                        input?.copyTo(fileOut)
                    }
                }
                if (restoreConfiguration(targetFile)) {
                    toastSuccess(R.string.toast_success)
                } else {
                    toastError(R.string.toast_failure)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Error during file restore", e)
                toastError(R.string.toast_failure)
            }
        }
    }

    private fun backupViaLocal() {
        // Check if running on Android TV - use fallback method
        if (Utils.isAndroidTv(this)) {
            backupToDownloadsFolder()
            return
        }

        val dateFormatted = SimpleDateFormat(
            "yyyy-MM-dd-HH-mm-ss",
            Locale.getDefault()
        ).format(System.currentTimeMillis())
        val defaultFileName = "${getString(R.string.app_name)}_${dateFormatted}.zip"

        launchCreateDocument(defaultFileName) { uri ->
            if (uri != null) {
                try {
                    val ret = backupConfigurationToCache()
                    if (ret.first) {
                        // Copy the cached zip file to user-selected location
                        contentResolver.openOutputStream(uri)?.use { output ->
                            File(ret.second).inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        // Clean up cache file
                        File(ret.second).delete()
                        toastSuccess(R.string.toast_success)
                    } else {
                        toastError(R.string.toast_failure)
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to backup configuration", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun restoreViaLocal() {
        // Check if running on Android TV - use fallback method
        if (Utils.isAndroidTv(this)) {
            restoreFromDownloadsFolder()
            return
        }

        showFileChooser()
    }

    private fun backupViaWebDav() {
        val saved = MmkvManager.decodeWebDavConfig()
        if (saved == null || saved.baseUrl.isEmpty()) {
            toastError(R.string.title_webdav_config_setting_unknown)
            return
        }

        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                val ret = backupConfigurationToCache()
                if (!ret.first) {
                    withContext(Dispatchers.Main) {
                        toastError(R.string.toast_failure)
                    }
                    return@launch
                }

                tempFile = File(ret.second)

                // Verify the file was created successfully and is not empty
                if (!tempFile.exists()) {
                    LogUtil.e(AppConfig.TAG, "WebDAV backup: temp file does not exist")
                    withContext(Dispatchers.Main) {
                        toastError(R.string.toast_failure)
                    }
                    return@launch
                }

                if (tempFile.length() == 0L) {
                    LogUtil.e(AppConfig.TAG, "WebDAV backup: temp file is empty (0 bytes)")
                    withContext(Dispatchers.Main) {
                        toastError(R.string.toast_failure)
                    }
                    return@launch
                }

                LogUtil.i(AppConfig.TAG, "WebDAV backup: uploading file ${tempFile.length()} bytes")
                WebDavManager.init(saved)

                val ok = try {
                    WebDavManager.uploadFile(tempFile, WEBDAV_BACKUP_FILE_NAME)
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "WebDAV upload error", e)
                    false
                }

                withContext(Dispatchers.Main) {
                    if (ok) toastSuccess(R.string.toast_success) else toastError(R.string.toast_failure)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "WebDAV backup error", e)
                withContext(Dispatchers.Main) {
                    toastError(R.string.toast_failure)
                }
            } finally {
                try {
                    tempFile?.delete()
                } catch (_: Exception) {
                }
                withContext(Dispatchers.Main) {
                    hideLoading()
                }
            }
        }
    }

    private fun restoreViaWebDav() {
        val saved = MmkvManager.decodeWebDavConfig()
        if (saved == null || saved.baseUrl.isEmpty()) {
            toastError(R.string.title_webdav_config_setting_unknown)
            return
        }

        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            var target: File? = null
            try {
                target = File(cacheDir, "download_${System.currentTimeMillis()}.zip")
                WebDavManager.init(saved)
                val ok = WebDavManager.downloadFile(WEBDAV_BACKUP_FILE_NAME, target)
                if (!ok) {
                    withContext(Dispatchers.Main) {
                        toastError(R.string.toast_failure)
                    }
                    return@launch
                }

                val restored = restoreConfiguration(target)
                withContext(Dispatchers.Main) {
                    if (restored) {
                        toastSuccess(R.string.toast_success)
                    } else {
                        toastError(R.string.toast_failure)
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "WebDAV download error", e)
                withContext(Dispatchers.Main) { toastError(R.string.toast_failure) }
            } finally {
                try {
                    target?.delete()
                } catch (_: Exception) {
                }
                withContext(Dispatchers.Main) {
                    hideLoading()
                }
            }
        }
    }

    private fun showWebDavSettingsDialog() {
        val dialogBinding = DialogWebdavBinding.inflate(layoutInflater)

        MmkvManager.decodeWebDavConfig()?.let { cfg ->
            dialogBinding.etWebdavUrl.setText(cfg.baseUrl)
            dialogBinding.etWebdavUser.setText(cfg.username ?: "")
            dialogBinding.etWebdavPass.setText(cfg.password ?: "")
            dialogBinding.etWebdavRemotePath.setText(cfg.remoteBasePath ?: "/")
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.title_webdav_config_setting)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.menu_item_save_config) { _, _ ->
                val url = dialogBinding.etWebdavUrl.text.toString().trim()
                val user = dialogBinding.etWebdavUser.text.toString().trim().ifEmpty { null }
                val pass = dialogBinding.etWebdavPass.text.toString()
                val remotePath = dialogBinding.etWebdavRemotePath.text.toString().trim().ifEmpty { AppConfig.WEBDAV_BACKUP_DIR }
                val cfg = WebDavConfig(baseUrl = url, username = user, password = pass, remoteBasePath = remotePath)
                MmkvManager.encodeWebDavConfig(cfg)
                toastSuccess(R.string.toast_success)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Backup to Downloads folder (fallback for Android TV where SAF may not be available)
     */
    private fun backupToDownloadsFolder() {
        // Check storage permission for Android 12 and below
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            checkAndRequestPermission(PermissionType.WRITE_EXTERNAL_STORAGE) {
                performBackupToDownloadsFolder()
            }
        } else {
            // Android 13+ doesn't need permission for Downloads
            performBackupToDownloadsFolder()
        }
    }

    private fun performBackupToDownloadsFolder() {
        try {
            val dateFormatted = SimpleDateFormat(
                "yyyy-MM-dd-HH-mm-ss",
                Locale.getDefault()
            ).format(System.currentTimeMillis())
            val fileName = "${getString(R.string.app_name)}_${dateFormatted}.zip"

            // Use public Downloads directory
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            val destFile = File(downloadsDir, fileName)

            val ret = backupConfigurationToCache()
            if (ret.first) {
                // Copy from cache to Downloads
                File(ret.second).inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                File(ret.second).delete()
                toastSuccess("${getString(R.string.toast_success)}\n${destFile.absolutePath}")
                LogUtil.i(AppConfig.TAG, "Backup saved to: ${destFile.absolutePath}")
            } else {
                toastError(R.string.toast_failure)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to backup to Downloads folder", e)
            toastError(R.string.toast_failure)
        }
    }

    /**
     * Restore from Downloads folder (fallback for Android TV where SAF may not be available)
     * Scans for the most recent backup file
     */
    private fun restoreFromDownloadsFolder() {
        // Check storage permission for Android 12 and below
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            checkAndRequestPermission(PermissionType.WRITE_EXTERNAL_STORAGE) {
                performRestoreFromDownloadsFolder()
            }
        } else {
            // Android 13+ doesn't need permission for Downloads
            performRestoreFromDownloadsFolder()
        }
    }

    private fun performRestoreFromDownloadsFolder() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists() || !downloadsDir.isDirectory) {
                toastError(getString(R.string.backup_downloads_folder_not_found))
                return
            }

            // Find all backup zip files
            val backupFiles = downloadsDir.listFiles { file ->
                file.isFile && file.name.endsWith(".zip") &&
                (file.name.startsWith(getString(R.string.app_name)) || file.name.contains("v2rayNG"))
            }?.sortedByDescending { it.lastModified() }

            if (backupFiles.isNullOrEmpty()) {
                toastError(getString(R.string.backup_no_files_found))
                return
            }

            // Show dialog to select backup file
            val fileNames = backupFiles.map {
                "${it.name} (${android.text.format.Formatter.formatFileSize(this, it.length())})"
            }.toTypedArray()

            AlertDialog.Builder(this)
                .setTitle(R.string.title_configuration_restore)
                .setItems(fileNames) { _, which ->
                    val selectedFile = backupFiles[which]
                    performRestore(selectedFile)
                }
                .setNeutralButton(R.string.backup_delete_old) { _, _ ->
                    showDeleteBackupsDialog(backupFiles)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to restore from Downloads folder", e)
            toastError(R.string.toast_failure)
        }
    }

    private fun showDeleteBackupsDialog(backupFiles: List<File>) {
        val fileNames = backupFiles.map {
            "${it.name} (${android.text.format.Formatter.formatFileSize(this, it.length())})"
        }.toTypedArray()

        val selectedItems = BooleanArray(backupFiles.size) { false }

        AlertDialog.Builder(this)
            .setTitle(R.string.backup_select_to_delete)
            .setMultiChoiceItems(fileNames, selectedItems) { _, which, isChecked ->
                selectedItems[which] = isChecked
            }
            .setPositiveButton(R.string.backup_delete_button) { _, _ ->
                var deletedCount = 0
                selectedItems.forEachIndexed { index, selected ->
                    if (selected) {
                        try {
                            if (backupFiles[index].delete()) {
                                deletedCount++
                            }
                        } catch (e: Exception) {
                            LogUtil.e(AppConfig.TAG, "Failed to delete ${backupFiles[index].name}", e)
                        }
                    }
                }
                if (deletedCount > 0) {
                    toastSuccess(getString(R.string.backup_deleted_count, deletedCount))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun performRestore(zipFile: File) {
        try {
            if (restoreConfiguration(zipFile)) {
                toastSuccess(R.string.toast_success)
            } else {
                toastError(R.string.toast_failure)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to restore configuration", e)
            toastError(R.string.toast_failure)
        }
    }
}