package com.v2ray.ang.helper

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import com.v2ray.ang.ui.base.BaseResult
import com.v2ray.ang.ui.base.BaseResultContract
import com.v2ray.ang.ui.scanner.ScannerActivity

/**
 * Helper for scanning QR codes.
 *
 * The scanner answers with [BaseResult.Selected]; parsing it by hand with a legacy "SCAN_RESULT"
 * extra silently dropped every scan, so the shared [BaseResultContract] is used instead.
 */
class QRCodeScannerHelper(private val activity: ComponentActivity) {
    private var scanCallback: ((String?) -> Unit)? = null

    private val scanLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(BaseResultContract()) { result ->
            val text = (result as? BaseResult.Selected)?.values?.firstOrNull()
            scanCallback?.invoke(text)
            scanCallback = null
        }

    /**
     * Launch the QR code scanner activity.
     *
     * @param onResult Callback invoked with the scan result (null if cancelled or failed)
     */
    fun launch(onResult: (String?) -> Unit) {
        scanCallback = onResult
        scanLauncher.launch(Intent(activity, ScannerActivity::class.java))
    }
}
