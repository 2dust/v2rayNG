package com.v2ray.ang

import android.app.Application
//import com.squareup.leakcanary.LeakCanary
import com.v2ray.ang.util.AngConfigManager
import me.dozen.dpreference.DPreference
import org.jetbrains.anko.defaultSharedPreferences

class AngApplication : Application() {
    companion object {
        const val PREF_LAST_VERSION = "pref_last_version"
    }

    var firstRun = false
        private set

    val defaultDPreference by lazy { DPreference(this, packageName + "_preferences") }

    override fun onCreate() {
        super.onCreate()

//        LeakCanary.install(this)

        firstRun = defaultSharedPreferences.getInt(PREF_LAST_VERSION, 0) != BuildConfig.VERSION_CODE
        if (firstRun)
            defaultSharedPreferences.edit().putInt(PREF_LAST_VERSION, BuildConfig.VERSION_CODE).apply()

        //Logger.init().logLevel(if (BuildConfig.DEBUG) LogLevel.FULL else LogLevel.NONE)
        AngConfigManager.inject(this)
    }
}
