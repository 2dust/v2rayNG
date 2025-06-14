package com.npv.crsgw.rest.repository

import com.npv.crsgw.rest.api.ApiService
import com.npv.crsgw.rest.model.GetHomeDataItemResponse
import com.npv.crsgw.rest.model.SignInWithEmailRequest
import com.npv.crsgw.rest.model.SignInWithEmailResponse
import com.npv.crsgw.rest.network.ApiResult
import com.npv.crsgw.rest.network.BaseRepository
import com.npv.crsgw.rest.network.RetrofitClient
import kotlinx.coroutines.flow.Flow
import kotlin.math.log

class NpvRepository : BaseRepository() {
    private val service = RetrofitClient.create(ApiService::class.java)

    /*
    val request = SignInWithEmailRequest()
    val loginFlow = apiFlow { service.loginByEmail(request) }
   */
    fun login(request: SignInWithEmailRequest): Flow<ApiResult<SignInWithEmailResponse>> {
        //return safeApiCall { service.loginByEmail(request) }
        return apiFlow { service.loginByEmail(request) }
    }

    suspend fun getHomeData(email: String): ApiResult<GetHomeDataItemResponse> {
        return safeApiCall { service.getHomeData(email) }
    }
}