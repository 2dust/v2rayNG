package com.v2ray.ang.ui.widget

import android.content.Context
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreConnectionState
import com.v2ray.ang.dto.ConnectionTestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LauncherWidgetPresentationTest {
    private val context = mock<Context>()
    private val profile = WidgetProfile("a", "Profile A")

    @Before
    fun strings() {
        mapOf(
            R.string.widget_status_connected to "Connected",
            R.string.widget_status_disconnected to "Disconnected",
            R.string.widget_no_profile to "No profile selected",
            R.string.connection_test_testing to "Testing...",
            R.string.connection_test_empty_message to "Test failed",
            R.string.acc_start to "Start",
            R.string.acc_stop to "Stop",
            R.string.connection_test_pending to "Test connection",
            R.string.title_service_restart to "Restart service",
        ).forEach { (id, text) -> whenever(context.getString(id)).thenReturn(text) }
        whenever(context.getString(R.string.server_test_delay_value, 42L)).thenReturn("42 ms")
    }

    @Test
    fun emptyAndDisconnectedStatesHaveNoTestDetails() {
        val empty = LauncherWidgetState(null, CoreConnectionState()).present(context)
        assertEquals("No profile selected", empty.profileName)
        assertEquals("Disconnected", empty.serviceStatus)
        assertEquals("Disconnected", empty.connectionStatus)
        assertFalse(empty.canTest)

        val selected = LauncherWidgetState(profile, CoreConnectionState()).present(context)
        assertEquals("Profile A", selected.profileName)
        assertEquals("Disconnected", selected.serviceStatus)
    }

    @Test
    fun connectedShowsDelayAndCountryButNeverIp() {
        val state = CoreConnectionState("a", result = ConnectionTestResult(
            42, country = " DE ", ipAddress = "192.0.2.1"
        ))
        val ui = LauncherWidgetState(profile, state).present(context)
        assertEquals("Connected", ui.serviceStatus)
        assertEquals("Connected · 42 ms · DE", ui.connectionStatus)
        assertEquals(LauncherWidgetStatusTone.SUCCESS, ui.connectionStatusTone)
        assertTrue(ui.canTest)
    }

    @Test
    fun blankCountryDoesNotLeaveSeparator() {
        val state = CoreConnectionState("a", result = ConnectionTestResult(42, country = " "))
        assertEquals("Connected · 42 ms", LauncherWidgetState(profile, state).present(context).connectionStatus)
    }

    @Test
    fun testingDisablesTestActionWithoutDisconnecting() {
        val ui = LauncherWidgetState(profile, CoreConnectionState("a", isTesting = true)).present(context)
        assertEquals("Connected", ui.serviceStatus)
        assertEquals("Connected · Testing...", ui.connectionStatus)
        assertFalse(ui.canTest)
    }

    @Test
    fun failureUsesErrorMessageOrLocalizedFallback() {
        listOf("timeout" to "timeout", "" to "Test failed").forEach { (error, expected) ->
            val state = CoreConnectionState("a", result = ConnectionTestResult(-1, errorMessage = error))
            val ui = LauncherWidgetState(profile, state).present(context)
            assertEquals("Connected · $expected", ui.connectionStatus)
            assertEquals(LauncherWidgetStatusTone.ERROR, ui.connectionStatusTone)
        }
    }

    @Test
    fun largeFontLayoutReducesTextAndPaddingAtThreshold() {
        val normal = launcherWidgetTextMetrics(1f)
        val large = launcherWidgetTextMetrics(2f)
        assertEquals(normal, launcherWidgetTextMetrics(1.79f))
        assertEquals(large, launcherWidgetTextMetrics(1.8f))
        assertTrue(large.profileFontSizeSp < normal.profileFontSizeSp)
        assertTrue(large.statusFontSizeSp < normal.statusFontSizeSp)
        assertEquals(0f, large.verticalPaddingDp)
    }
}
