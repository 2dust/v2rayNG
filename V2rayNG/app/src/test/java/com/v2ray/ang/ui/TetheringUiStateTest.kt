package com.v2ray.ang.ui

import com.v2ray.ang.R
import com.v2ray.ang.shizuku.ShizukuTetheringService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TetheringUiStateTest {

    @Test
    fun hotspotStateIsDerivedFromTheActiveTetheringMask() {
        val unknown = TetheringUiState()
        assertFalse(unknown.tetheringStateKnown)
        assertFalse(unknown.hotspotEnabled)

        val disabled = TetheringUiState(activeTetheringTypes = 0)
        assertTrue(disabled.tetheringStateKnown)
        assertFalse(disabled.hotspotEnabled)

        val usbOnly = TetheringUiState(
            activeTetheringTypes = 1 shl ShizukuTetheringService.TETHERING_TYPE_USB,
        )
        assertFalse(usbOnly.hotspotEnabled)

        val wifi = TetheringUiState(
            activeTetheringTypes = 1 shl ShizukuTetheringService.TETHERING_TYPE_WIFI,
        )
        assertTrue(wifi.hotspotEnabled)
    }

    @Test
    fun actionMappingPreservesTransientAndFailClosedStates() {
        val connecting = TetheringUiState(operation = TetheringOperation.CONNECTING)
        assertEquals(
            R.string.shizuku_routing_status_connecting,
            routingAction(connecting, serviceConnected = false, platformSupported = true).statusRes,
        )
        assertEquals(
            R.string.shizuku_hotspot_status_connecting,
            hotspotAction(connecting, serviceConnected = false, platformSupported = true).statusRes,
        )

        val waiting = TetheringUiState(
            routingState = ShizukuTetheringService.ROUTING_STATE_WAITING,
            activeTetheringTypes = 1 shl ShizukuTetheringService.TETHERING_TYPE_WIFI,
        )
        val routing = routingAction(waiting, serviceConnected = true, platformSupported = true)
        assertEquals(R.string.shizuku_routing_status_waiting, routing.statusRes)
        assertEquals(R.string.shizuku_routing_disable, routing.buttonRes)
        assertTrue(routing.enabled)
        assertEquals(
            R.string.shizuku_hotspot_status_waiting,
            hotspotAction(waiting, serviceConnected = true, platformSupported = true).statusRes,
        )

        val unsupported = hotspotAction(
            TetheringUiState(),
            serviceConnected = true,
            platformSupported = false,
        )
        assertEquals(R.string.shizuku_hotspot_status_unsupported, unsupported.statusRes)
        assertFalse(unsupported.enabled)
    }
}
