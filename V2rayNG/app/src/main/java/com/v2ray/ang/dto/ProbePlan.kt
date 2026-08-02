package com.v2ray.ang.dto

data class ProbeProfile(
    val guid: String,
    val outboundTags: List<String>,
    val balancerTag: String? = null,
)

data class ProbePlan(
    val content: String,
    val profiles: List<ProbeProfile>,
    val individualGuids: List<String> = emptyList(),
    val failedGuids: List<String> = emptyList(),
)
