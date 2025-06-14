package com.npv.crsgw.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.npv.crsgw.NpvErrorCode
import com.npv.crsgw.rest.model.GetHomeDataItemResponse
import com.npv.crsgw.rest.model.HomeDataItem
import com.npv.crsgw.rest.model.SignInWithEmailRequest
import com.npv.crsgw.rest.model.SignInWithEmailResponse
import com.npv.crsgw.rest.network.ApiResult
import com.npv.crsgw.rest.repository.NpvRepository

class UserViewModel : BaseViewModel() {
    private val TAG = UserViewModel::class.simpleName

    private val repo = NpvRepository()

    val userState = MutableLiveData<ApiResult<GetHomeDataItemResponse>>()

    fun getHomeData(email: String) {
        launchRequest(userState) {
           repo.getHomeData(email)
        }
    }

    /*
    suspend fun login(request: SignInWithEmailRequest): ApiResult<SignInWithEmailResponse> {
        return try {
            repo.login(request)
        } catch (e: Exception) {
            ApiResult.Failure(NpvErrorCode.UNKNOWN_ERROR.code, e.toString())
        }
    }
    */
}