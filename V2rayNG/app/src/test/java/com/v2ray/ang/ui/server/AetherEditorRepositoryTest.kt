package com.v2ray.ang.ui.server

import com.v2ray.ang.enums.AetherKeyKind
import com.v2ray.ang.enums.AetherProtocol
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AetherEditorRepositoryTest {

    @Test
    fun theProcessesAloneTellTheSessionWhereTheyCanBeListed() {
        // The core of a latency test listens on the Aether port as well; it is no session.
        assertNull(AetherEditorRepository.sessionOf(protocol = null, processesListed = true) { true })
        assertEquals(
            AetherSession(AetherProtocol.MASQUE),
            AetherEditorRepository.sessionOf(AetherProtocol.MASQUE, processesListed = true) { false }
        )
    }

    @Test
    fun withoutTheProcessesAListenerOnTheAetherPortStandsInForTheSession() {
        assertEquals(AetherSession(protocol = null), AetherEditorRepository.sessionOf(protocol = null, processesListed = false) { true })
        assertNull(AetherEditorRepository.sessionOf(protocol = null, processesListed = false) { false })
        assertEquals(
            AetherSession(AetherProtocol.WIREGUARD),
            AetherEditorRepository.sessionOf(AetherProtocol.WIREGUARD, processesListed = false) { true }
        )
    }

    @Test
    fun aSessionKeepsTheKeysItUsesAndNoOthers() {
        val masque = AetherSession(AetherProtocol.MASQUE)
        assertTrue(masque.usesKeysOf(AetherKeyKind.ALL))
        assertTrue(masque.usesKeysOf(AetherKeyKind.MASQUE))
        // Masque-in-masque replaces the MASQUE key as its outer hop key.
        assertTrue(masque.usesKeysOf(AetherKeyKind.MIM))
        assertFalse(masque.usesKeysOf(AetherKeyKind.WIREGUARD))
        assertFalse(masque.usesKeysOf(AetherKeyKind.GOOL))

        // Warp-in-warp uses the WireGuard key as its outer hop key, and an inner one of its own.
        val gool = AetherSession(AetherProtocol.GOOL)
        assertTrue(gool.usesKeysOf(AetherKeyKind.WIREGUARD))
        assertTrue(gool.usesKeysOf(AetherKeyKind.GOOL))
        assertFalse(gool.usesKeysOf(AetherKeyKind.MASQUE))
        assertFalse(gool.usesKeysOf(AetherKeyKind.MIM))

        // Only the listener was seen, so the session may use any key.
        val unknown = AetherSession(protocol = null)
        AetherKeyKind.entries.forEach { assertTrue(unknown.usesKeysOf(it), it.type) }
    }
}
