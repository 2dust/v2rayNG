package com.dalulong.app.ui.checkupdate

import android.app.Application
import com.dalulong.app.AppConfig
import com.dalulong.app.R
import com.dalulong.app.dto.CheckUpdateResult
import com.dalulong.app.handler.MmkvManager
import com.dalulong.app.handler.UpdateCheckerManager
import com.dalulong.app.ui.base.BaseViewModel
import com.dalulong.app.util.LogUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CheckUpdateViewModel(application: Application) : BaseViewModel(application) {

    private val _checkPreRelease = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_CHECK_UPDATE_PRE_RELEASE, false)
    )
    val checkPreRelease: StateFlow<Boolean> = _checkPreRelease.asStateFlow()

    private val _updateResult = MutableStateFlow<CheckUpdateResult?>(null)
    val updateResult: StateFlow<CheckUpdateResult?> = _updateResult.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    fun toggleCheckPreRelease(enabled: Boolean) {
        _checkPreRelease.value = enabled
        MmkvManager.encodeSettings(AppConfig.PREF_CHECK_UPDATE_PRE_RELEASE, enabled)
    }

    fun checkForUpdates() {
        launchLoading {
            toast(R.string.update_checking_for_update)
            try {
                val result = UpdateCheckerManager.checkForUpdate(_checkPreRelease.value)
                if (result.hasUpdate) {
                    _updateResult.value = result
                    _showUpdateDialog.value = true
                } else {
                    toastSuccess(R.string.update_already_latest_version)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to check for updates: ${e.message}")
                if (e.message == null) {
                    toastError(R.string.toast_failure)
                } else {
                    toastError(e.message.orEmpty())
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }
}