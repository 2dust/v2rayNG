package com.v2ray.ang

import android.content.Context
import androidx.multidex.MultiDexApplication
import androidx.preference.PreferenceManager
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import com.tencent.mmkv.MMKV
import com.v2ray.ang.util.MmkvManager

class AngApplication : MultiDexApplication() {
    companion object {
        const val PREF_LAST_VERSION = "pref_last_version"
        lateinit var appContext: Context
                private set

    }

    var firstRun = false
        private set

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
//        LeakCanary.install(this)
        FirebaseApp.initializeApp(this);
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        Thread.setDefaultUncaughtExceptionHandler(ExceptionHandler(this))
        Firebase.messaging.subscribeToTopic("all")

        val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        firstRun = defaultSharedPreferences.getInt(PREF_LAST_VERSION, 0) != BuildConfig.VERSION_CODE
        if (firstRun)
            defaultSharedPreferences.edit().putInt(PREF_LAST_VERSION, BuildConfig.VERSION_CODE).apply()

        //Logger.init().logLevel(if (BuildConfig.DEBUG) LogLevel.FULL else LogLevel.NONE)
        MMKV.initialize(this)
        MmkvManager.getDefaultSubscription()
    }
}
