package com.dalulong.app.dto.entities

data class WebDavConfig(
    val baseUrl: String,
    val username: String? = null,
    val password: String? = null,
    val remoteBasePath: String = "/",
    val timeoutSeconds: Long = 30
)