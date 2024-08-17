package com.v2ray.ang

import android.content.Context
import androidx.multidex.MultiDexApplication
import androidx.work.Configuration
import androidx.work.WorkManager
import com.tencent.mmkv.MMKV
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
        .setDefaultProcessName("${BuildConfig.APPLICATION_ID}:bg")
        .build()

    override fun onCreate() {
        super.onCreate()

//        LeakCanary.install(this)

//        val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
//        firstRun = defaultSharedPreferences.getInt(PREF_LAST_VERSION, 0) != BuildConfig.VERSION_CODE
//        if (firstRun)
//            defaultSharedPreferences.edit().putInt(PREF_LAST_VERSION, BuildConfig.VERSION_CODE).apply()

        //Logger.init().logLevel(if (BuildConfig.DEBUG) LogLevel.FULL else LogLevel.NONE)
        MMKV.initialize(this)

        Utils.setNightMode(application)
        // Initialize WorkManager with the custom configuration
        WorkManager.initialize(this, workManagerConfiguration)
    }
}
