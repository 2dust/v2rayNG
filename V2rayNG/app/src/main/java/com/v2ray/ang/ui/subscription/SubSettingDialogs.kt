package com.v2ray.ang.ui.subscription

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R
import com.v2ray.ang.dto.SubUpdateOptions
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.QRCodeDialog
import com.v2ray.ang.ui.compose.SelectListDialog
import com.v2ray.ang.ui.compose.SettingsSwitchItem

sealed interface SubDialog {
    data object UpdateOptions : SubDialog
    @Immutable
    data class Share(val url: String) : SubDialog
    @Immutable
    data class QrCode(val bitmap: Bitmap) : SubDialog
    @Immutable
    data class ConfirmRemove(val subId: String) : SubDialog
}

@Stable
class SubDialogHost(private val onAction: (SubAction) -> Unit) {
    var current by mutableStateOf<SubDialog?>(null)
        private set

    var confirmRemove: Boolean = false

    val show: (SubDialog) -> Unit = { current = it }
    val dismiss: () -> Unit = { current = null }

    val requestRemove: (String) -> Unit = { subId ->
        if (confirmRemove) current = SubDialog.ConfirmRemove(subId)
        else onAction(SubAction.RemoveConfirmed(subId))
    }
}

@Composable
fun rememberSubDialogHost(onAction: (SubAction) -> Unit): SubDialogHost =
    remember(onAction) { SubDialogHost(onAction) }

@Composable
fun SubDialogs(
    host: SubDialogHost,
    updateOptions: SubUpdateOptions,
    onAction: (SubAction) -> Unit
) {
    when (val dialog = host.current) {
        is SubDialog.Share -> SelectListDialog(
            options = ShareMethod.entries,
            optionText = { method ->
                when (method) {
                    ShareMethod.QR_CODE -> stringResource(R.string.share_subscription_qrcode)
                    ShareMethod.CLIPBOARD -> stringResource(R.string.share_subscription_clipboard)
                }
            },
            onSelected = { method ->
                host.dismiss()
                onAction(SubAction.ShareMethodSelected(method, dialog.url))
            },
            onDismiss = host.dismiss
        )
        is SubDialog.QrCode -> QRCodeDialog(
            bitmap = dialog.bitmap,
            onDismiss = host.dismiss
        )
        is SubDialog.ConfirmRemove -> DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_subscription_group),
            onConfirm = { onAction(SubAction.RemoveConfirmed(dialog.subId)) },
            onDismiss = host.dismiss
        )
        SubDialog.UpdateOptions -> SubUpdateOptionsDialog(
            options = updateOptions,
            onAction = onAction,
            onDismiss = {
                host.dismiss()
                onAction(SubAction.DismissUpdateOptions)
            }
        )
        null -> Unit
    }
}

@Composable
private fun SubUpdateOptionsDialog(
    options: SubUpdateOptions,
    onAction: (SubAction) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column {
                SettingsSwitchItem(
                    title = stringResource(R.string.title_sub_update),
                    checked = options.updateSubscription,
                    onCheckedChange = {
                        onAction(SubAction.UpdateOptionChanged(UpdateOptionField.UPDATE_SUBSCRIPTION, it))
                    }
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_auto_test_after_update_subscription),
                    summary = stringResource(R.string.summary_pref_auto_test_after_update_subscription),
                    checked = options.autoTestAfterUpdate,
                    onCheckedChange = {
                        onAction(SubAction.UpdateOptionChanged(UpdateOptionField.AUTO_TEST_AFTER_UPDATE, it))
                    }
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_auto_remove_invalid_after_test),
                    summary = stringResource(R.string.summary_pref_auto_remove_invalid_after_test),
                    checked = options.autoRemoveInvalid,
                    enabled = options.autoTestAfterUpdate,
                    onCheckedChange = {
                        onAction(SubAction.UpdateOptionChanged(UpdateOptionField.AUTO_REMOVE_INVALID, it))
                    }
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_auto_sort_after_test),
                    summary = stringResource(R.string.summary_pref_auto_sort_after_test),
                    checked = options.autoSortAfterTest,
                    enabled = options.autoTestAfterUpdate,
                    onCheckedChange = {
                        onAction(SubAction.UpdateOptionChanged(UpdateOptionField.AUTO_SORT_AFTER_TEST, it))
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAction(SubAction.ConfirmUpdateOptions) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
