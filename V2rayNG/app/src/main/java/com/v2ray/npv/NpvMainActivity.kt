package com.v2ray.npv

import android.os.Bundle
import com.v2ray.ang.ui.BaseActivity

class NpvMainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayHomeAsUpEnabled(false)

    }
}