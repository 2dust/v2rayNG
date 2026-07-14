package com.v2ray.ang.dto

/** One original runtime outbound represented by a uniquely tagged batch outbound. */
data class OutboundProbeCandidate(
    val probeTag: String,
    val runtimeTag: String,
)

/** Mapping needed to turn native batch snapshots back into one UI result per profile. */
data class OutboundProbeProfilePlan(
    val guid: String,
    val candidates: List<OutboundProbeCandidate>,
    val balancerTag: String? = null,
)

/** One-core configuration and its profile/outbound mappings. */
data class OutboundProbePlan(
    val content: String,
    val profiles: List<OutboundProbeProfilePlan>,
    val failedGuids: List<String>,
    val samples: Int,
) {
    /** One native concurrency slot per original UI configuration. */
    val probeGroups: List<List<String>>
        get() = profiles.map { profile ->
            profile.candidates.map { it.probeTag }.distinct()
        }

    val outboundTags: List<String>
        get() = probeGroups.flatten().distinct()

    val balancerTags: List<String>
        get() = profiles.mapNotNull { it.balancerTag }.distinct()
}
