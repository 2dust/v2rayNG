package com.v2ray.ang

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.multidex.MultiDexApplication
import androidx.work.Configuration
import androidx.work.WorkManager
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.Utils

class AngApplication : MultiDexApplication() {
    companion object {
        //const val PREF_LAST_VERSION = "pref_last_version"
        lateinit var application: AngApplication
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        application = this
    }

    private val workManagerConfiguration: Configuration = Configuration.Builder()
        .setDefaultProcessName("${ANG_PACKAGE}:bg")
        .build()

    override fun onCreate() {
        super.onCreate()

//        LeakCanary.install(this)

//        val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
//        firstRun = defaultSharedPreferences.getInt(PREF_LAST_VERSION, 0) != BuildConfig.VERSION_CODE
//        if (firstRun)
//            defaultSharedPreferences.edit().putInt(PREF_LAST_VERSION, BuildConfig.VERSION_CODE).apply()

        MMKV.initialize(this)

        Utils.setNightMode()
        // Initialize WorkManager with the custom configuration
        WorkManager.initialize(this, workManagerConfiguration)

        SettingsManager.initRoutingRulesets(this)
    }

}
