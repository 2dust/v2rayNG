package com.v2ray.ang.ui.main

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.v2ray.ang.ui.compose.TvActionDialog
import com.v2ray.ang.ui.compose.TvDeleteConfirmDialog
import com.v2ray.ang.ui.compose.TvQRCodeDialog

internal enum class TvBulkDeleteTarget(@StringRes val messageRes: Int, val action: MainAction) {
    Duplicate(R.string.confirm_delete_duplicate_profiles, MainAction.RemoveDuplicateServers),
    Invalid(R.string.confirm_delete_invalid_profiles, MainAction.RemoveInvalidServers)
}

private data class TvShareTarget(val profile: ProfileItem, val origin: TvMainFocusTarget.Profile, val neighbor: String?)

private data class TvDeleteTarget(val name: String, val origin: TvMainFocusTarget.Profile, val neighbor: String?) {
    companion object {
        val Saver = listSaver<TvDeleteTarget?, String>(
            save = { target -> target?.let { listOf(it.name, it.origin.guid, checkNotNull(it.origin.action).name, it.neighbor.orEmpty()) }.orEmpty() },
            restore = { TvDeleteTarget(it[0], TvMainFocusTarget.Profile(it[1], ProfileRowAction.valueOf(it[2])), it[3].ifEmpty { null }) }
        )
    }
}

internal class TvMainDialogActions(
    val bulkDelete: (TvBulkDeleteTarget) -> Unit,
    val profileAction: (ProfileRowAction, ServerRowUiModel, String?) -> Unit
)

@Composable
internal fun TvMainDialogs(
    shareQRCodeBitmap: Bitmap?,
    selectedGuid: String?,
    onAction: (MainAction) -> Unit,
    onEdit: (MainAction.EditServer, TvMainFocusTarget.Profile) -> Unit,
    onRestoreFocus: (TvMainFocusTarget) -> Unit,
    content: @Composable (TvMainDialogActions) -> Unit
) {
    var bulkDelete by remember { mutableStateOf<TvBulkDeleteTarget?>(null) }
    var shareTarget by remember { mutableStateOf<TvShareTarget?>(null) }
    var deleteTarget by rememberSaveable(stateSaver = TvDeleteTarget.Saver) { mutableStateOf<TvDeleteTarget?>(null) }
    var qrOrigin by rememberSaveable(stateSaver = TvMainFocusTarget.Profile.Saver) {
        mutableStateOf<TvMainFocusTarget.Profile?>(null)
    }
    // ShareQRCode publishes its bitmap synchronously. A missing bitmap means the dialog closed
    // or the code could not be generated; both should return to the originating row.
    LaunchedEffect(shareQRCodeBitmap, qrOrigin) {
        if (shareQRCodeBitmap == null) qrOrigin?.let {
            qrOrigin = null
            onRestoreFocus(it)
        }
    }

    bulkDelete?.let { target ->
        TvDeleteConfirmDialog(
            message = stringResource(target.messageRes),
            onConfirm = { bulkDelete = null; onAction(target.action) },
            onDismiss = { bulkDelete = null }
        )
    }
    shareTarget?.let { target ->
        TvActionDialog(
            serverMenuActions(target.profile.configType.isComplexType(), target.origin.action == ProfileRowAction.More),
            { stringResource(it.labelRes) },
            { action ->
                shareTarget = null
                action.dispatch(target.origin.guid, target.profile, { selectedAction ->
                    when (selectedAction) {
                        is MainAction.EditServer -> onEdit(selectedAction, target.origin)
                        is MainAction.ShareQRCode -> {
                            qrOrigin = target.origin
                            onAction(selectedAction)
                        }
                        else -> {
                            onAction(selectedAction)
                            onRestoreFocus(target.origin)
                        }
                    }
                }) { _, name -> deleteTarget = TvDeleteTarget(name, target.origin, target.neighbor) }
            },
            {
                shareTarget = null
                onRestoreFocus(target.origin)
            }
        )
    }
    deleteTarget?.let { target ->
        fun close(confirmed: Boolean) {
            deleteTarget = null
            val origin = target.origin
            onRestoreFocus(if (confirmed && origin.guid != selectedGuid) TvMainFocusTarget.AfterRemoval(origin, target.neighbor) else origin)
            if (confirmed) onAction(MainAction.RemoveServer(origin.guid))
        }
        TvDeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_profile),
            itemName = target.name,
            onConfirm = { close(true) },
            onDismiss = { close(false) }
        )
    }
    if (shareQRCodeBitmap != null) {
        TvQRCodeDialog(shareQRCodeBitmap) { onAction(MainAction.DismissQRCodeDialog) }
    }

    content(TvMainDialogActions(
        bulkDelete = { bulkDelete = it },
        profileAction = { action, row, neighbor ->
            val origin = TvMainFocusTarget.Profile(row.guid, action)
            when (action) {
                ProfileRowAction.More, ProfileRowAction.Share -> shareTarget = TvShareTarget(row.profile, origin, neighbor)
                ProfileRowAction.Edit -> onEdit(MainAction.EditServer(row.guid, row.profile), origin)
                ProfileRowAction.Delete -> deleteTarget = TvDeleteTarget(row.remarks, origin, neighbor)
            }
        }
    ))
}
