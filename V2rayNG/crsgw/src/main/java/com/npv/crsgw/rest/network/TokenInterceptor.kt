package com.npv.crsgw.rest.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class TokenInterceptor(
    private val tokenProvider: suspend () -> String?
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenProvider() } // 若是 suspend，必须阻塞获取
        val newRequest = chain.request().newBuilder()
            .apply {
                token?.let {
                    header("Authorization", "Bearer $it")
                }
            }
            .build()
        return chain.proceed(newRequest)
    }
}