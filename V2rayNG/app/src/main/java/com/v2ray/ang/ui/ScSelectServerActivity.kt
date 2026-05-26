package com.v2ray.ang.ui

import android.os.Bundle
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.handler.MmkvManager

class ScSelectServerActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moveTaskToBack(true)
        setContentView(R.layout.activity_none)

        val guid = intent.getStringExtra(EXTRA_GUID)
        if (!guid.isNullOrBlank() && MmkvManager.decodeServerConfig(guid) != null) {
            MmkvManager.setSelectServer(guid)
            if (CoreServiceManager.isRunning()) {
                CoreServiceManager.startVService(this, guid)
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_GUID = "EXTRA_GUID"
    }
}
