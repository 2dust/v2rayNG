package com.npv.crsgw.rest.network

import com.npv.crsgw.rest.api.ApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://npv-qji4ck.shgwzyz.org"

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // private val tokenProvider = { TokenManager.getToken() }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(TokenInterceptor { TokenManager.getToken() })
        .addInterceptor(RetryInterceptor())
        .addInterceptor(logging)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    fun <T> create(service: Class<T>): T = retrofit.create(service)
}