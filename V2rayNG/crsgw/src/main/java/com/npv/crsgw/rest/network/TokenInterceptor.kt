package com.npv.crsgw.rest.network

import com.npv.crsgw.event.AuthEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

        val response = chain.proceed(newRequest)

        if (response.code == 403) {
            // 通知 token 过期
            CoroutineScope(Dispatchers.Main).launch {
                AuthEventBus.notifyAuthExpired()
            }
        }

        /*
        if (response.code == 403) {
            response.close() // 重要：关闭旧响应
            val refreshSuccess = runBlocking { TokenManager.refreshToken() }
            if (refreshSuccess) {
                val newToken = runBlocking { TokenManager.getToken() }
                val refreshRequest = newRequest.newBuilder()
                    .removeHeader("Authorization")
                    .addHeader("Authorization", "Bearer $newToken")
                    .build()
                return chain.proceed(refreshRequest)
            } else {
                runBlocking {
                    AuthEventBus.notifyAuthExpired()
                }
            }
        }
        */

        return response
    }
}