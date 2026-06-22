package com.v2ray.ang.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyGroupLatencyFilterTest {
    private data class Node(
        val name: String,
        val delayMillis: Long?,
    )

    @Test
    fun filtersDeadNodesWhenValidMeasuredDelayExists() {
        val nodes = listOf(
            Node("dead", -1L),
            Node("fast", 120L),
            Node("slow", 320L),
        )

        val filtered = PolicyGroupLatencyFilter.filterForLowestLatency(
            items = nodes,
            policyGroupType = "leastPing",
        ) { it.delayMillis }

        assertEquals(listOf("fast", "slow"), filtered.map { it.name })
    }

    @Test
    fun keepsUnmeasuredNodesWhenNoMeasuredDelayExists() {
        val nodes = listOf(
            Node("dead", -1L),
            Node("unmeasured", 0L),
        )

        val filtered = PolicyGroupLatencyFilter.filterForLowestLatency(
            items = nodes,
            policyGroupType = "lowest_latency",
        ) { it.delayMillis }

        assertEquals(listOf("unmeasured"), filtered.map { it.name })
    }

    @Test
    fun doesNotChangeNonLowestLatencyPolicies() {
        val nodes = listOf(
            Node("dead", -1L),
            Node("normal", 150L),
        )

        val filtered = PolicyGroupLatencyFilter.filterForLowestLatency(
            items = nodes,
            policyGroupType = "random",
        ) { it.delayMillis }

        assertEquals(nodes, filtered)
    }

    @Test
    fun recognizesLowestLatencyPolicyNames() {
        assertTrue(PolicyGroupLatencyFilter.isLowestLatencyPolicy("leastPing"))
        assertTrue(PolicyGroupLatencyFilter.isLowestLatencyPolicy("lowest_latency"))
        assertTrue(PolicyGroupLatencyFilter.isLowestLatencyPolicy("url-test"))
        assertFalse(PolicyGroupLatencyFilter.isLowestLatencyPolicy("random"))
    }
}
