package com.npv.crsgw.rest.api

import com.npv.crsgw.rest.model.BaseResponse
import com.npv.crsgw.rest.model.SignInWithEmailRequest
import com.npv.crsgw.rest.model.SignInWithEmailResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("/portal/public/v1/signInWithUserName")
    suspend fun loginByEmail(@Body request: SignInWithEmailRequest): BaseResponse<SignInWithEmailResponse>

}