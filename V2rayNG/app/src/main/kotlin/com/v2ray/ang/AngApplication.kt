package com.v2ray.ang

import android.content.Context
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.multidex.MultiDexApplication
import androidx.preference.PreferenceManager
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import com.tencent.mmkv.MMKV
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import androidx.lifecycle.lifecycleScope
class AngApplication : MultiDexApplication() {
    companion object {
        const val PREF_LAST_VERSION = "pref_last_version"
        lateinit var appContext: Context
                private set

    }

//    var update = false
//        private set

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
//        LeakCanary.install(this)
        FirebaseApp.initializeApp(this);
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
//        Thread.setDefaultUncaughtExceptionHandler(ExceptionHandler(this))
        Firebase.messaging.subscribeToTopic("all")

        val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val update = defaultSharedPreferences.getInt(PREF_LAST_VERSION, 0) != BuildConfig.VERSION_CODE
        if (update) {
            copyAssets()
            defaultSharedPreferences.edit().putInt(PREF_LAST_VERSION, BuildConfig.VERSION_CODE).apply()
        }

        //Logger.init().logLevel(if (BuildConfig.DEBUG) LogLevel.FULL else LogLevel.NONE)
        MMKV.initialize(this)
        MmkvManager.getDefaultSubscription()
    }


    private fun copyAssets() {
            val extFolder = Utils.userAssetPath(this)
//        lifecycleScope.launch(Dispatchers.IO){
            try {
                val geo = arrayOf("geosite.dat", "geoip.dat")
                assets.list("")
                    ?.filter { geo.contains(it) }
                    ?.filter { !File(extFolder, it).exists() || File(extFolder, it).lastModified()<File(it).lastModified()}
                    ?.forEach {
                        val target = File(extFolder, it)
                        assets.open(it).use { input ->
                            FileOutputStream(target).use { output ->
                                input.copyTo(output)
                            }
                        }
                        Log.i(AppConfig.ANG_PACKAGE, "Copied from apk assets folder to ${target.absolutePath}")
                    }
            } catch (e: Exception) {
                Log.e(AppConfig.ANG_PACKAGE, "asset copy failed", e)
            }

    }
}
