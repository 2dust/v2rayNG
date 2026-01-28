package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.v2ray.ang.R
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.dto.PermissionType

class ScScannerActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_none)
        importQRcode()
    }

    private fun importQRcode(): Boolean {
        checkAndRequestPermission(PermissionType.CAMERA) {
            scanQRCode.launch(Intent(this, ScannerActivity::class.java))
        }
        return true
    }

    private val scanQRCode = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            val scanResult = it.data?.getStringExtra("SCAN_RESULT").orEmpty()
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