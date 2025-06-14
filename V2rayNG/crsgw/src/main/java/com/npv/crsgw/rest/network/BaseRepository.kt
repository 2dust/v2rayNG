package com.npv.crsgw.rest.network

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.npv.crsgw.NpvErrorCode
import com.npv.crsgw.rest.model.BaseResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

open class BaseRepository {
    private val TAG = BaseRepository::class.simpleName

    fun <T> apiFlow(call: suspend () -> BaseResponse<T>): Flow<ApiResult<T>> = flow {
        val response = call()
        if (response.ok() && response.data != null) {
            emit(ApiResult.Success(response.data))
        } else {
            emit(ApiResult.Failure(response.code, response.message))
        }
    }.catch { e ->
        Log.e(TAG, "API call exception: $e")
        emit(ApiResult.Failure(NpvErrorCode.UNKNOWN_ERROR.code, ""))
    }.flowOn(Dispatchers.IO)


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

    fun <T> apiLiveData(block: suspend () -> BaseResponse<T>): LiveData<ApiResult<T>> = liveData {
        try {
            val response = block()
            if (response.ok()) {
                emit(ApiResult.Success(response.data!!))
            } else {
                emit(ApiResult.Failure(response.code, response.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "API call exception: ${e.message}", e)
            emit(ApiResult.Failure(NpvErrorCode.UNKNOWN_ERROR.code, e.message ?: "Unknown error"))
        }
    }
}