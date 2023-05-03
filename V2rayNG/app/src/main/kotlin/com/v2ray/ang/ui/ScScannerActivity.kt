package com.v2ray.ang.ui

import android.Manifest
import android.content.*
import com.tbruyelle.rxpermissions.RxPermissions
import com.v2ray.ang.R
import com.v2ray.ang.util.AngConfigManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.v2ray.ang.extension.toast

class ScScannerActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_none)
        importQRcode()
    }

    fun importQRcode(): Boolean {
        RxPermissions(this)
                .request(Manifest.permission.CAMERA)
                .subscribe {
                    if (it)
                        scanQRCode.launch(Intent(this, ScannerActivity::class.java))
                    else
                        toast(R.string.toast_permission_denied)
                }

        return true
    }

    private val scanQRCode = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            val data=it.data?.getStringExtra("SCAN_RESULT")
            val count = AngConfigManager.importBatchConfig(data, if (data?.startsWith("http")==true)"" else "default", true, selectSub = true)
            if (count > 0) {
                toast(R.string.toast_success)
            } else {
                toast(R.string.toast_failure)
            }
            //todo @hiddify1
            startActivity(Intent(this, HiddifyMainActivity::class.java))  //todo: check to open main or hiddifyMain
        }
        finish()
    }
}
