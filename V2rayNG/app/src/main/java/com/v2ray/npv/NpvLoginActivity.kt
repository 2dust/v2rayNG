package com.v2ray.npv

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.npv.crsgw.component.LoadingDialog
import com.npv.crsgw.databinding.NpvActivityLoginBinding
import com.npv.crsgw.rest.model.NpvUser
import com.npv.crsgw.rest.model.SignInWithEmailRequest
import com.npv.crsgw.rest.network.ApiResult
import com.npv.crsgw.rest.repository.NpvRepository
import com.npv.crsgw.store.UserStore
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.SpeedtestManager
import kotlinx.coroutines.launch

class NpvLoginActivity : NpvBaseActivity() {
    private val TAG = NpvLoginActivity::class.simpleName

    private val binding by lazy { NpvActivityLoginBinding.inflate(layoutInflater) }

    private val repo = NpvRepository()
    private var loadingDialog: LoadingDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        Log.d(TAG, "Version: " + "v${BuildConfig.VERSION_NAME} (${SpeedtestManager.getLibVersion()})")

        // TODO: encrypt the user's password
        lifecycleScope.launch {
            val user = UserStore.getUser()
            binding.email.setText(user?.username)
            binding.password.setText(user?.password)
        }

        loadingDialog = LoadingDialog(this)

        // var viewModel = UserViewModel()

        binding.login.setOnClickListener {
            val email = binding.email.text?.trim().toString()
            val password = binding.password.text?.trim().toString()

            if (email.isEmpty() || password.isEmpty()) {
                toastError(getString(com.npv.crsgw.R.string.npv_missing_email_or_password))
                return@setOnClickListener
            }

            showLoading()

            val request = SignInWithEmailRequest(email, password)
            lifecycleScope.launch {
                repo.login(request).collect { result ->

                    hideLoading()

                    when (result) {
                        is ApiResult.Success -> {
                            val r = result.data
                            val user = NpvUser(r.avatar, r.username, r.nickname,
                                r.accessToken, r.tokenType, r.status,
                                password, "", "")
                            UserStore.storeUser(user)
                            // 跳转主页
                            startActivity(Intent(this@NpvLoginActivity, NpvMainActivity::class.java))
                        }

                        is ApiResult.Failure -> {
                            // ❌ 登录失败，显示错误信息
                            toastError("登录失败：${result.code}")
                        }

                        ApiResult.Loading -> {
                        }
                    }
                }
            }
        }
    }
}