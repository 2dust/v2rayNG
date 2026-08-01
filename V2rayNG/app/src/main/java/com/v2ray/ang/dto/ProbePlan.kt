package com.v2ray.ang.dto

data class ProbeProfile(
    val guid: String,
    val outboundTags: List<String>,
    val balancerTag: String? = null,
)

data class ProbePlan(
    val content: String,
    val profiles: List<ProbeProfile>,
    val failedGuids: List<String>,
    val samples: Int,
)
