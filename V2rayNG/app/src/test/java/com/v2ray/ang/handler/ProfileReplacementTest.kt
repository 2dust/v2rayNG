package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProfileReplacementTest {

    @Test
    fun `prefers a full match over a remarks-only match`() {
        val profiles = linkedMapOf(
            "remarks" to profile(remarks = "selected", server = "other"),
            "full" to profile(remarks = "selected", server = "host", port = "443", password = "secret"),
        )

        val result = ProfileReplacement.findSelectedReplacement(
            profiles = profiles,
            currentSelection = "old",
            selectedProfile = profile(
                remarks = " selected ",
                server = "HOST",
                port = "443",
                password = "secret",
            ),
        )

        assertEquals("full", result)
    }

    @Test
    fun `uses a remarks match when no full match exists`() {
        val profiles = linkedMapOf(
            "other" to profile(remarks = "other", server = "host"),
            "remarks" to profile(remarks = "selected", server = "other"),
        )

        val result = ProfileReplacement.findSelectedReplacement(
            profiles = profiles,
            currentSelection = "old",
            selectedProfile = profile(remarks = "selected", server = "host"),
        )

        assertEquals("remarks", result)
    }

    @Test
    fun `matches endpoint and password when remarks are unavailable`() {
        val profiles = linkedMapOf(
            "endpoint" to profile(server = "host", port = "443", password = "secret"),
            "other" to profile(server = "other", port = "443", password = "secret"),
        )

        val result = ProfileReplacement.findSelectedReplacement(
            profiles = profiles,
            currentSelection = "old",
            selectedProfile = profile(server = "HOST", port = "443", password = "secret"),
        )

        assertEquals("endpoint", result)
    }

    @Test
    fun `falls back to the first replacement profile`() {
        val profiles = linkedMapOf(
            "first" to profile(server = "first"),
            "second" to profile(server = "second"),
        )

        val result = ProfileReplacement.findSelectedReplacement(
            profiles = profiles,
            currentSelection = "old",
            selectedProfile = profile(server = "unmatched"),
        )

        assertEquals("first", result)
    }

    @Test
    fun `selects the first profile when there is no current selection`() {
        val result = ProfileReplacement.findSelectedReplacement(
            profiles = linkedMapOf(
                "first" to profile(server = "first"),
                "second" to profile(server = "second"),
            ),
            currentSelection = null,
            selectedProfile = null,
        )

        assertEquals("first", result)
    }

    @Test
    fun `keeps an existing selection when it is outside the replaced group`() {
        val result = ProfileReplacement.findSelectedReplacement(
            profiles = mapOf("candidate" to profile(server = "host")),
            currentSelection = "other-group",
            selectedProfile = null,
        )

        assertNull(result)
    }

    @Test
    fun `removes only unreferenced superseded payloads`() {
        val result = ProfileReplacement.findRemovablePayloads(
            replacedServers = listOf("orphan", "selected", "replacement", "cross-group"),
            replacementServers = setOf("replacement"),
            protectedServer = "selected",
            serversReferencedByOtherGroups = setOf("cross-group"),
        )

        assertEquals(setOf("orphan"), result)
    }

    @Test
    fun `keeps all payloads when another group index is unreadable`() {
        val result = ProfileReplacement.findRemovablePayloads(
            replacedServers = listOf("candidate"),
            replacementServers = emptySet(),
            protectedServer = null,
            serversReferencedByOtherGroups = null,
        )

        assertEquals(emptySet<String>(), result)
    }

    @Test
    fun `repeated subscription updates keep one copy of an imported group`() {
        val stored = mutableMapOf<String, ProfileItem>()
        var index = listOf<String>()

        repeat(3) { update ->
            val batch = linkedMapOf(
                "node-$update" to profile(remarks = "node", server = "host"),
                "group-$update" to group(EConfigType.POLICYGROUP, "Auto"),
            )
            val kept = ProfileReplacement.findKeptGroups(
                replacedProfiles = index.associateWith(stored::get),
                profiles = batch.values,
            )
            stored.putAll(batch)
            index = batch.keys.reversed() + kept
        }

        assertEquals(listOf("group-2", "node-2"), index)
    }

    @Test
    fun `keeps hand-made groups and drops groups imported again`() {
        val replaced = linkedMapOf(
            "imported" to group(EConfigType.POLICYGROUP, "Auto"),
            "earlier-copy" to group(EConfigType.POLICYGROUP, "Auto"),
            "hand-made" to group(EConfigType.POLICYGROUP, "Mine"),
            "hand-made-chain" to group(EConfigType.PROXYCHAIN, "Auto"),
            "node" to profile(remarks = "node"),
            "unreadable" to null,
        )

        val result = ProfileReplacement.findKeptGroups(
            replacedProfiles = replaced,
            profiles = listOf(profile(remarks = "node"), group(EConfigType.POLICYGROUP, "Auto")),
        )

        assertEquals(listOf("hand-made", "hand-made-chain"), result)
    }

    private fun group(type: EConfigType, remarks: String) = ProfileItem.create(type).apply {
        this.remarks = remarks
    }

    private fun profile(
        remarks: String = "",
        server: String = "",
        port: String = "",
        password: String = "",
    ) = ProfileItem.create(EConfigType.VMESS).apply {
        this.remarks = remarks
        this.server = server
        this.serverPort = port
        this.password = password
    }
}
