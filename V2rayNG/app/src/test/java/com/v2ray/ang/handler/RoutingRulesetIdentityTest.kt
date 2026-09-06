package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.util.JsonUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingRulesetIdentityTest {
    @Test
    fun legacyMissingNullBlankAndDuplicateIdsKeepTheirRuleContents() {
        val rules = JsonUtil.fromJson(
            """[{"remarks":"Missing"},{"id":null},{"id":" "},{"id":"same","remarks":"One"},{"id":"same","remarks":"Two"}]""",
            Array<RulesetItem>::class.java,
        )!!.toList()
        val normalized = withUniqueRoutingRuleIds(rules)
        assertTrue(normalized.all { it.id.isNotBlank() })
        assertEquals(rules.size, normalized.map { it.id }.toSet().size)
        assertEquals("same", normalized[3].id)
        assertEquals(rules.map { it.copy(id = "") }, normalized.map { it.copy(id = "") })
        assertEquals(normalized, withUniqueRoutingRuleIds(normalized))
        assertEquals("same", rules[4].id)
    }

    @Test
    fun replacementsCannotTakeAnyOriginalIdOrRepeatAGeneratedId() {
        val candidates = listOf("later", "", "first-new", "first-new", "second-new").iterator()
        val rules = listOf(RulesetItem(), RulesetItem(id = "later"), RulesetItem(id = "later"))
        val normalized = withUniqueRoutingRuleIds(rules) { candidates.next() }
        assertEquals(listOf("first-new", "later", "second-new"), normalized.map { it.id })
    }

    @Test
    fun identicalObjectReferencesStillBecomeDistinctRowsWithoutChangingTheInput() {
        val rule = RulesetItem(id = "same")
        val result = withUniqueRoutingRuleIds(listOf(rule, rule))
        assertEquals("same", result[0].id)
        assertNotEquals(result[0].id, result[1].id)
        assertEquals("same", rule.id)
    }

    @Test
    fun oldExportsDoNotMultiplyRetainedLockedCopiesAfterIdRepair() {
        val locked = RulesetItem(id = "duplicate", remarks = "Same", locked = true)
        val other = RulesetItem(id = "duplicate", remarks = "Same", outboundTag = "direct")
        val exported = listOf(locked, locked.copy(), other)
        var current = withUniqueRoutingRuleIds(exported)
        val expected = current
        repeat(3) {
            current = withUniqueRoutingRuleIds(SettingsManager.mergeRoutingRulesets(current, exported))
            assertEquals(expected.take(2), current.take(2))
            assertEquals(expected.map { it.copy(id = "") }, current.map { it.copy(id = "") })
            assertEquals(3, current.map { it.id }.toSet().size)
        }
    }

    @Test
    fun differentRulesWithTheSameTitleOrIdAreNotDiscarded() {
        val locked = RulesetItem(id = "same", remarks = "Same", locked = true)
        val different = locked.copy(domain = listOf("example.invalid"))
        val imported = listOf(locked.copy(id = "previous-id"), different, RulesetItem(id = "same", remarks = "Same"))
        val merged = withUniqueRoutingRuleIds(SettingsManager.mergeRoutingRulesets(listOf(locked), imported))
        assertEquals(listOf(locked, different, imported.last()).map { it.copy(id = "") }, merged.map { it.copy(id = "") })
        assertEquals(3, merged.map { it.id }.toSet().size)
    }

    @Test
    fun validHeaderLikeIdsAndEmptyListsArePreserved() {
        val rules = listOf(RulesetItem(id = "domain_strategy"), RulesetItem(id = "0"))
        assertEquals(rules, withUniqueRoutingRuleIds(rules))
        assertEquals(emptyList<RulesetItem>(), withUniqueRoutingRuleIds(emptyList()))
    }
}
