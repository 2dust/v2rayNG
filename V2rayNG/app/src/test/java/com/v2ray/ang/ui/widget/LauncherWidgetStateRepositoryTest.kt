package com.v2ray.ang.ui.widget

import com.v2ray.ang.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherWidgetStateRepositoryTest {
    @Test
    fun layoutBreakpointsSelectTheFourSupportedLayouts() {
        assertEquals(LauncherWidgetLayout.COMPACT, launcherWidgetLayoutForWidth(48f))
        assertEquals(LauncherWidgetLayout.COMPACT, launcherWidgetLayoutForWidth(109.9f))
        assertEquals(LauncherWidgetLayout.MEDIUM, launcherWidgetLayoutForWidth(110f))
        assertEquals(LauncherWidgetLayout.MEDIUM, launcherWidgetLayoutForWidth(239f))
        assertEquals(LauncherWidgetLayout.WIDE, launcherWidgetLayoutForWidth(240f))
        assertEquals(LauncherWidgetLayout.WIDE, launcherWidgetLayoutForWidth(319f))
        assertEquals(LauncherWidgetLayout.EXTRA_WIDE, launcherWidgetLayoutForWidth(320f))
    }

    @Test
    fun explicitStoppedEventWinsDuringCoreShutdownRace() {
        assertTrue(launcherWidgetRunningState(true, serviceReportedStopped = false))
        assertFalse(launcherWidgetRunningState(true, serviceReportedStopped = true))
        assertFalse(launcherWidgetRunningState(false, serviceReportedStopped = false))
    }

    @Test
    fun mediumLayoutUsesConnectedAndDisconnectedStatusLabels() {
        assertEquals(
            R.string.widget_status_connected,
            launcherWidgetServiceStatusResource(isRunning = true),
        )
        assertEquals(
            R.string.widget_status_disconnected,
            launcherWidgetServiceStatusResource(isRunning = false),
        )
    }

    @Test
    fun successfulConnectionDetailsIncludeDelayAndCountry() {
        assertEquals(
            "42 ms · DE",
            buildLauncherWidgetConnectionDetails(
                delayLabel = "42 ms",
                errorMessage = "",
                country = "DE",
                fallbackError = "No details",
            )
        )
    }

    @Test
    fun successfulConnectionDetailsOmitUnavailableLocationData() {
        assertEquals(
            "42 ms",
            buildLauncherWidgetConnectionDetails(
                delayLabel = "42 ms",
                errorMessage = "",
                country = " ",
                fallbackError = "No details",
            )
        )
    }

    @Test
    fun failedConnectionDetailsUseErrorOrLocalizedFallback() {
        assertEquals(
            "timeout",
            buildLauncherWidgetConnectionDetails(
                delayLabel = null,
                errorMessage = "timeout",
                country = "DE",
                fallbackError = "No details",
            )
        )
        assertEquals(
            "No details",
            buildLauncherWidgetConnectionDetails(
                delayLabel = null,
                errorMessage = "",
                country = null,
                fallbackError = "No details",
            )
        )
    }

    @Test
    fun maximumFontScaleUsesCompactTwoLineTextMetrics() {
        assertEquals(
            LauncherWidgetTextMetrics(14f, 12f, 8f, 4f, 5f),
            launcherWidgetTextMetrics(1f),
        )
        assertEquals(
            LauncherWidgetTextMetrics(14f, 12f, 8f, 4f, 5f),
            launcherWidgetTextMetrics(1.79f),
        )
        assertEquals(
            LauncherWidgetTextMetrics(12f, 8f, 4f, 2f, 0f),
            launcherWidgetTextMetrics(1.8f),
        )
    }
}
