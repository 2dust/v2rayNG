package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfigBenchmarkTest {

    private fun m(
        guid: String,
        ping: Long = ConfigBenchmark.NOT_MEASURED,
        down: Double? = null,
        up: Double? = null,
    ) = ConfigBenchmark.Measurement(
        guid = guid,
        pingMs = ping,
        downloadMbps = down,
        uploadMbps = up,
        measuredAt = 1L,
    )

    @Test
    fun rankingIsLowestPingFirst() {
        val ranked = ConfigBenchmark.rank(
            listOf(
                m("slow", ping = 240),
                m("fast", ping = 80),
                m("middle", ping = 150),
            )
        )
        assertEquals(listOf("fast", "middle", "slow"), ranked.map { it.guid })
    }

    @Test
    fun unreachableGoesLastEvenWithGreatSpeeds() {
        val ranked = ConfigBenchmark.rank(
            listOf(
                m("dead", down = 90.0),
                m("alive", ping = 300),
            )
        )
        assertEquals(listOf("alive", "dead"), ranked.map { it.guid })
    }

    @Test
    fun equalPingsPreferTheFasterDownload() {
        val ranked = ConfigBenchmark.rank(
            listOf(m("a", ping = 100, down = 5.0), m("b", ping = 100, down = 40.0))
        )
        assertEquals(listOf("b", "a"), ranked.map { it.guid })
    }

    @Test
    fun suggestionsIgnoreUnmeasuredConfigs() {
        val suggestions = ConfigBenchmark.suggestions(
            listOf(
                m("a", ping = 90),
                m("b", ping = 70),
                m("c", ping = 60),
                m("d", ping = 55),
                m("never", ping = ConfigBenchmark.NOT_MEASURED),
            )
        )
        assertEquals(listOf("d", "c", "b"), suggestions.map { it.guid })
    }

    @Test
    fun pingOnlyRunKeepsTheSpeedsMeasuredEarlier() {
        val stored = mapOf("a" to m("a", ping = 120, down = 30.0, up = 8.0))
        val merged = ConfigBenchmark.merge(stored, listOf(m("a", ping = 90)))
        val row = merged.getValue("a")

        assertEquals(90L, row.pingMs)
        assertEquals(30.0, row.downloadMbps ?: -1.0)
        assertEquals(8.0, row.uploadMbps ?: -1.0)
    }

    @Test
    fun failedRunKeepsEverythingFromBefore() {
        val stored = mapOf("a" to m("a", ping = 120, down = 30.0))
        val merged = ConfigBenchmark.merge(stored, listOf(m("a")))
        val row = merged.getValue("a")

        assertEquals(120L, row.pingMs)
        assertEquals(30.0, row.downloadMbps ?: -1.0)
        assertNull(row.uploadMbps)
        assertTrue(merged.containsKey("a"))
    }

    @Test
    fun speedOnlyRunAddsSpeedsToAnUnmeasuredConfig() {
        val merged = ConfigBenchmark.merge(emptyMap(), listOf(m("a", down = 12.5, up = 3.0)))
        val row = merged.getValue("a")

        assertEquals(ConfigBenchmark.NOT_MEASURED, row.pingMs)
        assertEquals(12.5, row.downloadMbps ?: -1.0)
    }
}
