package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.compose.InputDialog
import com.v2ray.ang.compose.InputField
import com.v2ray.ang.compose.SelectListDialog
import com.v2ray.ang.compose.SettingsMenuItem
import com.v2ray.ang.dto.entities.WebDavConfig
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.viewmodel.BackupViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class BackupActivity : HelperBaseComponentActivity() {

    private val viewModel: BackupViewModel by viewModels()

    private val configBackupOptions: Array<out String> by lazy {
        resources.getStringArray(R.array.config_backup_options)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewModelEvent.collect { event ->
                    when (event) {
                        is BackupViewModel.BackupViewModelEvent.ShareFile -> {
                            handleShareFile(event.filePath)
                        }

                        is BackupViewModel.BackupViewModelEvent.ExportLocal -> {
                            handleExportLocal(event.cachePath, event.targetUri)
                        }

                        is BackupViewModel.BackupViewModelEvent.RestoreSuccess -> {
                            SettingsManager.initApp(this@BackupActivity)
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        BackupScreen(
            isLoadingState = viewModel.isLoading,
            webDavConfigState = viewModel.webDavConfig,
            backupOptions = configBackupOptions.toList(),
            onBackupOptionSelected = { which ->
                when (which) {
                    0 -> backupViaLocal()
                    1 -> viewModel.backupViaWebDav(cacheDir, getString(R.string.app_name))
                }
            },
            onShareClick = { viewModel.shareBackup(cacheDir, getString(R.string.app_name)) },
            restoreOptions = configBackupOptions.toList(),
            onRestoreOptionSelected = { which ->
                when (which) {
                    0 -> restoreViaLocal()
                    1 -> viewModel.restoreViaWebDav(cacheDir)
                }
            },
            onWebDavSave = { config -> viewModel.saveWebDavConfig(config) },
            onBackClick = { finish() }
        )
    }

    private fun handleShareFile(filePath: String) {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("application/zip")
                    .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(
                        Intent.EXTRA_STREAM,
                        FileProvider.getUriForFile(
                            this,
                            BuildConfig.APPLICATION_ID + ".cache",
                            File(filePath)
                        )
                    ),
                getString(R.string.title_configuration_share)
            )
        )
    }

    private fun handleExportLocal(cachePath: String, targetUri: Uri) {
        try {
            contentResolver.openOutputStream(targetUri)?.use { output ->
                File(cachePath).inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            File(cachePath).delete()
            toastSuccess(R.string.toast_success)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to copy backup to Uri", e)
            toastError(R.string.toast_failure)
        }
    }

    private fun backupViaLocal() {
        val dateFormatted = SimpleDateFormat(
            "yyyy-MM-dd-HH-mm-ss",
            Locale.getDefault()
        ).format(System.currentTimeMillis())
        val defaultFileName = "${getString(R.string.app_name)}_${dateFormatted}.zip"

        launchCreateDocument(defaultFileName) { uri ->
            if (uri != null) {
                viewModel.prepareBackupForUri(cacheDir, getString(R.string.app_name), uri)
            }
        }
    }

    private fun restoreViaLocal() {
        launchFileChooser { uri ->
            if (uri == null) {
                return@launchFileChooser
            }
            try {
                val targetFile =
                    File(cacheDir.absolutePath, "${System.currentTimeMillis()}.zip")
                contentResolver.openInputStream(uri).use { input ->
                    targetFile.outputStream().use { fileOut ->
                        input?.copyTo(fileOut)
                    }
                }
                viewModel.restoreConfiguration(cacheDir, targetFile)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Error during file restore", e)
                toastError(R.string.toast_failure)
            }
        }
    }
}

@Composable
fun BackupScreen(
    isLoadingState: StateFlow<Boolean>,
    webDavConfigState: StateFlow<WebDavConfig?>,
    backupOptions: List<String>,
    onBackupOptionSelected: (Int) -> Unit,
    onShareClick: () -> Unit,
    restoreOptions: List<String>,
    onRestoreOptionSelected: (Int) -> Unit,
    onWebDavSave: (WebDavConfig) -> Unit,
    onBackClick: () -> Unit
) {
    val isLoading by isLoadingState.collectAsState()
    val currentWebDavConfig by webDavConfigState.collectAsState()
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showWebDavDialog by remember { mutableStateOf(false) }

    val webDavSummary = currentWebDavConfig?.baseUrl

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_configuration_backup_restore),
                onBackClick = onBackClick,
                isLoading = isLoading
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_backup_24dp),
                title = stringResource(R.string.title_configuration_backup),
                onClick = { showBackupDialog = true }
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_share_24dp),
                title = stringResource(R.string.title_configuration_share),
                onClick = onShareClick
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_restore_24dp),
                title = stringResource(R.string.title_configuration_restore),
                onClick = { showRestoreDialog = true }
            )
            Spacer(modifier = Modifier.height(16.dp))
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_settings_24dp),
                title = stringResource(R.string.title_webdav_config_setting),
                subtitle = webDavSummary,
                onClick = { showWebDavDialog = true }
            )
        }
    }

    if (showBackupDialog) {
        SelectListDialog(
            title = stringResource(R.string.title_configuration_backup),
            options = backupOptions,
            onSelected = { index, _ ->
                showBackupDialog = false
                onBackupOptionSelected(index)
            },
            onDismiss = { showBackupDialog = false }
        )
    }
    if (showRestoreDialog) {
        SelectListDialog(
            title = stringResource(R.string.title_configuration_restore),
            options = restoreOptions,
            onSelected = { index, _ ->
                showRestoreDialog = false
                onRestoreOptionSelected(index)
            },
            onDismiss = { showRestoreDialog = false }
        )
    }
    if (showWebDavDialog) {
        WebDavInputDialog(
            initialConfig = currentWebDavConfig,
            onSave = {
                showWebDavDialog = false
                onWebDavSave(it)
            },
            onDismiss = { showWebDavDialog = false }
        )
    }
}

@Composable
private fun WebDavInputDialog(
    initialConfig: WebDavConfig?,
    onSave: (WebDavConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf(initialConfig?.baseUrl ?: "") }
    var username by remember { mutableStateOf(initialConfig?.username ?: "") }
    var password by remember { mutableStateOf(initialConfig?.password ?: "") }
    var remotePath by remember { mutableStateOf(initialConfig?.remoteBasePath ?: "/") }

    val fields = listOf(
        InputField(
            label = stringResource(R.string.title_webdav_url),
            value = url
        ),
        InputField(
            label = stringResource(R.string.title_webdav_user),
            value = username
        ),
        InputField(
            label = stringResource(R.string.title_webdav_pass),
            value = password,
            visualTransformation = VisualTransformation.None
        ),
        InputField(
            label = stringResource(R.string.title_webdav_remote_path),
            value = remotePath
        )
    )

    InputDialog(
        title = stringResource(R.string.title_webdav_config_setting),
        fields = fields,
        onFieldChange = { index, value ->
            when (index) {
                0 -> url = value
                1 -> username = value
                2 -> password = value
                3 -> remotePath = value
            }
        },
        confirmText = stringResource(R.string.menu_item_save_config),
        dismissText = stringResource(android.R.string.cancel),
        onConfirm = {
            onSave(
                WebDavConfig(
                    baseUrl = url.trim(),
                    username = username.trim().ifEmpty { null },
                    password = password,
                    remoteBasePath = remotePath.trim().ifEmpty { AppConfig.WEBDAV_BACKUP_DIR }
                )
            )
        },
        onDismiss = onDismiss
    )
}
