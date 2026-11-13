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
import com.v2ray.ang.extension.isComplexType

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
    deleteDialog: @Composable (message: String, itemName: String?, confirm: () -> Unit, dismiss: () -> Unit) -> Unit,
    shareDialog: @Composable (List<ServerMenuAction>, (ServerMenuAction) -> Unit, () -> Unit) -> Unit,
    qrCodeDialog: @Composable (Bitmap, () -> Unit) -> Unit,
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

    when (val current = dialog) {
        is MainDialog.BulkDelete -> deleteDialog(
            stringResource(current.target.messageRes), null, { onAction(current.target.action) }, { dialog = null }
        )
        is MainDialog.Share -> {
            val options = serverMenuActions(current.profile.configType.isComplexType(), current.more)
            shareDialog(
                options,
                { action ->
                    dialog = null
                    when (action) {
                        ServerMenuAction.ShareQRCode -> onAction(MainAction.ShareQRCode(current.guid))
                        ServerMenuAction.ShareClipboard -> onAction(MainAction.ShareClipboard(current.guid))
                        ServerMenuAction.ShareFullContent -> onAction(MainAction.ShareFullContent(current.guid))
                        ServerMenuAction.Edit -> onAction(MainAction.EditServer(current.guid, current.profile))
                        ServerMenuAction.Delete -> requestRemove(current.guid, current.profile.remarks)
                    }
                },
                {
                    dialog = null
                    onRestoreFocus?.invoke(current.restoreFocus)
                }
            )
        }
        null -> Unit
    }
    deleteTarget?.let { target ->
        deleteDialog(
            stringResource(R.string.confirm_delete_profile), target.profileName,
            { onAction(MainAction.RemoveServer(target.guid)) }, { deleteTarget = null }
        )
    }
    if (shareQRCodeBitmap != null) {
        qrCodeDialog(shareQRCodeBitmap) { onAction(MainAction.DismissQRCodeDialog) }
    }

    content(MainDialogActions(
        bulkDelete = { dialog = MainDialog.BulkDelete(it) },
        share = { guid, profile, more, restore -> dialog = MainDialog.Share(guid, profile, more, restore) },
        remove = requestRemove
    ))
}
