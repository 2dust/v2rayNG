package com.npv.crsgw.rest.model

data class SignInWithEmailResponse(
    val avatar: String,
    val username: String,
    val nickname: String,
    val accessToken: String,
    val tokenType: String,
    val status: String
)