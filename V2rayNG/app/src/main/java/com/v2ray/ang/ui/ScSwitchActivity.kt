package com.v2ray.ang.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.MyContextWrapper

class ScSwitchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moveTaskToBack(true)
        if (CoreServiceManager.isRunning()) {
            CoreServiceManager.stopVService(this)
        } else {
            CoreServiceManager.startVServiceFromToggle(this)
        }
        finish()
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(MyContextWrapper.wrap(newBase ?: return, SettingsManager.getLocale()))
    }
}
