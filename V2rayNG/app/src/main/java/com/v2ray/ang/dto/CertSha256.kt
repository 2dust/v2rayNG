package com.v2ray.ang.dto

data class CertSha256Request(
    val address: String = "",
    val port: Int = 443,
    val serverName: String? = null,
    val timeoutMs: Long = 5000L,
)

data class CertSha256Result(
    val sha256: String = "",
    val error: String = "",
)
