package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.RulesetItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdultContentLockTest {

    private val userRule = RulesetItem(id = "user", domain = listOf("domain:example.com"), outboundTag = AppConfig.TAG_DIRECT)

    @Test
    fun off_leavesTheRulesAsTheyAre() {
        val rules = listOf(userRule)
        assertSame(rules, AdultContentLock.withLock(rules, enabled = false))
        assertNull(AdultContentLock.withLock(null, enabled = false))
    }

    @Test
    fun on_putsTheBlockRuleInFrontOfTheUsersRules() {
        val result = AdultContentLock.withLock(listOf(userRule), enabled = true).orEmpty()
        assertEquals(2, result.size)
        assertEquals(AppConfig.TAG_BLOCKED, result[0].outboundTag)
        assertTrue(result[0].enabled)
        assertEquals(userRule, result[1])
    }

    @Test
    fun on_withNoUserRules_stillGivesTheBlockRule() {
        val result = AdultContentLock.withLock(null, enabled = true).orEmpty()
        assertEquals(1, result.size)
        assertTrue(result[0].domain.orEmpty().isNotEmpty())
    }

    @Test
    fun theBlockRuleNamesNoGeositeCategory() {
        // A geosite category would stop the core from starting on a device whose geosite file lacks it.
        assertTrue(AdultContentLock.BLOCKED_DOMAINS.none { it.startsWith("geosite:") })
    }
}
