package com.v2ray.ang.vm

import android.app.Application
import android.text.TextUtils
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.AssetUrlItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.launch
import java.io.File

class UserAssetUrlViewModel(application: Application) : BaseViewModel(application) {

    sealed interface UserAssetUrlViewModelEvent : ViewModelEvent {
        object SaveSuccess : UserAssetUrlViewModelEvent
        object DeleteSuccess : UserAssetUrlViewModelEvent
    }

    fun saveAssetUrl(editAssetId: String, remarks: String, url: String, extDir: File) {
        val assetList = MmkvManager.decodeAssetUrls()
        if (assetList.any { it.assetUrl.remarks == remarks && it.guid != editAssetId }) {
            toast(R.string.msg_remark_is_duplicate)
            return
        }
        if (TextUtils.isEmpty(remarks) || TextUtils.isEmpty(url)) {
            return
        }

        var assetItem = MmkvManager.decodeAsset(editAssetId)
        var assetId = editAssetId
        if (assetItem != null) {
            val file = extDir.resolve(assetItem.remarks)
            if (file.exists()) {
                try {
                    file.delete()
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to delete asset file: ${file.path}", e)
                }
            }
        } else {
            assetId = Utils.getUuid()
            assetItem = AssetUrlItem()
        }

        assetItem.remarks = remarks
        assetItem.url = url

        MmkvManager.encodeAsset(assetId, assetItem)
        toastSuccess(R.string.toast_success)
        viewModelScope.launch {
            _viewModelEvent.send(UserAssetUrlViewModelEvent.SaveSuccess)
        }
    }

    fun deleteAssetUrl(editAssetId: String) {
        if (editAssetId.isNotEmpty()) {
            MmkvManager.removeAssetUrl(editAssetId)
            viewModelScope.launch {
                _viewModelEvent.send(UserAssetUrlViewModelEvent.DeleteSuccess)
            }
        }
    }
}
