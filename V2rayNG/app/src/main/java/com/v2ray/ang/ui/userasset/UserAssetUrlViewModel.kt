package com.v2ray.ang.ui.userasset

import androidx.lifecycle.SavedStateHandle
import com.v2ray.ang.R
import com.v2ray.ang.repository.UserAssetRepository
import com.v2ray.ang.ui.AppRoute
import com.v2ray.ang.ui.base.BaseEditViewModel
import com.v2ray.ang.ui.base.BaseResult
import com.v2ray.ang.ui.base.BaseText
import com.v2ray.ang.ui.compose.ToastType
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class UserAssetUrlViewModel(
    private val handle: SavedStateHandle,
    private val repo: UserAssetRepository
) : BaseEditViewModel<UserAssetUrlUiState, UserAssetUrlAction>(
    UserAssetUrlUiState(assetId = handle.get<String>(AppRoute.EXTRA_ASSET_ID).orEmpty())
) {

    /**
     * Read-only slice. The edited id is fixed at construction, so the top bar must not depend on
     * the full state read: otherwise every keystroke in the form rebuilds it.
     */
    val isEdit: StateFlow<Boolean> = MutableStateFlow(state.isEdit).asStateFlow()

    init {
        load()
    }

    /** SavedStateHandle keeps the typed text across process death without extra plumbing. */
    private fun load() = launch(loading = true) {
        val restoredRemarks = handle.get<String>(KEY_REMARKS)
        val restoredUrl = handle.get<String>(KEY_URL)
        if (restoredRemarks != null || restoredUrl != null) {
            setState { copy(remarks = restoredRemarks.orEmpty(), url = restoredUrl.orEmpty()) }
            return@launch
        }

        val item = if (state.isEdit) repo.loadAsset(state.assetId) else null
        val qrCodeUrl = handle.get<String>(AppRoute.EXTRA_ASSET_QRCODE).orEmpty()
        setState {
            when {
                item != null -> copy(remarks = item.remarks, url = item.url)
                qrCodeUrl.isNotEmpty() -> copy(remarks = File(qrCodeUrl).name, url = qrCodeUrl)
                else -> this
            }
        }
    }

    override fun onAction(action: UserAssetUrlAction) {
        when (action) {
            is UserAssetUrlAction.RemarksChanged -> {
                handle[KEY_REMARKS] = action.value
                setState { copy(remarks = action.value) }
            }

            is UserAssetUrlAction.UrlChanged -> {
                handle[KEY_URL] = action.value
                setState { copy(url = action.value) }
            }

            UserAssetUrlAction.Save -> save()
            UserAssetUrlAction.Back -> cancel()

            UserAssetUrlAction.DeleteClicked -> setState { copy(showDeleteDialog = true) }
            UserAssetUrlAction.DialogDismiss -> setState { copy(showDeleteDialog = false) }
            UserAssetUrlAction.DialogConfirm -> {
                setState { copy(showDeleteDialog = false) }
                delete()
            }
        }
    }

    override suspend fun doSave(): BaseResult? {
        val remarks = state.remarks.trim()
        val url = state.url.trim()
        if (remarks.isEmpty()) {
            toastFieldRequired(R.string.sub_setting_remarks)
            return null
        }
        if (url.isEmpty()) {
            toastFieldRequired(R.string.title_url)
            return null
        }
        if (!Utils.isValidUrl(url)) {
            toastError(R.string.toast_invalid_url)
            return null
        }
        if (repo.isRemarkDuplicated(remarks, state.assetId)) {
            toastError(R.string.msg_remark_is_duplicate)
            return null
        }
        val id = repo.saveAsset(state.assetId, remarks, url)
        return BaseResult.Saved(id = id, refreshList = true)
    }

    override suspend fun doDelete(): BaseResult? {
        if (!state.isEdit) return null
        repo.removeAssetUrl(state.assetId)
        return BaseResult.Deleted(id = state.assetId, refreshList = true)
    }

    private fun toastFieldRequired(labelRes: Int) = toast(
        BaseText.of(R.string.toast_field_required, BaseText.of(labelRes)),
        ToastType.ERROR
    )

    private companion object {
        const val KEY_REMARKS = "asset_edit_remarks"
        const val KEY_URL = "asset_edit_url"
    }
}
