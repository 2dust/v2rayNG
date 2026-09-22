package com.v2ray.ang.ui.widget

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LauncherWidgetPresentationTest {
    private val context = mock<Context>()
    private val profile = WidgetProfile("a", "Profile A")

    @Test
    fun profileRefreshDoesNotShareTheTestCancellationMessageId() {
        assertNotEquals(AppConfig.MSG_MEASURE_DELAY_CANCEL, AppConfig.MSG_SELECTED_PROFILE_CHANGED)
    }

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
            val ui = LauncherWidgetState(selected, isRunning = false).present(context)
            assertEquals("No profile selected", ui.profileName)
            assertFalse(ui.isRunning)
        }
    }

    @Test
    fun selectedNameAndServiceActionsArePresented() {
        val ui = LauncherWidgetState(profile, isRunning = true).present(context)
        assertEquals("Profile A", ui.profileName)
        assertTrue(ui.isRunning)
        assertEquals("Start", ui.startActionLabel)
        assertEquals("Stop", ui.stopActionLabel)
    }

    @Test
    fun stopOnlyChangesRunningState() {
        val running = LauncherWidgetState(profile, isRunning = true).present(context)
        val stopped = LauncherWidgetState(profile, isRunning = false).present(context)
        assertEquals(running.copy(isRunning = false), stopped)
    }
}
