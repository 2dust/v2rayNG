package com.npv.crsgw.rest.model

data class BaseResponse<T>(
    val code: Int,
    val message: String,
    val data: T?
) {
    fun ok(): Boolean = code == 0
}