package com.npv.crsgw.rest.model

data class NpvUser(
    val avatar: String?,
    val username: String,
    val nickname: String?,
    val accessToken: String,
    val tokenType: String,
    val status: String,
    val password: String?
)