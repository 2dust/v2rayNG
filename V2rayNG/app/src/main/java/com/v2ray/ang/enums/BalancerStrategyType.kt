package com.v2ray.ang.enums

/**
 * Load-balancing strategy types supported by xray balancer.
 *
 * @param policyGroupType The string value written into the xray config JSON.
 * @param policyGroupTypeValue The numeric string stored in [com.v2ray.ang.dto.entities.ProfileItem.policyGroupType].
 * @param requiresBurstObservatory Whether this strategy needs a burstObservatory (leastLoad).
 * @param requiresObservatory Whether this strategy needs an observatory (leastPing).
 * @param supportsObservatory Whether an observatory can health-filter this strategy.
 */
enum class BalancerStrategyType(
    val policyGroupType: String,
    val policyGroupTypeValue: String,
    val requiresBurstObservatory: Boolean = false,
    val requiresObservatory: Boolean = false,
    val supportsObservatory: Boolean = false,
) {
    LEAST_LOAD("leastLoad", "1", requiresBurstObservatory = true),
    RANDOM("random", "2", supportsObservatory = true),
    ROUND_ROBIN("roundRobin", "3", supportsObservatory = true),
    LEAST_PING("leastPing", "", requiresObservatory = true); // default / else

    companion object {
        fun from(policyGroupType: String?): BalancerStrategyType =
            entries.firstOrNull { it.policyGroupTypeValue == policyGroupType } ?: LEAST_PING
    }
}
