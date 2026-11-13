package com.v2ray.ang.ui.main

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.QRCodeDialog
import com.v2ray.ang.ui.compose.TvDeleteConfirmDialog
import com.v2ray.ang.ui.compose.TvQRCodeDialog

data class ServerDeleteTarget(
    val guid: String,
    val profileName: String,
) {
    companion object {
        val Saver = listSaver<ServerDeleteTarget?, String>(
            save = { target -> target?.let { listOf(it.guid, it.profileName) }.orEmpty() },
            restore = { ServerDeleteTarget(it[0], it[1]) },
        )
    }
}

private sealed interface MainDialog {
    data class BulkDelete(val target: BulkDeleteTarget) : MainDialog
    data class Share(val guid: String, val profile: ProfileItem, val more: Boolean, val restoreFocus: () -> Boolean) : MainDialog
}

internal class MainDialogActions(
    val bulkDelete: (BulkDeleteTarget) -> Unit,
    val share: (String, ProfileItem, Boolean, () -> Boolean) -> Unit,
    val remove: (String, String) -> Unit
)

@Composable
internal fun MainDialogs(
    confirmRemove: Boolean,
    shareQRCodeBitmap: Bitmap?,
    onAction: (MainAction) -> Unit,
    onRestoreFocus: ((() -> Boolean) -> Unit)? = null,
    content: @Composable (MainDialogActions) -> Unit
) {
    var dialog by remember { mutableStateOf<MainDialog?>(null) }
    var deleteTarget by rememberSaveable(stateSaver = ServerDeleteTarget.Saver) {
        mutableStateOf<ServerDeleteTarget?>(null)
    }
    val requestRemove: (String, String) -> Unit = { guid, name ->
        if (confirmRemove) deleteTarget = ServerDeleteTarget(guid, name)
        else onAction(MainAction.RemoveServer(guid))
    }

    val television = onRestoreFocus != null
    when (val current = dialog) {
        is MainDialog.BulkDelete -> MainDeleteDialog(
            television, stringResource(current.target.messageRes), null,
            onConfirm = { onAction(current.target.action) }, onDismiss = { dialog = null }
        )
        is MainDialog.Share -> ShareMethodDialog(
            guid = current.guid,
            profile = current.profile,
            more = current.more,
            television = television,
            onDismiss = {
                dialog = null
                onRestoreFocus?.invoke(current.restoreFocus)
            },
            onActionSelected = { dialog = null },
            onAction = onAction,
            onRemove = requestRemove
        )
        null -> Unit
    }
    deleteTarget?.let { target ->
        MainDeleteDialog(
            television, stringResource(R.string.confirm_delete_profile), target.profileName,
            onConfirm = { onAction(MainAction.RemoveServer(target.guid)) },
            onDismiss = { deleteTarget = null }
        )
    }
    if (shareQRCodeBitmap != null) {
        val dismiss = { onAction(MainAction.DismissQRCodeDialog) }
        if (television) TvQRCodeDialog(shareQRCodeBitmap, dismiss) else QRCodeDialog(shareQRCodeBitmap, dismiss)
    }

    content(MainDialogActions(
        bulkDelete = { dialog = MainDialog.BulkDelete(it) },
        share = { guid, profile, more, restore -> dialog = MainDialog.Share(guid, profile, more, restore) },
        remove = requestRemove
    ))
}

@Composable
private fun MainDeleteDialog(television: Boolean, message: String, item: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (television) TvDeleteConfirmDialog(message, onConfirm, onDismiss, item)
    else DeleteConfirmDialog(message, onConfirm, onDismiss, item)
}
