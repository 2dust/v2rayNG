package com.v2ray.ang.ui.backup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.WebDavConfig
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.InputDialog
import com.v2ray.ang.ui.compose.InputField
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.PreferenceGroupHeader
import com.v2ray.ang.ui.compose.SelectListDialog
import com.v2ray.ang.ui.compose.SettingsMenuItem
import com.v2ray.ang.util.BackupCipher
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

enum class BackupLocation(@StringRes val labelRes: Int) {
    Local(R.string.backup_location_local),
    WebDav(R.string.backup_location_webdav)
}

class BackupActivity : HelperBaseComponentActivity() {

    private val viewModel: BackupViewModel by viewModels()

    /** What the open password dialog is collecting a password for. */
    private sealed interface PendingPassword {
        /** Create a backup; the password is optional. */
        data class Backup(val location: BackupLocation) : PendingPassword

        /** Share a backup file; the password is optional. */
        object Share : PendingPassword

        /** Open an existing backup; [mismatch] marks a wrong password so the dialog can say so. */
        data class Restore(val file: File, val mismatch: Boolean = false) : PendingPassword
    }

    private var pendingPassword by mutableStateOf<PendingPassword?>(null)

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

                        is BackupViewModel.BackupViewModelEvent.RestorePasswordRequired -> {
                            pendingPassword =
                                PendingPassword.Restore(event.cachedFile, mismatch = false)
                        }

                        is BackupViewModel.BackupViewModelEvent.RestorePasswordMismatch -> {
                            pendingPassword =
                                PendingPassword.Restore(event.cachedFile, mismatch = true)
                        }

                        is BackupViewModel.BackupViewModelEvent.RestoreSuccess -> {
                            pendingPassword = null
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
            passwordRequest = passwordRequest(),
            onPasswordSubmit = { password -> submitPassword(password) },
            onPasswordDismiss = {
                pendingPassword = null
                viewModel.cancelPendingRestore()
            },
            onBackupOptionSelected = { location ->
                pendingPassword = PendingPassword.Backup(location)
            },
            onShareClick = { pendingPassword = PendingPassword.Share },
            onRestoreOptionSelected = { location ->
                when (location) {
                    BackupLocation.Local -> restoreViaLocal()
                    BackupLocation.WebDav -> viewModel.restoreViaWebDav(cacheDir, null)
                }
            },
            onCleanupProfiles = viewModel::cleanupProfileStorage,
            onWebDavSave = { config -> viewModel.saveWebDavConfig(config) },
            onBackClick = { finish() }
        )
    }

    /** Describes the open password prompt for the screen, or null when none is needed. */
    private fun passwordRequest(): BackupPasswordRequest? = when (val pending = pendingPassword) {
        null -> null
        is PendingPassword.Backup -> BackupPasswordRequest(
            key = "backup-${pending.location}",
            titleRes = R.string.backup_password_backup_title,
            hintRes = R.string.backup_password_backup_hint,
            optional = true,
            mismatch = false,
        )

        PendingPassword.Share -> BackupPasswordRequest(
            key = "share",
            titleRes = R.string.backup_password_backup_title,
            hintRes = R.string.backup_password_backup_hint,
            optional = true,
            mismatch = false,
        )

        is PendingPassword.Restore -> BackupPasswordRequest(
            key = "restore-${pending.file.path}-${pending.mismatch}",
            titleRes = R.string.backup_password_restore_title,
            hintRes = R.string.backup_password_restore_hint,
            optional = false,
            mismatch = pending.mismatch,
        )
    }

    private fun submitPassword(password: String) {
        val pending = pendingPassword ?: return
        pendingPassword = null
        val secret = password.takeIf { it.isNotEmpty() }
        val appName = getString(R.string.app_name)

        when (pending) {
            is PendingPassword.Backup -> when (pending.location) {
                BackupLocation.Local -> backupViaLocal(secret)
                BackupLocation.WebDav -> viewModel.backupViaWebDav(cacheDir, appName, secret)
            }

            PendingPassword.Share -> viewModel.shareBackup(cacheDir, appName, secret)

            is PendingPassword.Restore ->
                viewModel.restoreConfiguration(cacheDir, pending.file, secret)
        }
    }

    private fun handleShareFile(filePath: String) {
        val file = File(filePath)
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType(mimeTypeOf(file))
                    .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(
                        Intent.EXTRA_STREAM,
                        FileProvider.getUriForFile(
                            this,
                            BuildConfig.APPLICATION_ID + ".cache",
                            file
                        )
                    ),
                getString(R.string.title_configuration_share)
            )
        )
    }

    private fun mimeTypeOf(file: File): String =
        if (file.extension.equals(BackupCipher.EXTENSION, ignoreCase = true)) {
            "application/octet-stream"
        } else {
            "application/zip"
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

    private fun backupViaLocal(password: String?) {
        val dateFormatted = SimpleDateFormat(
            "yyyy-MM-dd-HH-mm-ss",
            Locale.getDefault()
        ).format(System.currentTimeMillis())
        val extension = if (password == null) "zip" else BackupCipher.EXTENSION
        val defaultFileName = "${getString(R.string.app_name)}_${dateFormatted}.$extension"

        launchCreateDocument(defaultFileName) { uri ->
            if (uri != null) {
                viewModel.prepareBackupForUri(cacheDir, getString(R.string.app_name), uri, password)
            }
        }
    }

    private fun restoreViaLocal() {
        launchFileChooser(mimeType = "*/*") { uri ->
            if (uri == null) {
                return@launchFileChooser
            }
            try {
                val targetFile = File(cacheDir, "restore_${System.currentTimeMillis()}.zip")
                contentResolver.openInputStream(uri).use { input ->
                    targetFile.outputStream().use { fileOut ->
                        input?.copyTo(fileOut)
                    }
                }
                viewModel.restoreConfiguration(cacheDir, targetFile, null)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Error during file restore", e)
                toastError(R.string.toast_failure)
            }
        }
    }
}

/** Everything the screen needs to draw the password prompt. */
data class BackupPasswordRequest(
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val hintRes: Int,
    val optional: Boolean,
    val mismatch: Boolean,
)

@Composable
fun BackupScreen(
    isLoadingState: StateFlow<Boolean>,
    webDavConfigState: StateFlow<WebDavConfig?>,
    passwordRequest: BackupPasswordRequest?,
    onPasswordSubmit: (String) -> Unit,
    onPasswordDismiss: () -> Unit,
    onBackupOptionSelected: (BackupLocation) -> Unit,
    onShareClick: () -> Unit,
    onRestoreOptionSelected: (BackupLocation) -> Unit,
    onCleanupProfiles: () -> Unit,
    onWebDavSave: (WebDavConfig) -> Unit,
    onBackClick: () -> Unit
) {
    val isLoading by isLoadingState.collectAsState()
    val currentWebDavConfig by webDavConfigState.collectAsState()
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showCleanupDialog by remember { mutableStateOf(false) }
    var showWebDavDialog by remember { mutableStateOf(false) }

    val webDavSummary = currentWebDavConfig?.baseUrl

    Scaffold(
        contentWindowInsets = WindowInsets(0),
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
            PreferenceGroupHeader(title = stringResource(R.string.backup_group_backup))
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_backup_24dp),
                title = stringResource(R.string.title_configuration_backup),
                subtitle = stringResource(R.string.summary_backup_contents),
                onClick = { showBackupDialog = true }
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_share_24dp),
                title = stringResource(R.string.title_configuration_share),
                onClick = onShareClick
            )

            PreferenceGroupHeader(title = stringResource(R.string.backup_group_restore))
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_restore_24dp),
                title = stringResource(R.string.title_configuration_restore),
                onClick = { showRestoreDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_delete_24dp),
                title = stringResource(R.string.title_profile_storage_cleanup),
                subtitle = stringResource(R.string.summary_profile_storage_cleanup),
                onClick = { showCleanupDialog = true }
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_settings_24dp),
                title = stringResource(R.string.title_webdav_config_setting),
                subtitle = webDavSummary,
                onClick = { showWebDavDialog = true }
            )
            NavigationBarsSpacer()
        }
    }

    if (showBackupDialog) {
        SelectListDialog(
            title = stringResource(R.string.title_configuration_backup),
            options = BackupLocation.entries,
            optionText = { stringResource(it.labelRes) },
            onSelected = { location ->
                showBackupDialog = false
                onBackupOptionSelected(location)
            },
            onDismiss = { showBackupDialog = false }
        )
    }
    if (showRestoreDialog) {
        SelectListDialog(
            title = stringResource(R.string.title_configuration_restore),
            options = BackupLocation.entries,
            optionText = { stringResource(it.labelRes) },
            onSelected = { location ->
                showRestoreDialog = false
                onRestoreOptionSelected(location)
            },
            onDismiss = { showRestoreDialog = false }
        )
    }
    if (showCleanupDialog) {
        DeleteConfirmDialog(
            message = stringResource(R.string.message_profile_storage_cleanup),
            onConfirm = onCleanupProfiles,
            onDismiss = { showCleanupDialog = false }
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

    if (passwordRequest != null) {
        BackupPasswordDialog(
            request = passwordRequest,
            onSubmit = onPasswordSubmit,
            onDismiss = onPasswordDismiss
        )
    }
}

@Composable
private fun BackupPasswordDialog(
    request: BackupPasswordRequest,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Keyed by the request, so a wrong password reopens with an empty field.
    var password by remember(request.key) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(request.titleRes)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(request.hintRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.backup_password_label)) },
                    isError = request.mismatch,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (request.mismatch) {
                    Text(
                        text = stringResource(R.string.toast_backup_wrong_password),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(password.trim()) },
                enabled = request.optional || password.isNotBlank()
            ) {
                Text(stringResource(R.string.backup_password_continue))
            }
        },
        dismissButton = {
            Row {
                if (request.optional) {
                    TextButton(onClick = { onSubmit("") }) {
                        Text(stringResource(R.string.backup_password_none))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
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
        dismissText = stringResource(R.string.action_cancel),
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
