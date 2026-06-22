package com.v2ray.ang.core

/**
 * Filters invalid latency values before building lowest-latency policy groups.
 *
 * Some failed delay tests are stored as negative values, such as -1ms. Without
 * filtering, these values can be treated as better than valid latency values.
 */
object PolicyGroupLatencyFilter {
    private val lowestLatencyPolicies = setOf(
        "latency",
        "lowestlatency",
        "lowestping",
        "leastlatency",
        "leastdelay",
        "leastping",
        "urltest",
        "leastload",
    )

    fun isLowestLatencyPolicy(policyGroupType: String?): Boolean {
        val normalized = policyGroupType
            .orEmpty()
            .lowercase()
            .replace("_", "")
            .replace("-", "")
            .replace(" ", "")

        return normalized in lowestLatencyPolicies
    }

    fun isValidMeasuredDelay(delayMillis: Long?): Boolean {
        return delayMillis != null && delayMillis > 0L
    }

    fun isNotDeadDelay(delayMillis: Long?): Boolean {
        return delayMillis == null || delayMillis >= 0L
    }

    fun <T> filterForLowestLatency(
        items: List<T>,
        policyGroupType: String?,
        delayOf: (T) -> Long?,
    ): List<T> {
        if (!isLowestLatencyPolicy(policyGroupType)) {
            return items
        }

        val measured = items.filter { isValidMeasuredDelay(delayOf(it)) }
        if (measured.isNotEmpty()) {
            return measured
        }

        val notDead = items.filter { isNotDeadDelay(delayOf(it)) }
        if (notDead.isNotEmpty()) {
            return notDead
        }

        return items
    }
}
