package com.v2ray.ang.ui.main

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.ui.compose.TvActionDialog
import com.v2ray.ang.ui.compose.TvDeleteConfirmDialog
import com.v2ray.ang.ui.compose.TvQRCodeDialog

internal enum class TvBulkDeleteTarget(@StringRes val messageRes: Int, val action: MainAction) {
    Duplicate(R.string.confirm_delete_duplicate_profiles, MainAction.RemoveDuplicateServers),
    Invalid(R.string.confirm_delete_invalid_profiles, MainAction.RemoveInvalidServers)
}

private data class TvShareTarget(val guid: String, val profile: ProfileItem, val more: Boolean, val restoreFocus: () -> Boolean)

internal class TvMainDialogActions(
    val bulkDelete: (TvBulkDeleteTarget) -> Unit,
    val share: (String, ProfileItem, Boolean, () -> Boolean) -> Unit,
    val remove: (String, String) -> Unit
)

@Composable
internal fun TvMainDialogs(
    shareQRCodeBitmap: Bitmap?,
    onAction: (MainAction) -> Unit,
    onRestoreFocus: (() -> Boolean) -> Unit,
    content: @Composable (TvMainDialogActions) -> Unit
) {
    var bulkDelete by remember { mutableStateOf<TvBulkDeleteTarget?>(null) }
    var shareTarget by remember { mutableStateOf<TvShareTarget?>(null) }
    var deleteTarget by rememberSaveable(stateSaver = ServerDeleteTarget.Saver) {
        mutableStateOf<ServerDeleteTarget?>(null)
    }
    val requestRemove: (String, String) -> Unit = { guid, name -> deleteTarget = ServerDeleteTarget(guid, name) }

    bulkDelete?.let { target ->
        TvDeleteConfirmDialog(
            message = stringResource(target.messageRes),
            onConfirm = { onAction(target.action) },
            onDismiss = { bulkDelete = null }
        )
    }
    shareTarget?.let { target ->
        TvActionDialog(
            serverMenuActions(target.profile.configType.isComplexType(), target.more),
            { stringResource(it.labelRes) },
            { action ->
                shareTarget = null
                action.dispatch(target.guid, target.profile, onAction, requestRemove)
            },
            {
                shareTarget = null
                onRestoreFocus(target.restoreFocus)
            }
        )
    }
    deleteTarget?.let { target ->
        TvDeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_profile),
            itemName = target.profileName,
            onConfirm = { onAction(MainAction.RemoveServer(target.guid)) },
            onDismiss = { deleteTarget = null }
        )
    }
    if (shareQRCodeBitmap != null) {
        TvQRCodeDialog(shareQRCodeBitmap) { onAction(MainAction.DismissQRCodeDialog) }
    }

    content(TvMainDialogActions(
        bulkDelete = { bulkDelete = it },
        share = { guid, profile, more, restore -> shareTarget = TvShareTarget(guid, profile, more, restore) },
        remove = requestRemove
    ))
}
