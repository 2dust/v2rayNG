package com.v2ray.ang.ui.shortcut

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.ui.base.BaseComponentActivity

class ScStopActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        LaunchedEffect(Unit) {
            moveTaskToBack(true)
            if (CoreServiceManager.isRunning()) {
                CoreServiceManager.stopVService(this@ScStopActivity)
            }
            finish()
        }
    }
}
