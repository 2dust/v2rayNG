package com.npv.crsgw.rest.repository

import com.npv.crsgw.rest.api.ApiService
import com.npv.crsgw.rest.model.SignInWithEmailRequest
import com.npv.crsgw.rest.model.SignInWithEmailResponse
import com.npv.crsgw.rest.network.ApiResult
import com.npv.crsgw.rest.network.BaseRepository
import com.npv.crsgw.rest.network.RetrofitClient
import kotlinx.coroutines.flow.Flow

class NpvRepository : BaseRepository() {
    private val service = RetrofitClient.create(ApiService::class.java)

    suspend fun login(request: SignInWithEmailRequest): ApiResult<SignInWithEmailResponse> {
        return safeApiCall { service.loginByEmail(request) }
    }
}