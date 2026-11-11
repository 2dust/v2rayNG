package com.v2ray.ang.ui.backup

import android.app.Application
import android.net.Uri
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.WebDavConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.WebDavManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.ui.base.ViewModelEvent
import com.v2ray.ang.util.BackupCipher
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.ZipUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class BackupViewModel(application: Application) : BaseViewModel(application) {

    private val _webDavConfig = MutableStateFlow(MmkvManager.decodeWebDavConfig())
    val webDavConfig: StateFlow<WebDavConfig?> = _webDavConfig.asStateFlow()

    /** File the restore is currently working on, so a retry after a wrong password can reuse it. */
    private var pendingRestoreFile: File? = null

    sealed interface BackupViewModelEvent : ViewModelEvent {
        data class ShareFile(val filePath: String) : BackupViewModelEvent
        data class ExportLocal(val cachePath: String, val targetUri: Uri) : BackupViewModelEvent

        /** The picked backup is password protected and nothing was restored yet. */
        data class RestorePasswordRequired(val cachedFile: File) : BackupViewModelEvent

        /** The password did not open the backup. */
        data class RestorePasswordMismatch(val cachedFile: File) : BackupViewModelEvent
        object RestoreSuccess : BackupViewModelEvent
    }

    private sealed interface Archive {
        data class Ready(val zipFile: File) : Archive
        object PasswordRequired : Archive
        object PasswordMismatch : Archive
    }

    fun saveWebDavConfig(config: WebDavConfig) {
        MmkvManager.encodeWebDavConfig(config)
        _webDavConfig.value = config
        toastSuccess(R.string.toast_success)
    }

    fun cleanupProfileStorage() {
        launchLoading {
            try {
                val removed = withContext(Dispatchers.IO) {
                    MmkvManager.removeOrphanedServerProfiles()
                }
                if (removed == null) {
                    toastError(R.string.toast_profile_storage_cleanup_skipped)
                } else {
                    toastSuccess(getString(R.string.toast_profile_storage_cleanup, removed))
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to clean up profile storage", e)
                toastError(R.string.toast_failure)
            }
        }
    }

    fun shareBackup(cacheDir: File, appName: String, password: String?) {
        launchLoading {
            val ret = backupFileToCache(cacheDir, appName, password)
            if (ret.first) {
                _viewModelEvent.send(BackupViewModelEvent.ShareFile(ret.second))
            } else {
                toastError(R.string.toast_backup_protect_failed)
            }
        }
    }

    fun prepareBackupForUri(cacheDir: File, appName: String, targetUri: Uri, password: String?) {
        launchLoading {
            val ret = backupFileToCache(cacheDir, appName, password)
            if (ret.first) {
                _viewModelEvent.send(BackupViewModelEvent.ExportLocal(ret.second, targetUri))
            } else {
                toastError(R.string.toast_backup_protect_failed)
            }
        }
    }

    fun restoreConfiguration(cacheDir: File, sourceFile: File, password: String?) {
        launchLoading {
            restoreFrom(cacheDir, sourceFile, password)
        }
    }

    /** Drops the temporary copy kept for a password retry. */
    fun cancelPendingRestore() {
        pendingRestoreFile?.delete()
        pendingRestoreFile = null
    }

    fun backupViaWebDav(cacheDir: File, appName: String, password: String?) {
        val config = _webDavConfig.value
        if (config == null || (config.baseUrl.isEmpty())) {
            toastError(R.string.title_webdav_config_setting_unknown)
            return
        }

        launchLoading {
            var tempFile: File? = null
            try {
                val ret = backupFileToCache(cacheDir, appName, password)
                if (!ret.first) {
                    toastError(R.string.toast_backup_protect_failed)
                    return@launchLoading
                }

                tempFile = File(ret.second)
                WebDavManager.init(config)

                val ok = try {
                    WebDavManager.uploadFile(tempFile, AppConfig.WEBDAV_BACKUP_FILE_NAME)
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "WebDAV upload error", e)
                    false
                }

                if (ok) {
                    toastSuccess(R.string.toast_success)
                } else {
                    toastError(R.string.toast_failure)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "WebDAV backup error", e)
                toastError(R.string.toast_failure)
            } finally {
                tempFile?.delete()
            }
        }
    }

    fun restoreViaWebDav(cacheDir: File, password: String?) {
        val config = _webDavConfig.value
        if (config == null || (config.baseUrl.isEmpty())) {
            toastError(R.string.title_webdav_config_setting_unknown)
            return
        }

        launchLoading {
            val target = File(cacheDir, "webdav_${System.currentTimeMillis()}.zip")
            try {
                WebDavManager.init(config)
                val ok = WebDavManager.downloadFile(AppConfig.WEBDAV_BACKUP_FILE_NAME, target)
                if (!ok) {
                    target.delete()
                    toastError(R.string.toast_failure)
                    return@launchLoading
                }
                restoreFrom(cacheDir, target, password)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "WebDAV download error", e)
                toastError(R.string.toast_failure)
            }
        }
    }

    /**
     * Opens [sourceFile] (decrypting it when it carries a password) and restores it. A missing or
     * wrong password is reported through an event instead of a toast, so the screen can ask again.
     */
    private suspend fun restoreFrom(cacheDir: File, sourceFile: File, password: String?) {
        pendingRestoreFile = sourceFile
        when (val archive = openArchive(sourceFile, password)) {
            is Archive.Ready -> {
                val success = performRestore(cacheDir, archive.zipFile)
                if (archive.zipFile != sourceFile) archive.zipFile.delete()
                if (success) {
                    pendingRestoreFile?.delete()
                    pendingRestoreFile = null
                    toastSuccess(R.string.toast_success)
                    _viewModelEvent.send(BackupViewModelEvent.RestoreSuccess)
                } else {
                    toastError(R.string.toast_restore_failed)
                }
            }

            Archive.PasswordRequired ->
                _viewModelEvent.send(BackupViewModelEvent.RestorePasswordRequired(sourceFile))

            Archive.PasswordMismatch ->
                _viewModelEvent.send(BackupViewModelEvent.RestorePasswordMismatch(sourceFile))
        }
    }

    private suspend fun openArchive(sourceFile: File, password: String?): Archive =
        withContext(Dispatchers.IO) {
            if (!BackupCipher.isEncrypted(sourceFile)) {
                return@withContext Archive.Ready(sourceFile)
            }
            if (password.isNullOrEmpty()) {
                return@withContext Archive.PasswordRequired
            }

            val plain = File(sourceFile.parentFile, "${sourceFile.nameWithoutExtension}_plain.zip")
            if (BackupCipher.decrypt(sourceFile, plain, password.toCharArray())) {
                Archive.Ready(plain)
            } else {
                Archive.PasswordMismatch
            }
        }

    /**
     * Exports every MMKV storage (settings, servers, subscriptions, routing) into one archive and
     * optionally protects it with [password].
     */
    private fun backupFileToCache(
        cacheDir: File,
        appName: String,
        password: String?,
    ): Pair<Boolean, String> {
        val dateFormatted = SimpleDateFormat(
            "yyyy-MM-dd-HH-mm-ss",
            Locale.getDefault()
        ).format(System.currentTimeMillis())
        val folderName = "${appName}_$dateFormatted"
        val backupDir = File(cacheDir, folderName)
        val zipFile = File(cacheDir, "$folderName.zip")

        val count = MMKV.backupAllToDirectory(backupDir.absolutePath)
        if (count <= 0) {
            backupDir.deleteRecursively()
            return Pair(false, "")
        }

        val zipped = ZipUtil.zipFromFolder(backupDir.absolutePath, zipFile.absolutePath)
        backupDir.deleteRecursively()
        if (!zipped) {
            zipFile.delete()
            return Pair(false, "")
        }

        val secret = password?.takeIf { it.isNotEmpty() } ?: return Pair(true, zipFile.absolutePath)
        val encryptedFile = File(cacheDir, "$folderName.${BackupCipher.EXTENSION}")
        val encrypted = BackupCipher.encrypt(zipFile, encryptedFile, secret.toCharArray())
        zipFile.delete()
        return if (encrypted) Pair(true, encryptedFile.absolutePath) else Pair(false, "")
    }

    private suspend fun performRestore(cacheDir: File, zipFile: File): Boolean =
        withContext(Dispatchers.IO) {
            val backupDir = File(cacheDir, "restore_${System.nanoTime()}")
            try {
                if (!ZipUtil.unzipToFolder(zipFile, backupDir.absolutePath)) {
                    return@withContext false
                }

                val count = MMKV.restoreAllFromDirectory(backupDir.absolutePath)
                SettingsChangeManager.makeSetupGroupTab()
                SettingsChangeManager.makeRestartService()
                count > 0
            } finally {
                backupDir.deleteRecursively()
            }
        }
}
