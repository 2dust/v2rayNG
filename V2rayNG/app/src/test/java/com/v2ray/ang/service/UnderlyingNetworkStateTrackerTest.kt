package com.v2ray.ang.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnderlyingNetworkStateTrackerTest {
    private val tracker = UnderlyingNetworkStateTracker<String>()

    @Test
    fun initialAndRepeatedAvailabilityAreNotTransitions() {
        assertFalse(tracker.onAvailable("wifi"))
        assertFalse(tracker.onAvailable("wifi"))
        assertTrue(tracker.isCurrent("wifi"))
    }

    @Test
    fun changingNetworksIsATransition() {
        tracker.onAvailable("wifi")

        assertTrue(tracker.onAvailable("cellular"))
        assertTrue(tracker.isCurrent("cellular"))
    }

    @Test
    fun losingAndReacquiringAnUnderlayIsATransition() {
        tracker.onAvailable("wifi")

        assertTrue(tracker.onLost("wifi"))
        assertTrue(tracker.onAvailable("wifi"))
    }

    @Test
    fun losingAStaleNetworkDoesNotClearTheCurrentUnderlay() {
        tracker.onAvailable("wifi")
        tracker.onAvailable("cellular")

        assertFalse(tracker.onLost("wifi"))
        assertTrue(tracker.isCurrent("cellular"))
    }

    @Test
    fun resetMakesTheNextNetworkInitialAgain() {
        tracker.onAvailable("wifi")
        tracker.reset()

        assertFalse(tracker.onAvailable("cellular"))
    }
}
