package com.v2ray.ang

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.work.Configuration
import androidx.work.WorkManager
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.compose.ThemeManager
import com.v2ray.ang.handler.SettingsManager
import rikka.shizuku.ShizukuProvider

class AngApplication : Application() {
    companion object {
        lateinit var application: AngApplication
    }

    /**
     * Attaches the base context to the application.
     * @param base The base context.
     */
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        application = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The VPN core process must receive replacement Shizuku Binders after a server restart.
            ShizukuProvider.enableMultiProcessSupport(Application.getProcessName() == packageName)
        }
    }

    private val workManagerConfiguration: Configuration = Configuration.Builder()
        .setDefaultProcessName("${ANG_PACKAGE}:bg")
        .build()

    /**
     * Initializes the application.
     */
    override fun onCreate() {
        super.onCreate()

        MMKV.initialize(this)

        // Initialize WorkManager with the custom configuration
        WorkManager.initialize(this, workManagerConfiguration)

        // Ensure critical preference defaults are present in MMKV early
        SettingsManager.initApp(this)

        // Initialize theme state from MMKV
        ThemeManager.refresh()
    }
}
