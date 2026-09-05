package com.v2ray.ang.ui.userasset

import android.net.Uri
import com.v2ray.ang.R
import com.v2ray.ang.repository.AssetImportResult
import com.v2ray.ang.repository.UserAssetRepository
import com.v2ray.ang.ui.AppRoute
import com.v2ray.ang.ui.base.BaseResult
import com.v2ray.ang.ui.base.BaseText
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Geo-file downloads run with a determinate top-bar progress and a final result toast.
 */
class UserAssetViewModel(
    private val repo: UserAssetRepository
) : BaseViewModel<UserAssetUiState, UserAssetAction>(UserAssetUiState()) {

    /** Only decides which [BaseResult] Back answers with. */
    private var changed = false

    /** Back can be pressed again before the Activity actually finishes. */
    private var finishing = false

    private val _downloadProgress = MutableStateFlow<AssetDownloadProgress?>(null)

    /**
     * Read-only state slice. Progress ticks once per file.
     */
    val downloadProgress: StateFlow<AssetDownloadProgress?> = _downloadProgress.asStateFlow()

    init {
        refresh()
    }

    override fun onAction(action: UserAssetAction) {
        when (action) {
            UserAssetAction.Back -> finish()

            is UserAssetAction.GeoSourceSelected -> selectGeoSource(action.value)

            UserAssetAction.AddFileClicked -> platform(UserAssetEvent.PickFile)
            UserAssetAction.ScanQrCodeClicked -> platform(UserAssetEvent.ScanQrCode)
            UserAssetAction.AddUrlClicked -> navigate(AppRoute.UserAssetUrl())

            // A null uri means the picker was dismissed, which is not a failure worth reporting.
            is UserAssetAction.FileSelected -> action.uri?.let(::importFile)
            is UserAssetAction.QrCodeScanned -> importFromQrCode(action.text)

            UserAssetAction.DownloadClicked -> download()

            is UserAssetAction.Edit -> navigate(AppRoute.UserAssetUrl(assetId = action.guid))

            is UserAssetAction.RemoveClicked -> askRemove(action.guid)

            UserAssetAction.DialogConfirm -> confirmDialog()
            UserAssetAction.DialogDismiss -> setState { copy(dialog = null) }

            is UserAssetAction.ResultReceived -> if (action.result.isOk) {
                changed = true
                refresh()
            }
        }
    }

    // ===== Loading =====

    private fun refresh() = launch(loading = true) { reload() }

    private suspend fun reload() {
        val snapshot = repo.loadSnapshot()
        val rows = snapshot.files.toAssetRows()
        setState {
            copy(
                assets = rows,
                geoSources = snapshot.geoSources,
                geoSource = snapshot.geoSource
            )
        }
    }

    private fun selectGeoSource(value: String) {
        if (value == state.geoSource) return
        launch(loading = true) {
            repo.setGeoSource(value)
            changed = true
            reload()
        }
    }

    // ===== Import =====

    private fun importFile(uri: Uri) = launch(loading = true) {
        when (repo.importFile(uri)) {
            AssetImportResult.SUCCESS -> {
                changed = true
                reload()
                toastSuccess()
            }

            AssetImportResult.DUPLICATE -> toastError(R.string.msg_remark_is_duplicate)
            AssetImportResult.FAILURE -> toastError(R.string.toast_asset_copy_failed)
        }
    }

    private fun importFromQrCode(text: String?) {
        if (!Utils.isValidUrl(text)) {
            toastError(R.string.toast_invalid_url)
            return
        }
        navigate(AppRoute.UserAssetUrl(qrCodeUrl = text.orEmpty()))
    }

    // ===== Delete =====

    private fun askRemove(guid: String) {
        val target = state.assets.firstOrNull { it.guid == guid } ?: return
        setState { copy(dialog = UserAssetDialog.ConfirmRemove(target.guid, target.remarks)) }
    }

    private fun confirmDialog() {
        when (val dialog = state.dialog) {
            is UserAssetDialog.ConfirmRemove -> {
                setState { copy(dialog = null) }
                remove(dialog.guid, dialog.remarks)
            }

            null -> Unit
        }
    }

    private fun remove(guid: String, remarks: String) = launch(loading = true) {
        repo.removeAssetWithFile(guid, remarks)
        changed = true
        reload()
        toastSuccess()
    }

    // ===== Download =====

    private fun download() = launch(loading = true) {
        toastInfo(R.string.msg_downloading_content)
        val success = try {
            repo.downloadAll { done, total ->
                _downloadProgress.value = AssetDownloadProgress(done, total)
            }
        } finally {
            // Must clear on failure and on cancellation too, or the bar stays stuck at the last tick.
            _downloadProgress.value = null
        }
        if (success > 0) {
            changed = true
            toast(BaseText.of(R.string.title_update_asset_count, success))
        } else {
            toastError()
        }
        reload()
    }

    // ===== Finishing =====

    private fun finish() {
        if (finishing) return
        finishing = true
        finishWith(
            if (changed) BaseResult.Changed(refreshList = true) else BaseResult.Cancelled
        )
    }
}
