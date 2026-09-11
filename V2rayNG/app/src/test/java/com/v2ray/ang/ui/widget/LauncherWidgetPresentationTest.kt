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
            R.string.widget_no_profile to "No profile selected",
            R.string.acc_start to "Start",
            R.string.acc_stop to "Stop",
        ).forEach { (id, text) -> whenever(context.getString(id)).thenReturn(text) }
    }

    @Test
    fun missingOrBlankProfileUsesPlaceholder() {
        listOf(null, profile.copy(name = " ")).forEach { selected ->
            val ui = LauncherWidgetState(selected, CoreConnectionState()).present(context)
            assertEquals("No profile selected", ui.profileName)
            assertFalse(ui.isRunning)
        }
    }

    @Test
    fun selectedNameDoesNotDependOnRunningProfile() {
        val ui = LauncherWidgetState(profile, CoreConnectionState("other")).present(context)
        assertEquals("Profile A", ui.profileName)
        assertTrue(ui.isRunning)
        assertEquals("Start", ui.startActionLabel)
        assertEquals("Stop", ui.stopActionLabel)
    }

    @Test
    fun connectionTestsDoNotChangeWidgetPresentation() {
        val connected = CoreConnectionState("a")
        val expected = LauncherWidgetState(profile, connected).present(context)
        listOf(
            connected.copy(isTesting = true),
            connected.copy(result = ConnectionTestResult(42, country = "DE", ipAddress = "192.0.2.1")),
            connected.copy(result = ConnectionTestResult(-1, errorMessage = "timeout")),
        ).forEach { connection ->
            assertEquals(expected, LauncherWidgetState(profile, connection).present(context))
        }
    }

    @Test
    fun stopOnlyChangesRunningState() {
        val running = LauncherWidgetState(profile, CoreConnectionState("a")).present(context)
        val stopped = LauncherWidgetState(profile, CoreConnectionState()).present(context)
        assertEquals(running.copy(isRunning = false), stopped)
    }
}
