package com.v2ray.ang.helper

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.v2ray.ang.ui.ScannerActivity

/**
 * Helper for scanning QR codes.
 *
 * This class encapsulates the logic for launching the QR code scanner activity
 * and handling the scan result.
 */
class QRCodeScannerHelper(private val activity: AppCompatActivity) {
    private var scanCallback: ((String?) -> Unit)? = null

    private val scanLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                val scanResult = result.data?.getStringExtra("SCAN_RESULT")
                scanCallback?.invoke(scanResult)
            } else {
                scanCallback?.invoke(null)
            }
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
