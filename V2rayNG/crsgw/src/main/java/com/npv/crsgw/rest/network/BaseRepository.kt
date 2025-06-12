package com.npv.crsgw.rest.network

import android.util.Log
import com.npv.crsgw.NpvErrorCode
import com.npv.crsgw.rest.model.BaseResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

open class BaseRepository {
    private val TAG = BaseRepository::class.simpleName

    fun <T> apiCall(call: suspend () -> BaseResponse<T>): Flow<ApiResult<T>> = flow {
        try {
            val response = call()
            if (response.ok() && response.data != null) {
                emit(ApiResult.Success(response.data))
            } else {
                emit(ApiResult.Failure(response.code, response.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "API call exception: $e")
            emit(ApiResult.Failure(NpvErrorCode.UNKNOWN_ERROR.code, ""))
        }
    }

    suspend fun <T> safeApiCall(block: suspend () -> BaseResponse<T>): ApiResult<T> {
        return try {
            val response = block()
            if (response.ok()) {
                ApiResult.Success(response.data!!)
            } else {
                ApiResult.Failure(response.code, response.message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "API call exception: $e")
            ApiResult.Failure(NpvErrorCode.UNKNOWN_ERROR.code, "")
        }
    }
}