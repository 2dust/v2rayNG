package com.v2ray.ang.ui.userasset

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.v2ray.ang.repository.AssetFile
import com.v2ray.ang.ui.base.BaseAction
import com.v2ray.ang.ui.base.BaseEvent
import com.v2ray.ang.ui.base.BaseResult
import com.v2ray.ang.ui.base.BaseUiState

/**
 * Display model of one asset row.
 */
@Immutable
data class AssetRow(
    val guid: String,
    val remarks: String,
    val properties: String,
    /** Built-in and file-imported assets have no editable URL. */
    val editable: Boolean
)

/** Foreground bulk-download progress. Lives in a state slice, never in [UserAssetUiState]. */
@Immutable
data class AssetDownloadProgress(val done: Int, val total: Int) {
    val fraction: Float get() = if (total <= 0) 0f else done.toFloat() / total
}

@Immutable
data class UserAssetUiState(
    val assets: List<AssetRow> = emptyList(),
    val geoSources: List<String> = emptyList(),
    val geoSource: String = "",
    val dialog: UserAssetDialog? = null
) : BaseUiState

sealed interface UserAssetDialog {
    /** Carries the remark so the confirmation text needs no lookup back into the list. */
    @Immutable
    data class ConfirmRemove(val guid: String, val remarks: String) : UserAssetDialog
}

/** The complete set of user intents of the asset list screen. */
sealed interface UserAssetAction : BaseAction {
    data object Back : UserAssetAction
    data class GeoSourceSelected(val value: String) : UserAssetAction

    data object AddFileClicked : UserAssetAction
    data object AddUrlClicked : UserAssetAction
    data object ScanQrCodeClicked : UserAssetAction
    data class FileSelected(val uri: Uri?) : UserAssetAction
    data class QrCodeScanned(val text: String?) : UserAssetAction

    data object DownloadClicked : UserAssetAction
    data class Edit(val guid: String) : UserAssetAction
    data class RemoveClicked(val guid: String) : UserAssetAction
    data object DialogConfirm : UserAssetAction
    data object DialogDismiss : UserAssetAction

    data class ResultReceived(val result: BaseResult) : UserAssetAction
}

/** Platform events; consumed by the screen through `LocalPlatformActions`. */
sealed interface UserAssetEvent : BaseEvent.Platform {
    data object PickFile : UserAssetEvent
    data object ScanQrCode : UserAssetEvent
}

// ===== Asset URL editor =====

@Immutable
data class UserAssetUrlUiState(
    val assetId: String = "",
    val remarks: String = "",
    val url: String = "",
    val showDeleteDialog: Boolean = false
) : BaseUiState {
    val isEdit: Boolean get() = assetId.isNotEmpty()
}

sealed interface UserAssetUrlAction : BaseAction {
    data class RemarksChanged(val value: String) : UserAssetUrlAction
    data class UrlChanged(val value: String) : UserAssetUrlAction
    data object Save : UserAssetUrlAction
    data object Back : UserAssetUrlAction
    data object DeleteClicked : UserAssetUrlAction
    data object DialogConfirm : UserAssetUrlAction
    data object DialogDismiss : UserAssetUrlAction
}

// ====== Mapping helpers =====

/** Projects the domain models onto the stable model the list renders. */
fun List<AssetFile>.toAssetRows(): List<AssetRow> = map { assetFile ->
    AssetRow(
        guid = assetFile.guid,
        remarks = assetFile.remarks,
        properties = assetFile.properties,
        editable = !assetFile.locked && !assetFile.isLocalFile
    )
}
