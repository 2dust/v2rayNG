package com.v2ray.ang.ui.server

import com.v2ray.ang.dto.entities.AetherSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AetherEditorStateTest {
    @Test
    fun loadingAndSavingDisableEveryProtocolControl() {
        listOf(AetherEditorState(), AetherEditorState(loading = false, saving = true)).forEach {
            assertFalse(it.editable)
            assertFalse(it.masqueEnabled)
            assertFalse(it.fragmentationEnabled)
            assertFalse(it.fragmentRangeEnabled)
        }
    }

    @Test
    fun wireguardDisablesMasqueAndSwitchingBackPreservesItsSettings() {
        val masque = AetherEditorState(loading = false,
            settings = AetherSettings(carrier = "h2", fragmentSize = "20-40", fragmentDelay = "3-8"))
        assertTrue(masque.masqueEnabled)
        assertTrue(masque.fragmentRangeEnabled)
        val wireguard = masque.copy(settings = masque.settings.copy(protocol = "wg"))
        assertTrue(wireguard.editable)
        assertFalse(wireguard.masqueEnabled)
        assertFalse(wireguard.fragmentationEnabled)
        assertFalse(wireguard.fragmentRangeEnabled)
        val restored = wireguard.copy(settings = wireguard.settings.copy(protocol = "masque"))
        assertEquals(masque.settings, restored.settings)
        assertTrue(restored.fragmentRangeEnabled)
    }

    @Test
    fun http3AndFragmentOffDisableOnlyTheInapplicableControls() {
        val http3 = AetherEditorState(loading = false, settings = AetherSettings(carrier = "h3"))
        assertTrue(http3.masqueEnabled)
        assertFalse(http3.fragmentationEnabled)
        assertFalse(http3.fragmentRangeEnabled)
        val fragmentOff = http3.copy(settings = http3.settings.copy(carrier = "h2", fragment = false))
        assertTrue(fragmentOff.fragmentationEnabled)
        assertFalse(fragmentOff.fragmentRangeEnabled)
    }
}
