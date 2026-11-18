package com.v2ray.ang.ui.update

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.AppUpdateManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.launch

/**
 * Announces a new release when the app starts. The check is rate limited, honors the pre-release
 * switch of the "Check for update" screen and stays quiet for a version the user postponed.
 */
class AppUpdateViewModel(application: Application) : BaseViewModel(application) {

    val controller = AppUpdateController(
        context = localizedContext,
        scope = viewModelScope,
        rememberPostpone = true,
        onError = { toastError(it) }
    )

    init {
        viewModelScope.launch {
            try {
                AppUpdateManager.checkForAnnouncement()?.let { controller.offer(it) }
            } catch (e: Exception) {
                // Offline or GitHub unreachable: the announcement simply does not appear.
                LogUtil.w(AppConfig.TAG, "Automatic update check failed: ${e.message}")
            }
        }
    }
}
