package com.v2ray.ang.ui.server

import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProxyChainMembersTest {
    @Test
    fun removalFollowsThePendingKeyAfterReordering() {
        val pendingKey = "two"
        val members = listOf("Two", "One", "Three")
        val keys = listOf("two", "one", "three")

        val (remainingMembers, remainingKeys) = withoutProxyChainMember(members, keys, pendingKey)

        assertEquals(listOf("One", "Three"), remainingMembers)
        assertEquals(listOf("one", "three"), remainingKeys)
        assertEquals(listOf("Two", "One", "Three"), members)
        assertEquals(listOf("two", "one", "three"), keys)
    }

    @Test
    fun duplicateNamesDoNotChangeWhichMemberIsRemoved() {
        val (members, keys) = withoutProxyChainMember(
            listOf("Same", "Same", "Other"),
            listOf("first", "second", "third"),
            "second",
        )

        assertEquals(listOf("Same", "Other"), members)
        assertEquals(listOf("first", "third"), keys)
    }

    @Test
    fun missingMemberLeavesBothListsUnchanged() {
        val members = listOf("One", "Three")
        val keys = listOf("one", "three")

        val (remainingMembers, remainingKeys) = withoutProxyChainMember(members, keys, "two")

        assertSame(members, remainingMembers)
        assertSame(keys, remainingKeys)
    }

    @Test
    fun emptyChainRemainsEmpty() {
        assertEquals(
            emptyList<String>() to emptyList<String>(),
            withoutProxyChainMember(emptyList(), emptyList(), "missing"),
        )
    }

    @Test
    fun blankMemberIsRemovedWithItsOwnKey() {
        assertEquals(
            listOf("One") to listOf("one"),
            withoutProxyChainMember(listOf("One", ""), listOf("one", "blank"), "blank"),
        )
    }

    @Test
    fun anAetherMemberCanStandAnywhereButOnlyOnce() {
        assertFalse(hasSecondAetherMember(listOf(EConfigType.AETHER, EConfigType.VLESS)))
        assertFalse(hasSecondAetherMember(listOf(EConfigType.VLESS, EConfigType.AETHER)))
        assertFalse(hasSecondAetherMember(listOf(EConfigType.VLESS, EConfigType.AETHER, EConfigType.TROJAN)))
        assertFalse(hasSecondAetherMember(listOf(EConfigType.VLESS, EConfigType.TROJAN)))
        assertFalse(hasSecondAetherMember(listOf(null, EConfigType.VLESS)))
        assertTrue(hasSecondAetherMember(listOf(EConfigType.AETHER, EConfigType.AETHER)))
        assertTrue(hasSecondAetherMember(listOf(EConfigType.AETHER, EConfigType.VLESS, EConfigType.AETHER)))
    }
}
