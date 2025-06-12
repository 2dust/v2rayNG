package com.v2ray.npv

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.npv.crsgw.databinding.NpvActivityLoginBinding
import com.npv.crsgw.rest.model.NpvUser
import com.npv.crsgw.rest.model.SignInWithEmailRequest
import com.npv.crsgw.rest.network.ApiResult
import com.npv.crsgw.rest.network.TokenManager
import com.npv.crsgw.store.UserStore
import com.npv.crsgw.ui.UserViewModel
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.ui.BaseActivity
import com.v2ray.npv.SplashActivity
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class LoginActivity : BaseActivity() {
    private val TAG = LoginActivity::class.simpleName

    private val binding by lazy { NpvActivityLoginBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        Log.d(TAG, "Version: " + "v${BuildConfig.VERSION_NAME} (${SpeedtestManager.getLibVersion()})")

        // TODO: Delete these two lines
        binding.email.setText("wyylling@gmail.com")
        binding.password.setText("Wyyl1002!")

        var viewModel = UserViewModel()

        binding.login.setOnClickListener {
            val email = binding.email.text?.trim().toString()
            val password = binding.password.text?.trim().toString()

            if (email.isEmpty() || password.isEmpty()) {
                toastError(getString(com.npv.crsgw.R.string.npv_missing_email_or_password))
                return@setOnClickListener
            }

            lifecycleScope.launch {
                when (val result = viewModel.login(SignInWithEmailRequest(email, password))) {
                    is ApiResult.Success -> {
                        val r = result.data
                        val user = NpvUser(r.avatar, r.username, r.nickname, r.accessToken, r.tokenType, r.status)
                        UserStore.storeUser(user)
                        // 跳转主页
                        startActivity(Intent(this@LoginActivity, NpvMainActivity::class.java))
                    }
                    is ApiResult.Failure -> {
                        toastError("登录失败：${result.code}")
                    }
                }
            }
        }
    }
}