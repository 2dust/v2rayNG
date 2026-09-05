package com.v2ray.ang.ui.backup

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTheme
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.InputDialog
import com.v2ray.ang.ui.compose.InputField
import com.v2ray.ang.ui.compose.SelectListDialog

@Immutable
sealed interface BackupDialog {
    data class PickChannel(val restoring: Boolean) : BackupDialog
    data object WebDav : BackupDialog
    data object CleanupConfirmation : BackupDialog
}

/** Dialog visibility is UI-local state; exposing `show`/`dismiss` as fields keeps them stable. */
@Stable
class BackupDialogHost {
    var current by mutableStateOf<BackupDialog?>(null)
        private set

    val show: (BackupDialog) -> Unit = { current = it }
    val dismiss: () -> Unit = { current = null }
}

@Composable
fun BackupDialogs(
    dialog: BackupDialog?,
    draft: WebDavForm,
    onDismiss: () -> Unit,
    onAction: (BackupAction) -> Unit
) = when (dialog) {
    null -> Unit

    is BackupDialog.PickChannel -> ChannelDialog(
        restoring = dialog.restoring,
        onDismiss = onDismiss,
        onAction = onAction
    )

    BackupDialog.WebDav -> WebDavDialog(
        form = draft,
        onDismiss = onDismiss,
        onAction = onAction
    )

    // ConfirmDialog already calls onDismiss after onConfirm.
    BackupDialog.CleanupConfirmation -> DeleteConfirmDialog(
        message = stringResource(R.string.message_profile_storage_cleanup),
        onConfirm = { onAction(BackupAction.CleanupConfirmed) },
        onDismiss = onDismiss
    )
}

@Composable
private fun ChannelDialog(
    restoring: Boolean,
    onDismiss: () -> Unit,
    onAction: (BackupAction) -> Unit
) {
    val onSelected = remember(onDismiss, onAction) {
        { channel: BackupChannel ->
            onDismiss()
            onAction(BackupAction.ChannelSelected(channel))
        }
    }
    SelectListDialog(
        options = BackupChannel.entries,
        optionText = { channel ->
            when (channel) {
                BackupChannel.LOCAL -> stringResource(R.string.backup_location_local)
                BackupChannel.WEBDAV -> stringResource(R.string.backup_location_webdav)
            }
        },
        onSelected = onSelected,
        onDismiss = onDismiss,
        title = stringResource(
            if (restoring) {
                R.string.title_configuration_restore
            } else {
                R.string.title_configuration_backup
            }
        )
    )
}

/** Built on the shared [InputDialog]; it already supplies `appFieldColors()` and the spacing. */
@Composable
private fun WebDavDialog(
    form: WebDavForm,
    onDismiss: () -> Unit,
    onAction: (BackupAction) -> Unit
) {
    val callbacks = remember(onAction) { WebDavCallbacks(onAction) }
    val masked = remember { PasswordVisualTransformation() }
    val urlLabel = stringResource(R.string.title_webdav_url)
    val userLabel = stringResource(R.string.title_webdav_user)
    val passLabel = stringResource(R.string.title_webdav_pass)
    val pathLabel = stringResource(R.string.title_webdav_remote_path)

    val fields = remember(form, callbacks, urlLabel, userLabel, passLabel, pathLabel) {
        listOf(
            InputField(
                label = urlLabel,
                value = form.baseUrl,
                keyboardType = KeyboardType.Uri,
                onValueChange = callbacks.onUrlChange
            ),
            InputField(
                label = userLabel,
                value = form.username,
                onValueChange = callbacks.onUsernameChange
            ),
            InputField(
                label = passLabel,
                value = form.password,
                keyboardType = KeyboardType.Password,
                visualTransformation = masked,
                onValueChange = callbacks.onPasswordChange
            ),
            InputField(
                label = pathLabel,
                value = form.remotePath,
                onValueChange = callbacks.onPathChange
            )
        )
    }
    val onConfirm = remember(onDismiss, onAction) {
        {
            onAction(BackupAction.WebDavSaved)
            onDismiss()
        }
    }

    InputDialog(
        title = stringResource(R.string.title_webdav_config_setting),
        fields = fields,
        confirmText = stringResource(R.string.menu_item_save_config),
        dismissText = stringResource(R.string.action_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Stable
private class WebDavCallbacks(onAction: (BackupAction) -> Unit) {
    val onUrlChange: (String) -> Unit =
        { onAction(BackupAction.WebDavFieldChanged(WebDavField.URL, it)) }
    val onUsernameChange: (String) -> Unit =
        { onAction(BackupAction.WebDavFieldChanged(WebDavField.USERNAME, it)) }
    val onPasswordChange: (String) -> Unit =
        { onAction(BackupAction.WebDavFieldChanged(WebDavField.PASSWORD, it)) }
    val onPathChange: (String) -> Unit =
        { onAction(BackupAction.WebDavFieldChanged(WebDavField.PATH, it)) }
}

@Preview(showBackground = true)
@Composable
private fun WebDavDialogPreview() = AppTheme {
    WebDavDialog(
        form = WebDavForm(
            baseUrl = "https://dav.example.com/remote.php/dav",
            username = "alice",
            password = "secret",
            remotePath = "/v2rayNG"
        ),
        onDismiss = {},
        onAction = {}
    )
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChannelDialogPreview() = AppTheme {
    ChannelDialog(restoring = true, onDismiss = {}, onAction = {})
}
