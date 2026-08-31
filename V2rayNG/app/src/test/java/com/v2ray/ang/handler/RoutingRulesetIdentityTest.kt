package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.util.JsonUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingRulesetIdentityTest {
    @Test
    fun repairsLegacyAndDuplicateIdsWithoutChangingRulesOrOrder() {
        val rules = JsonUtil.fromJson(
            """[{"remarks":"same","domain":["first.example"],"locked":true},
                {"id":"kept","remarks":"same","ip":["192.0.2.1"],"enabled":false},
                {"id":"kept","remarks":"same","port":"443"},
                {"id":null,"remarks":"null ID"},{"id":" ","remarks":"blank ID"}]""",
            Array<RulesetItem>::class.java
        )!!.toList()
        val contents = rules.map { it.copy(id = "") }

        assertTrue(ensureRoutingRulesetIds(rules))

        assertEquals("kept", rules[1].id)
        assertEquals(contents, rules.map { it.copy(id = "") })
        assertUniqueIds(rules)
        val reloaded = JsonUtil.fromJson(JsonUtil.toJson(rules), Array<RulesetItem>::class.java)!!.toList()
        assertFalse(ensureRoutingRulesetIds(reloaded))
        assertEquals(rules, reloaded)
    }

    @Test
    fun preservesAlreadyUniqueIdsIncludingRepeatedTitles() {
        val rules = listOf(rule("first"), rule("second"), rule("domain_strategy"))
        assertFalse(ensureRoutingRulesetIds(rules))
        assertEquals(listOf("first", "second", "domain_strategy"), rules.map { it.id })
    }

    @Test
    fun emptyListNeedsNoRepair() {
        assertFalse(ensureRoutingRulesetIds(emptyList()))
    }

    @Test
    fun exportingAndReimportingDoesNotDuplicateLockedRule() {
        val current = listOf(rule("locked", locked = true), rule("unlocked"))
        val exported = JsonUtil.fromJson(JsonUtil.toJson(current), Array<RulesetItem>::class.java)!!.toList()

        val merged = SettingsManager.mergeRoutingRulesets(current, exported)

        assertEquals(current, merged)
        assertFalse(ensureRoutingRulesetIds(merged))
        assertEquals(current, SettingsManager.mergeRoutingRulesets(merged, exported))
    }

    @Test
    fun keepsDifferentImportedRulesEvenIfIdsAndTitlesCollide() {
        val locked = rule("same-id", locked = true)
        val changed = locked.copy(domain = listOf("different.example"))
        val imported = listOf(locked.copy(), changed, rule("same-id"), rule("another"))

        val merged = SettingsManager.mergeRoutingRulesets(listOf(locked, rule("removed")), imported)
        val expected = listOf(locked, changed, imported[2], imported[3]).map { it.copy(id = "") }
        assertTrue(ensureRoutingRulesetIds(merged))

        assertEquals("same-id", merged.first().id)
        assertEquals(expected, merged.map { it.copy(id = "") })
        assertUniqueIds(merged)
    }

    @Test
    fun importedDuplicatesAreNotSilentlyDiscarded() {
        val imported = listOf(rule("duplicate"), rule("duplicate"))
        val merged = SettingsManager.mergeRoutingRulesets(emptyList(), imported)
        assertTrue(ensureRoutingRulesetIds(merged))
        assertEquals(2, merged.size)
        assertUniqueIds(merged)
    }

    @Test
    fun emptyImportRetainsOnlyLockedRules() {
        val locked = rule("locked", locked = true)
        assertEquals(listOf(locked), SettingsManager.mergeRoutingRulesets(listOf(rule("unlocked"), locked), emptyList()))
        assertTrue(SettingsManager.mergeRoutingRulesets(emptyList(), emptyList()).isEmpty())
    }

    private fun assertUniqueIds(rules: List<RulesetItem>) {
        assertTrue(rules.all { it.id.isNotBlank() })
        assertEquals(rules.size, rules.map { it.id }.toSet().size)
    }

    private fun rule(id: String, locked: Boolean = false) = RulesetItem(id = id, remarks = "same", locked = locked)
}
