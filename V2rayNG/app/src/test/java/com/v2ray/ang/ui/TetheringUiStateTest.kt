package com.v2ray.ang.ui

import com.v2ray.ang.R
import com.v2ray.ang.dto.HotspotRoutingSnapshot
import com.v2ray.ang.shizuku.ShizukuTetheringService
import com.v2ray.ang.shizuku.TetheringStatusSnapshot
import com.v2ray.ang.shizuku.tetheringTypeBit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TetheringUiStateTest {

    @Test
    fun failedShutdownRetainsStopEvenWhenMainCoreIsStopped() {
        val state = TetheringUiState(
            serviceConnected = true,
            hasRoutingSession = true,
            routingState = ShizukuTetheringService.ROUTING_STATE_ERROR,
            coreRunning = false,
        )
        assertTrue(state.routingSessionEnabled)
        assertTrue(routingAction(state).enabled)
        assertEquals(TetheringOperation.STOPPING_ROUTING, state.operationFor(ShizukuAction.ToggleRouting))
        assertFalse(state.withServiceConnection(false).hasRoutingSession)
    }

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
            routingAction(connecting).statusRes,
        )
        assertEquals(
            R.string.shizuku_hotspot_status_connecting,
            hotspotAction(connecting).statusRes,
        )

        val waiting = TetheringUiState(
            routingState = ShizukuTetheringService.ROUTING_STATE_WAITING,
            activeTetheringTypes = 1 shl ShizukuTetheringService.TETHERING_TYPE_WIFI,
        )
        val connectedWaiting = waiting.copy(serviceConnected = true)
        val routing = routingAction(connectedWaiting)
        assertEquals(R.string.shizuku_routing_status_waiting, routing.statusRes)
        assertTrue(routing.enabled)
        assertEquals(
            R.string.shizuku_hotspot_status_waiting,
            hotspotAction(connectedWaiting).statusRes,
        )
    }

    @Test
    fun viewModelStateStartsDisconnectedAndResetsShellStateOnDisconnect() {
        val initial = TetheringUiState()
        assertFalse(initial.serviceConnected)

        val connected = initial.withServiceConnection(true).copy(
            operation = TetheringOperation.STARTING_ROUTING,
            routingState = ShizukuTetheringService.ROUTING_STATE_ACTIVE_NATIVE,
            routingDetail = "testtun0 · Lab",
            activeTetheringTypes = tetheringTypeBit(ShizukuTetheringService.TETHERING_TYPE_WIFI),
            ipv6TetheringTypes = 0,
            coreRunning = true,
        )
        val disconnected = connected.withServiceConnection(false)

        assertFalse(disconnected.serviceConnected)
        assertEquals(TetheringOperation.NONE, disconnected.operation)
        assertEquals(ShizukuTetheringService.ROUTING_STATE_DISABLED, disconnected.routingState)
        assertEquals("", disconnected.routingDetail)
        assertEquals(ShizukuTetheringService.TETHERING_TYPES_UNKNOWN, disconnected.activeTetheringTypes)
        assertEquals(ShizukuTetheringService.TETHERING_TYPES_UNKNOWN, disconnected.ipv6TetheringTypes)
        assertTrue(disconnected.coreRunning)
    }

    @Test
    fun viewModelStateMapsCoreAndUserServiceSnapshots() {
        val coreRunning = TetheringUiState().withCoreRunning(true)
        assertTrue(coreRunning.coreRunning)
        assertFalse(coreRunning.withCoreSnapshot(HotspotRoutingSnapshot()).coreRunning)

        val status = TetheringStatusSnapshot(
            routingState = ShizukuTetheringService.ROUTING_STATE_ACTIVE_HEV,
            routingDetail = "testtun4 · Lab",
            activeTetheringTypes = tetheringTypeBit(ShizukuTetheringService.TETHERING_TYPE_USB),
            ipv6TetheringTypes = 0,
            warning = ShizukuTetheringService.RESULT_OK,
        )
        val loaded = coreRunning.copy(operation = TetheringOperation.CHECKING)
            .withTetheringStatus(status, ipv6Enabled = true)

        assertEquals(TetheringOperation.NONE, loaded.operation)
        assertEquals(status.routingState, loaded.routingState)
        assertEquals(status.routingDetail, loaded.routingDetail)
        assertEquals(status.activeTetheringTypes, loaded.activeTetheringTypes)
        assertEquals(status.ipv6TetheringTypes, loaded.ipv6TetheringTypes)
        assertTrue(loaded.ipv6Enabled)
    }

    @Test
    fun viewModelActionMappingCoversEveryActionAndToggleDirection() {
        assertNull(TetheringUiState().operationFor(ShizukuAction.RequestPermission))
        assertNull(TetheringUiState().operationFor(ShizukuAction.Refresh))
        assertEquals(
            TetheringOperation.STARTING_ROUTING,
            TetheringUiState().operationFor(ShizukuAction.ToggleRouting),
        )
        assertEquals(
            TetheringOperation.STOPPING_ROUTING,
            TetheringUiState(
                routingState = ShizukuTetheringService.ROUTING_STATE_WAITING,
            ).operationFor(ShizukuAction.ToggleRouting),
        )
        assertEquals(
            TetheringOperation.STARTING_HOTSPOT,
            TetheringUiState(activeTetheringTypes = 0).operationFor(ShizukuAction.ToggleHotspot),
        )
        assertEquals(
            TetheringOperation.STOPPING_HOTSPOT,
            TetheringUiState(
                activeTetheringTypes = tetheringTypeBit(ShizukuTetheringService.TETHERING_TYPE_WIFI),
            ).operationFor(ShizukuAction.ToggleHotspot),
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
