package com.v2ray.ang.viewmodel

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.WEBDAV_BACKUP_FILE_NAME
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.WebDavConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.WebDavManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.ZipUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class BackupViewModel : BaseViewModel() {

    private val _webDavConfig = MutableStateFlow(MmkvManager.decodeWebDavConfig())
    val webDavConfig: StateFlow<WebDavConfig?> = _webDavConfig.asStateFlow()

    sealed interface BackupViewModelEvent : ViewModelEvent {
        data class ShareFile(val filePath: String) : BackupViewModelEvent
        data class ExportLocal(val cachePath: String, val targetUri: Uri) : BackupViewModelEvent
        object RestoreSuccess : BackupViewModelEvent
    }

    fun saveWebDavConfig(config: WebDavConfig) {
        MmkvManager.encodeWebDavConfig(config)
        _webDavConfig.value = config
        toastSuccess(R.string.toast_success)
    }

    fun shareBackup(cacheDir: File, appName: String) {
        launchLoading {
            val ret = backupConfigurationToCache(cacheDir, appName)
            if (ret.first) {
                _viewModelEvent.send(BackupViewModelEvent.ShareFile(ret.second))
            } else {
                toastError(R.string.toast_failure)
            }
        }
    }

    fun prepareBackupForUri(cacheDir: File, appName: String, targetUri: Uri) {
        launchLoading {
            val ret = backupConfigurationToCache(cacheDir, appName)
            if (ret.first) {
                _viewModelEvent.send(BackupViewModelEvent.ExportLocal(ret.second, targetUri))
            } else {
                toastError(R.string.toast_failure)
            }
        }
    }

    fun restoreConfiguration(cacheDir: File, zipFile: File) {
        launchLoading {
            val success = performRestore(cacheDir, zipFile)
            if (success) {
                toastSuccess(R.string.toast_success)
                _viewModelEvent.send(BackupViewModelEvent.RestoreSuccess)
            } else {
                toastError(R.string.toast_failure)
            }
        }
    }

    fun backupViaWebDav(cacheDir: File, appName: String) {
        val config = _webDavConfig.value
        if (config == null || (config.baseUrl.isEmpty())) {
            toastError(R.string.title_webdav_config_setting_unknown)
            return
        }

        launchLoading {
            var tempFile: File? = null
            try {
                val ret = backupConfigurationToCache(cacheDir, appName)
                if (!ret.first) {
                    toastError(R.string.toast_failure)
                    return@launchLoading
                }

                tempFile = File(ret.second)
                WebDavManager.init(config)

                val ok = try {
                    WebDavManager.uploadFile(tempFile, WEBDAV_BACKUP_FILE_NAME)
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

    fun restoreViaWebDav(cacheDir: File) {
        val config = _webDavConfig.value
        if (config == null || (config.baseUrl.isEmpty())) {
            toastError(R.string.title_webdav_config_setting_unknown)
            return
        }

        launchLoading {
            var target: File? = null
            try {
                target = File(cacheDir, "download_${System.currentTimeMillis()}.zip")
                WebDavManager.init(config)
                val ok = WebDavManager.downloadFile(WEBDAV_BACKUP_FILE_NAME, target)
                if (!ok) {
                    toastError(R.string.toast_failure)
                    return@launchLoading
                }

                if (performRestore(cacheDir, target)) {
                    toastSuccess(R.string.toast_success)
                    _viewModelEvent.send(BackupViewModelEvent.RestoreSuccess)
                } else {
                    toastError(R.string.toast_failure)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "WebDAV download error", e)
                toastError(R.string.toast_failure)
            } finally {
                target?.delete()
            }
        }
    }

    private fun backupConfigurationToCache(cacheDir: File, appName: String): Pair<Boolean, String> {
        val dateFormatted = SimpleDateFormat(
            "yyyy-MM-dd-HH-mm-ss",
            Locale.getDefault()
        ).format(System.currentTimeMillis())
        val folderName = "${appName}_$dateFormatted"
        val backupDir = cacheDir.absolutePath + "/$folderName"
        val outputZipFilePath = "${cacheDir.absolutePath}/$folderName.zip"

        val count = MMKV.backupAllToDirectory(backupDir)
        if (count <= 0) {
            return Pair(false, "")
        }

        return if (ZipUtil.zipFromFolder(backupDir, outputZipFilePath)) {
            Pair(true, outputZipFilePath)
        } else {
            Pair(false, "")
        }
    }

    private fun performRestore(cacheDir: File, zipFile: File): Boolean {
        val backupDir = cacheDir.absolutePath + "/${System.currentTimeMillis()}"

        if (!ZipUtil.unzipToFolder(zipFile, backupDir)) {
            return false
        }

        val count = MMKV.restoreAllFromDirectory(backupDir)
        SettingsChangeManager.makeSetupGroupTab()
        SettingsChangeManager.makeRestartService()

        return count > 0
    }
}
