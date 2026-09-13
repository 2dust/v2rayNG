package com.v2ray.ang.ui.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ProxyChainMembersTest {
    @Test
    fun movingResolvesCurrentKeysAndKeepsDuplicateNamesDistinct() {
        val members = listOf("Same", "Other", "Same")
        val keys = listOf("first", "middle", "last")
        val moved = moveProxyChainMember(members, keys, "last", "first")!!
        assertEquals(listOf("Same", "Same", "Other"), moved.first)
        assertEquals(listOf("last", "first", "middle"), moved.second)
        assertEquals(listOf("Same", "Other", "Same"), members)
        assertEquals(listOf("first", "middle", "last"), keys)

        assertEquals(members to keys, moveProxyChainMember(moved.first, moved.second, "last", "middle"))
    }

    @Test
    fun missingKeysEmptyChainsAndUnchangedPositionsRejectMoves() {
        val members = listOf("One", "Two")
        val keys = listOf("one", "two")
        assertNull(moveProxyChainMember(members, keys, "missing", "two"))
        assertNull(moveProxyChainMember(members, keys, "one", "missing"))
        assertNull(moveProxyChainMember(members, keys, "one", "one"))
        assertNull(moveProxyChainMember(emptyList(), emptyList(), "one", "two"))
    }

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
}
