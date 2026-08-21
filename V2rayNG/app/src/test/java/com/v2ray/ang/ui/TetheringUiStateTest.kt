package com.v2ray.ang.ui

import com.v2ray.ang.R
import com.v2ray.ang.shizuku.ShizukuTetheringService
import com.v2ray.ang.shizuku.tetheringTypeBit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
            routingAction(connecting, serviceConnected = false).statusRes,
        )
        assertEquals(
            R.string.shizuku_hotspot_status_connecting,
            hotspotAction(connecting, serviceConnected = false).statusRes,
        )

        val waiting = TetheringUiState(
            routingState = ShizukuTetheringService.ROUTING_STATE_WAITING,
            activeTetheringTypes = 1 shl ShizukuTetheringService.TETHERING_TYPE_WIFI,
        )
        val routing = routingAction(waiting, serviceConnected = true)
        assertEquals(R.string.shizuku_routing_status_waiting, routing.statusRes)
        assertTrue(routing.enabled)
        assertEquals(
            R.string.shizuku_hotspot_status_waiting,
            hotspotAction(waiting, serviceConnected = true).statusRes,
        )
    }

    @Test
    fun reportsTheObservedIpModeForEachActiveDownstream() {
        val wifi = tetheringTypeBit(ShizukuTetheringService.TETHERING_TYPE_WIFI)
        val usb = tetheringTypeBit(ShizukuTetheringService.TETHERING_TYPE_USB)
        val state = TetheringUiState(
            activeTetheringTypes = wifi or usb,
            ipv6TetheringTypes = wifi,
            ipv6Enabled = true,
        )

        assertEquals(
            TetheringIpMode.DUAL_STACK,
            state.ipMode(ShizukuTetheringService.TETHERING_TYPE_WIFI),
        )
        assertEquals(
            TetheringIpMode.IPV4_ONLY,
            state.ipMode(ShizukuTetheringService.TETHERING_TYPE_USB),
        )
    }

    @Test
    fun hidesTheIpModeWhenIpv6IsDisabledOrTheDownstreamIsInactive() {
        val wifi = tetheringTypeBit(ShizukuTetheringService.TETHERING_TYPE_WIFI)
        val disabled = TetheringUiState(activeTetheringTypes = wifi)
            .ipMode(ShizukuTetheringService.TETHERING_TYPE_WIFI)
        val inactive = TetheringUiState(
            activeTetheringTypes = wifi,
            ipv6TetheringTypes = wifi,
            ipv6Enabled = true,
        ).ipMode(ShizukuTetheringService.TETHERING_TYPE_USB)

        assertNull(disabled)
        assertNull(inactive)
    }

    @Test
    fun doesNotMistakeAnUnavailableIpv6ProbeForIpv4Only() {
        val wifi = tetheringTypeBit(ShizukuTetheringService.TETHERING_TYPE_WIFI)
        val state = TetheringUiState(
            activeTetheringTypes = wifi,
            ipv6TetheringTypes = ShizukuTetheringService.TETHERING_TYPES_UNKNOWN,
            ipv6Enabled = true,
        )

        assertEquals(
            TetheringIpMode.UNKNOWN,
            state.ipMode(ShizukuTetheringService.TETHERING_TYPE_WIFI),
        )
    }
}
