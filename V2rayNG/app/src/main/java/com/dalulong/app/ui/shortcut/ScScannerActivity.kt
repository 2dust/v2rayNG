package com.dalulong.app.ui.shortcut

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.dalulong.app.R
import com.dalulong.app.extension.toastError
import com.dalulong.app.extension.toastSuccess
import com.dalulong.app.handler.AngConfigManager
import com.dalulong.app.ui.base.HelperBaseComponentActivity
import com.dalulong.app.ui.main.MainActivity

class ScScannerActivity : HelperBaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        LaunchedEffect(Unit) {
            importQRcode()
        }
    }

    private fun importQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                val (count, countSub) = AngConfigManager.importBatchConfig(scanResult, "", false)

                if (count + countSub > 0) {
                    toastSuccess(R.string.toast_success)
                } else {
                    toastError(R.string.toast_failure)
                }

                startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
        }
    }
}