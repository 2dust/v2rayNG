package com.v2ray.ang.dto

data class OutboundProbeProfilePlan(
    val guid: String,
    val outboundTags: List<String>,
    val balancerTag: String? = null,
)

data class OutboundProbePlan(
    val content: String,
    val profiles: List<OutboundProbeProfilePlan>,
    val failedGuids: List<String>,
    val samples: Int,
)
