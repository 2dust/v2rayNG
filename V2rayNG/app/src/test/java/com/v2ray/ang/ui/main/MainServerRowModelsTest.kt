package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MainServerRowModelsTest {

    private fun aether(block: ProfileItem.() -> Unit = {}) = ProfileItem.create(EConfigType.AETHER).apply(block)

    @Test
    fun anAetherRowNamesTheTunnelFromTheOutsideIn() {
        assertEquals("AETHER / WIREGUARD", serverProtocolDescription(aether()))
        assertEquals("AETHER / WIREGUARD → PSIPHON", serverProtocolDescription(aether { aetherProtocol = "wg"; aetherPsiphon = "chain" }))
        assertEquals("AETHER / TOR → MASQUE", serverProtocolDescription(aether { aetherProtocol = "masque"; aetherTor = "reverse" }))
        assertEquals("AETHER / MIM → TOR", serverProtocolDescription(aether { aetherProtocol = "mim"; aetherTor = "chain" }))
        // A carrier alone is the whole tunnel; no WARP protocol is named for it.
        assertEquals("AETHER / PSIPHON", serverProtocolDescription(aether { aetherProtocol = "wg"; aetherPsiphon = "only" }))
        assertEquals("AETHER / TOR", serverProtocolDescription(aether { aetherTor = "only" }))
        // A command written by hand is read the same way.
        assertEquals("AETHER / GOOL → TOR", serverProtocolDescription(aether { aetherCommand = "aether --gool --tor" }))
    }
}
