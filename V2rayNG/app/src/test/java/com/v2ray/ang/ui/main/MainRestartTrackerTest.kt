package com.v2ray.ang.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainRestartTrackerTest {

    @Test
    fun serverChangeRejectsCurrentAndConcurrentSelections() {
        val tracker = MainRestartTracker()

        assertFalse(tracker.beginServerChange("current", "current"))
        assertTrue(tracker.beginServerChange("current", "next"))
        assertTrue(tracker.hasPendingServerChange)
        assertFalse(tracker.beginServerChange("current", "other"))
    }

    @Test
    fun backgroundRestartBlocksTransientServiceStateUntilCompletion() {
        val tracker = MainRestartTracker()

        tracker.onRestarting()

        assertFalse(tracker.isIdle)
        assertFalse(tracker.hasPendingServerChange)
        assertNull(tracker.complete())
        assertTrue(tracker.isIdle)
    }

    @Test
    fun completedServerRestartReturnsRequestedGuid() {
        val tracker = MainRestartTracker()
        tracker.beginServerChange("current", "next")

        assertEquals("next", tracker.complete())
        assertTrue(tracker.isIdle)
    }

    @Test
    fun handledDaemonResultKeepsServerRestartPending() {
        val tracker = MainRestartTracker()
        tracker.beginServerChange("current", "next")

        assertFalse(tracker.completeUnhandledServerChange("next", handled = true))
        assertFalse(tracker.isIdle)
        assertEquals("next", tracker.complete())
    }

    @Test
    fun daemonAbsentCompletesMatchingServerRestart() {
        val tracker = MainRestartTracker()
        tracker.beginServerChange("current", "next")

        assertFalse(tracker.completeUnhandledServerChange("stale", handled = false))
        assertFalse(tracker.isIdle)
        assertTrue(tracker.completeUnhandledServerChange("next", handled = false))
        assertTrue(tracker.isIdle)
    }
}
