package com.v2ray.ang.ui

import android.os.Bundle
import com.v2ray.ang.R
import com.v2ray.ang.handler.V2RayServiceManager

class ScStopActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moveTaskToBack(true)

        setContentView(R.layout.activity_none)

        if (V2RayServiceManager.isRunning()) {
            V2RayServiceManager.stopVService(this)
        }
        finish()
    }
}
