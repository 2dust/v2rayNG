package com.v2ray.ang.vm

import android.app.Application
import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.util.Utils

class AboutViewModel(application: Application) : BaseViewModel(application) {

    val versionText: String by lazy {
        val libVersion = CoreNativeManager.getLibVersion()
        "v${BuildConfig.VERSION_NAME} ($libVersion)"
    }

    val appIdText: String = BuildConfig.APPLICATION_ID

    fun openSourceCodeUrl(context: Context) {
        Utils.openUri(context, AppConfig.APP_URL)
    }

    fun openIssuesUrl(context: Context) {
        Utils.openUri(context, AppConfig.APP_ISSUES_URL)
    }

    fun openTelegramChannelUrl(context: Context) {
        Utils.openUri(context, AppConfig.TG_CHANNEL_URL)
    }

    fun openPrivacyPolicyUrl(context: Context) {
        Utils.openUri(context, AppConfig.APP_PRIVACY_POLICY)
    }
}
