package com.v2ray.ang.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.WEBDAV_BACKUP_FILE_NAME
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.WebDavConfig
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.WebDavManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.ZipUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class BackupActivity : HelperBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                BackupScreen(
                    isLoadingFlow = isLoadingFlow,
                    onBack = { finish() },
                    onBackup = { showBackupOptions() },
                    onRestore = { showRestoreOptions() },
                    onShare = { shareBackup() },
                    onWebDavSettings = { showWebDavSettingsDialog() }
                )
            }
        }
    }

    private fun showBackupOptions() {
        val options = resources.getStringArray(R.array.config_backup_options)
        AlertDialog.Builder(this)
            .setTitle(R.string.title_configuration_backup)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> backupViaLocal()
                    1 -> backupViaWebDav()
                }
            }.show()
    }

    private fun showRestoreOptions() {
        val options = resources.getStringArray(R.array.config_backup_options)
        AlertDialog.Builder(this)
            .setTitle(R.string.title_configuration_restore)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> restoreViaLocal()
                    1 -> restoreViaWebDav()
                }
            }.show()
    }

    private fun shareBackup() {
        val ret = backupConfigurationToCache()
        if (ret.first) {
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("application/zip")
                    .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".cache", File(ret.second))),
                getString(R.string.title_configuration_share)
            ))
        } else {
            toastError(R.string.toast_failure)
        }
    }

    private fun backupConfigurationToCache(): Pair<Boolean, String> {
        val dateFormatted = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault()).format(System.currentTimeMillis())
        val folderName = "${getString(R.string.app_name)}_${dateFormatted}"
        val backupDir = File(cacheDir, folderName).absolutePath
        val outputZipFilePath = File(cacheDir, "$folderName.zip").absolutePath

        val count = MMKV.backupAllToDirectory(backupDir)
        if (count <= 0) return Pair(false, "")
        return if (ZipUtil.zipFromFolder(backupDir, outputZipFilePath)) Pair(true, outputZipFilePath) else Pair(false, "")
    }

    private fun restoreConfiguration(zipFile: File): Boolean {
        val backupDir = File(cacheDir, System.currentTimeMillis().toString()).absolutePath
        if (!ZipUtil.unzipToFolder(zipFile, backupDir)) return false
        val count = MMKV.restoreAllFromDirectory(backupDir)
        SettingsChangeManager.makeSetupGroupTab()
        SettingsChangeManager.makeRestartService()
        SettingsManager.initApp(this)
        return count > 0
    }

    private fun backupViaLocal() {
        val dateFormatted = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault()).format(System.currentTimeMillis())
        val defaultFileName = "${getString(R.string.app_name)}_${dateFormatted}.zip"

        launchCreateDocument(defaultFileName) { uri ->
            if (uri != null) {
                try {
                    val ret = backupConfigurationToCache()
                    if (ret.first) {
                        contentResolver.openOutputStream(uri)?.use { output ->
                            File(ret.second).inputStream().use { input -> input.copyTo(output) }
                        }
                        File(ret.second).delete()
                        toastSuccess(R.string.toast_success)
                    } else {
                        toastError(R.string.toast_failure)
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to backup", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun restoreViaLocal() {
        launchFileChooser { uri ->
            if (uri == null) return@launchFileChooser
            try {
                val targetFile = File(cacheDir, "${System.currentTimeMillis()}.zip")
                contentResolver.openInputStream(uri).use { input ->
                    targetFile.outputStream().use { fileOut -> input?.copyTo(fileOut) }
                }
                if (restoreConfiguration(targetFile)) toastSuccess(R.string.toast_success)
                else toastError(R.string.toast_failure)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Restore error", e)
                toastError(R.string.toast_failure)
            }
        }
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
                if (ret.first) {
                    tempFile = File(ret.second)
                    WebDavManager.init(saved)
                    val ok = WebDavManager.uploadFile(tempFile, WEBDAV_BACKUP_FILE_NAME)
                    withContext(Dispatchers.Main) { if (ok) toastSuccess(R.string.toast_success) else toastError(R.string.toast_failure) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toastError(R.string.toast_failure) }
            } finally {
                tempFile?.delete()
                withContext(Dispatchers.Main) { hideLoading() }
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
                if (WebDavManager.downloadFile(WEBDAV_BACKUP_FILE_NAME, target)) {
                    val restored = restoreConfiguration(target)
                    withContext(Dispatchers.Main) { if (restored) toastSuccess(R.string.toast_success) else toastError(R.string.toast_failure) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toastError(R.string.toast_failure) }
            } finally {
                target?.delete()
                withContext(Dispatchers.Main) { hideLoading() }
            }
        }
    }

    private fun showWebDavSettingsDialog() {
        // We'll use a Compose Dialog instead of the old XML one
        setContent {
            MaterialTheme {
                var showDialog by remember { mutableStateOf(true) }
                if (showDialog) {
                    val initialConfig = MmkvManager.decodeWebDavConfig() ?: WebDavConfig(baseUrl = "")
                    var config by remember { mutableStateOf(initialConfig) }
                    
                    AlertDialog(
                        onDismissRequest = { showDialog = false },
                        title = { Text(stringResource(R.string.title_webdav_config_setting)) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = config.baseUrl, onValueChange = { config = config.copy(baseUrl = it) }, label = { Text("WebDAV URL") })
                                OutlinedTextField(value = config.username ?: "", onValueChange = { config = config.copy(username = it.ifEmpty { null }) }, label = { Text("Username") })
                                OutlinedTextField(value = config.password ?: "", onValueChange = { config = config.copy(password = it.ifEmpty { null }) }, label = { Text("Password") })
                                OutlinedTextField(value = config.remoteBasePath, onValueChange = { config = config.copy(remoteBasePath = it.ifEmpty { "/" }) }, label = { Text("Remote Path") })
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                MmkvManager.encodeWebDavConfig(config)
                                toastSuccess(R.string.toast_success)
                                showDialog = false
                            }) { Text(stringResource(R.string.menu_item_save_config)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDialog = false }) { Text(stringResource(android.R.string.cancel)) }
                        }
                    )
                }
                
                // Keep the background screen
                BackupScreen(
                    isLoadingFlow = isLoadingFlow,
                    onBack = { finish() },
                    onBackup = { showBackupOptions() },
                    onRestore = { showRestoreOptions() },
                    onShare = { shareBackup() },
                    onWebDavSettings = { showWebDavSettingsDialog() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    isLoadingFlow: kotlinx.coroutines.flow.StateFlow<Boolean>,
    onBack: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onShare: () -> Unit,
    onWebDavSettings: () -> Unit
) {
    val isLoading by isLoadingFlow.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_configuration_backup_restore)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.title_configuration_backup)) },
                    supportingContent = { Text("Backup current configuration") },
                    modifier = Modifier.clickable(onClick = onBackup)
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.title_configuration_restore)) },
                    supportingContent = { Text("Restore from previous backup") },
                    modifier = Modifier.clickable(onClick = onRestore)
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.title_configuration_share)) },
                    supportingContent = { Text("Share configuration as zip") },
                    modifier = Modifier.clickable(onClick = onShare)
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.title_webdav_config_setting)) },
                    supportingContent = { Text("Configure WebDAV sync") },
                    modifier = Modifier.clickable(onClick = onWebDavSettings)
                )
            }
        }
    }
}
