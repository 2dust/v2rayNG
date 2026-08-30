package com.v2ray.ang.ui.compose

import com.v2ray.ang.extension.AccessibilityLiveRegionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveRegionMessageTrackerTest {

    @Test
    fun ignoresMessagesWithoutLiveRegionMode() {
        val tracker = LiveRegionMessageTracker { 0L }

        assertNull(tracker.next(AppSnackbarMessage(message = "Visible only")))
    }

    @Test
    fun ignoresBlankMessages() {
        val tracker = LiveRegionMessageTracker { 0L }

        assertNull(
            tracker.next(
                AppSnackbarMessage(
                    message = " ",
                    liveRegionMode = AccessibilityLiveRegionMode.POLITE,
                )
            )
        )
    }

    @Test
    fun suppressesOnlyRecentDuplicatesWithTheSameMode() {
        var now = 100L
        val tracker = LiveRegionMessageTracker { now }
        val polite = AppSnackbarMessage(
            message = "Service state",
            liveRegionMode = AccessibilityLiveRegionMode.POLITE,
        )

        assertEquals(1L, tracker.next(polite)?.id)
        assertNull(tracker.next(polite))

        val assertive = polite.copy(liveRegionMode = AccessibilityLiveRegionMode.ASSERTIVE)
        assertEquals(2L, tracker.next(assertive)?.id)

        now += DuplicateLiveRegionMessageWindowMs
        assertEquals(3L, tracker.next(assertive)?.id)
    }

    @Test
    fun usesTheAccessibilityMessageWithoutChangingTheVisibleMessage() {
        val tracker = LiveRegionMessageTracker { 0L }
        val event = AppSnackbarMessage(
            message = "Service started successfully",
            liveRegionMode = AccessibilityLiveRegionMode.ASSERTIVE,
            accessibilityMessage = "Service started successfully. Now connected to Lab.",
        )

        assertEquals(
            "Service started successfully. Now connected to Lab.",
            tracker.next(event)?.text,
        )
        assertEquals("Service started successfully", event.message)
    }
}
