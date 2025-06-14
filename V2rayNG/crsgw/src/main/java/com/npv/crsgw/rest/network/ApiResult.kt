package com.npv.crsgw.rest.network

sealed class ApiResult<out T> {
    object Loading : ApiResult<Nothing>()
    data class Success<out T>(val data: T) : ApiResult<T>()
    data class Failure(val code: Int, val message: String) : ApiResult<Nothing>()
}