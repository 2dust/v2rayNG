package com.dalulong.app.ui.shortcut

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.dalulong.app.core.CoreServiceManager
import com.dalulong.app.ui.base.BaseComponentActivity

class ScSwitchActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        LaunchedEffect(Unit) {
            moveTaskToBack(true)
            if (CoreServiceManager.isRunning()) {
                CoreServiceManager.stopVService(this@ScSwitchActivity)
            } else {
                CoreServiceManager.startVServiceFromToggle(this@ScSwitchActivity)
            }
            finish()
        }
    }
}
