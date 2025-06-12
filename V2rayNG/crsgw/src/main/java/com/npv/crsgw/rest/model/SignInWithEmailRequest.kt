package com.npv.crsgw.rest.model

data class SignInWithEmailRequest(
    val email: String,
    val password: String
)