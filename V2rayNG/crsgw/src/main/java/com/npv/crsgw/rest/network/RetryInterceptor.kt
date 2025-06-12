package com.npv.crsgw.rest.network

import okhttp3.Interceptor
import okhttp3.Response

class RetryInterceptor(private val maxRetry: Int = 3) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        var response = chain.proceed(request)
        var retryCount = 0
        while (!response.isSuccessful && retryCount < maxRetry) {
            retryCount++
            response = chain.proceed(request)
        }
        return response
    }
}