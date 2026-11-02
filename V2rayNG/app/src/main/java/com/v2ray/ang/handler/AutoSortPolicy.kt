package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.BalancerStrategyType
import com.v2ray.ang.enums.EConfigType

internal object AutoSortPolicy {
    const val DEFAULT_INTERVAL_MINUTES = 10
    val SUPPORTED_INTERVAL_MINUTES = listOf(1, 5, 10, 30)

    fun normalizedInterval(minutes: Int?): Int =
        minutes?.takeIf { it in SUPPORTED_INTERVAL_MINUTES } ?: DEFAULT_INTERVAL_MINUTES

    fun intervalMillis(minutes: Int): Long =
        normalizedInterval(minutes) * 60_000L

    fun shouldRun(
        selectedGuid: String?,
        configGuid: String?,
        config: ProfileItem?,
        isRunning: Boolean,
    ): Boolean {
        if (
            !isRunning ||
            selectedGuid.isNullOrBlank() ||
            configGuid != selectedGuid ||
            config == null
        ) {
            return false
        }
        if (config.configType != EConfigType.POLICYGROUP) return false
        if (BalancerStrategyType.from(config.policyGroupType) != BalancerStrategyType.LEAST_PING) {
            return false
        }
        return config.policyGroupTestOutbounds != false &&
                config.policyGroupAutoSortEnabled == true &&
                !config.policyGroupSubscriptionId.isNullOrBlank() &&
                isSafeFallback(config.policyGroupFallbackTag)
    }

    fun isTestableNode(config: ProfileItem): Boolean =
        config.configType != EConfigType.POLICYGROUP &&
                config.configType != EConfigType.PROXYCHAIN

    fun isSafeFallback(fallbackTag: String?): Boolean {
        val normalized = fallbackTag?.trim()
        return normalized.isNullOrEmpty() ||
                (
                    !normalized.equals(AppConfig.TAG_DIRECT, ignoreCase = true) &&
                            !normalized.equals(AppConfig.TAG_BLOCKED, ignoreCase = true)
                )
    }
}
