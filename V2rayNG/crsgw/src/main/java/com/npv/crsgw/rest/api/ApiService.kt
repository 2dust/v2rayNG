package com.npv.crsgw.rest.api

import com.npv.crsgw.rest.model.BaseResponse
import com.npv.crsgw.rest.model.GetHomeDataItemResponse
import com.npv.crsgw.rest.model.SignInWithEmailRequest
import com.npv.crsgw.rest.model.SignInWithEmailResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    @POST("/portal/public/v1/signInWithUserName")
    suspend fun loginByEmail(@Body request: SignInWithEmailRequest): BaseResponse<SignInWithEmailResponse>

    @GET("/portal/v1/home/dataItem/{email}")
    suspend fun getHomeData(@Path("email") email: String): BaseResponse<GetHomeDataItemResponse>
}