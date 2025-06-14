package com.npv.crsgw.ui

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.npv.crsgw.NpvErrorCode
import com.npv.crsgw.rest.model.BaseResponse
import com.npv.crsgw.rest.model.SignInWithEmailRequest
import com.npv.crsgw.rest.model.SignInWithEmailResponse
import com.npv.crsgw.rest.network.ApiResult
import com.npv.crsgw.rest.repository.NpvRepository
import kotlinx.coroutines.launch

open class BaseViewModel : ViewModel() {
    private val TAG = BaseViewModel::class.simpleName

    fun <T> launchRequest(
        liveData: MutableLiveData<ApiResult<T>>,
        block: suspend () -> ApiResult<T>
    ) {
        viewModelScope.launch {
            liveData.value = ApiResult.Loading
            try {
                val result = block()
                liveData.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Request failed: $e")
                liveData.value = ApiResult.Failure(NpvErrorCode.UNKNOWN_ERROR.code, e.message ?: "网络异常")
            }
        }
    }

    /*
    protected fun <T> launchRequest(
        liveData: MutableLiveData<ApiResult<T>>,
        block: suspend () -> BaseResponse<T>
    ) {
        viewModelScope.launch {
            liveData.value = ApiResult.Loading
            try {
                val response = block()
                if (response.ok()) {
                    liveData.value = ApiResult.Success(response.data!!)
                } else {
                    liveData.value = ApiResult.Failure(response.code, response.message)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Request failed: $e")
                liveData.value = ApiResult.Failure(NpvErrorCode.UNKNOWN_ERROR.code, e.message ?: "网络异常")
            }
        }
    }
    */
}