package com.v2ray.ang.dto

import java.io.Serializable

data class TestServiceMessage(
    val key: Int,
    val testId: String = "",
    val subscriptionId: String = "",
    val serverGuids: List<String>? = null,
    val excludedServerGuids: List<String> = emptyList(),
    val onlyTcp: Boolean = false
) : Serializable

