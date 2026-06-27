package com.v2ray.ang.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager

class ScStartActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moveTaskToBack(true)

        setContent { }

        if (!CoreServiceManager.isRunning()) {
            CoreServiceManager.startVServiceFromToggle(this)
        }
        finish()
    }
}

