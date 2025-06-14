package com.v2ray.npv

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import com.npv.crsgw.component.LoadingDialog
import com.npv.crsgw.event.AuthEventBus
import com.npv.crsgw.rest.network.ApiResult
import com.npv.crsgw.store.UserStore
import com.v2ray.ang.ui.BaseActivity
import kotlinx.coroutines.launch

abstract class NpvBaseActivity : BaseActivity() {

    private var hasHandledLogout = false

    private var loadingDialog: LoadingDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            AuthEventBus.authExpiredFlow.collect {
                if (!hasHandledLogout) {
                    hasHandledLogout = true // 防止多次跳转

                    UserStore.clear() // 清除用户信息

                    startActivity(Intent(this@NpvBaseActivity, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                }
            }
        }
    }

    fun showLoading() {
        if (loadingDialog == null) {
            loadingDialog = LoadingDialog(this)
        }
        if (loadingDialog?.isShowing != true) {
            loadingDialog?.show()
        }
    }

    fun hideLoading() {
        loadingDialog?.dismiss()
    }

    override fun onDestroy() {
        loadingDialog?.dismiss()
        loadingDialog = null
        super.onDestroy()
    }

    protected fun <T> observeState(
        liveData: LiveData<ApiResult<T>>,
        onSuccess: (T) -> Unit,
        onFailure: ((code: Int, msg: String) -> Unit)? = null,
        onLoading: (() -> Unit)? = null
    ) {
        liveData.observe(this) { state ->
            when (state) {
                is ApiResult.Loading -> onLoading?.invoke()
                is ApiResult.Success -> onSuccess(state.data)
                is ApiResult.Failure -> onFailure?.invoke(state.code, state.message)
            }
        }
    }
}