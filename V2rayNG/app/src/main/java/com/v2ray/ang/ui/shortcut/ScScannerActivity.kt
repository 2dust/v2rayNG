package com.v2ray.ang.ui.shortcut

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.main.MainActivity

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
                startActivity(Intent(this, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_IMPORT_CONFIG, scanResult)
                })
            }
            finish()
        }
    }
}
