package com.v2ray.ang.ui.routing

import com.v2ray.ang.dto.entities.RulesetItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutingSettingsViewModelTest {

    @Test
    fun removeRoutingRule_resolvesTheCurrentPositionFromTheStableId() {
        val rules = listOf(rule("first"), rule("second"), rule("target"))

        val removal = removeRoutingRule(rules, "target")

        assertEquals(2, removal?.position)
        assertEquals(listOf("first", "second"), removal?.remainingRules?.map { it.id })
        assertEquals(listOf("first", "second", "target"), rules.map { it.id })
    }

    @Test
    fun removeRoutingRule_removesOnlyTheMatchingRule() {
        val rules = listOf(rule("first"), rule("target"), rule("last"))

        val removal = removeRoutingRule(rules, "target")

        assertEquals(1, removal?.position)
        assertEquals(listOf("first", "last"), removal?.remainingRules?.map { it.id })
    }

    @Test
    fun removeRoutingRule_ignoresAnUnknownStableId() {
        assertNull(removeRoutingRule(listOf(rule("first")), "missing"))
    }

    private fun rule(id: String) = RulesetItem(id = id)
}
