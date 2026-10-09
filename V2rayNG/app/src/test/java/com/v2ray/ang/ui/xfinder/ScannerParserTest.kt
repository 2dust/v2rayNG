package com.v2ray.ang.ui.xfinder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScannerParserTest {
    @Test
    fun missingOrMalformedJsonReturnsNull() {
        assertNull(ScannerParser.parse(null))
        assertNull(ScannerParser.parse(""))
        assertNull(ScannerParser.parse("not json"))
        assertNull(ScannerParser.parse("""{"updated":1}"""))
    }

    @Test
    fun emptyResultsGiveEmptyListWithStats() {
        val data = ScannerParser.parse("""{"total_tested":5,"online_count":0,"pinned_count":0,"results":[]}""")!!
        assertTrue(data.ips.isEmpty())
        assertEquals(ScannerStats(total = 5, online = 0, pinned = 0), data.stats)
    }

    @Test
    fun pinnedFirstThenAscendingMs() {
        val json = """
        {"total_tested":10,"online_count":4,"pinned_count":1,"results":[
          {"ip":"1.1.1.1","ms":300,"colo":"LAX","country":"USA","source":"cloudflare","source_emoji":"☁️","pinned":false},
          {"ip":"2.2.2.2","ms":50,"colo":"PHX","country":"USA","source":"fastly","source_emoji":"⚡","pinned":false},
          {"ip":"3.3.3.3","ms":900,"colo":null,"country":null,"source":"cloudfront","source_emoji":"🟧","pinned":true},
          {"ip":"4.4.4.4","ms":10,"source":"fastly","pinned":true}
        ]}"""
        val data = ScannerParser.parse(json)!!
        assertEquals(listOf("4.4.4.4", "3.3.3.3", "2.2.2.2", "1.1.1.1"), data.ips.map { it.ip })
        assertEquals(ScannerStats(total = 10, online = 4, pinned = 1), data.stats)
    }

    @Test
    fun locationJoinsColoAndCountryAndToleratesNulls() {
        val json = """{"results":[
          {"ip":"1.1.1.1","ms":1,"colo":"LAX","country":"USA","source":"cloudflare"},
          {"ip":"2.2.2.2","ms":2,"colo":"BOG","country":null,"source":"cloudfront"},
          {"ip":"3.3.3.3","ms":3,"colo":null,"country":null,"source":"fastly"}
        ]}"""
        val ips = ScannerParser.parse(json)!!.ips
        assertEquals("LAX • USA", ips[0].location)
        assertEquals("BOG", ips[1].location)
        assertEquals("", ips[2].location)
    }

    @Test
    fun missingEmojiFallsBackToSourceAndStatsFallBackToCounts() {
        val json = """{"results":[{"ip":"1.1.1.1","ms":1,"source":"cloudflare"},{"ip":"2.2.2.2","ms":2,"source":"fastly","pinned":true}]}"""
        val data = ScannerParser.parse(json)!!
        assertEquals("☁️", data.ips.first { it.ip == "1.1.1.1" }.sourceEmoji)
        assertEquals(ScannerStats(total = 2, online = 2, pinned = 1), data.stats)
    }

    @Test
    fun duplicateIpsAndEntriesWithoutIpAreDropped() {
        val json = """{"results":[{"ip":"1.1.1.1","ms":5},{"ip":"1.1.1.1","ms":6},{"ms":7}]}"""
        assertEquals(1, ScannerParser.parse(json)!!.ips.size)
    }
}
