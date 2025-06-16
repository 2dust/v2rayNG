package com.v2ray.npv

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.npv.crsgw.store.UserStore
import com.v2ray.ang.ui.BaseActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 可以添加启动动画或延迟
        lifecycleScope.launch {
            delay(500)
            if (UserStore.isLoggedIn()) {
                startActivity(Intent(this@SplashActivity, NpvMainActivity::class.java))
            } else {
                startActivity(Intent(this@SplashActivity, NpvLoginActivity::class.java))
            }
            finish()
        }
    }
}