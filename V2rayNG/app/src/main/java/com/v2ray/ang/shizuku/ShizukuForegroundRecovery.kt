package com.v2ray.ang.shizuku

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.helper.MessageHelper

/** Forwards app-foreground events needed to recover a replaced Shizuku Binder. */
internal object ShizukuForegroundRecovery : Application.ActivityLifecycleCallbacks {

    fun register(application: Application) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            Application.getProcessName() != application.packageName ||
            !application.resources.getBoolean(R.bool.shizuku_tethering_enabled)
        ) return
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        // Android 14+ can defer Shizuku's replacement-Binder notification while the app process is
        // cached. Remove this foreground retry only when the minimum supported Shizuku release
        // delivers that callback to cached non-provider processes on every supported Android API.
        MessageHelper.sendMsg2Service(activity, AppConfig.MSG_SHIZUKU_APP_FOREGROUND, "")
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
