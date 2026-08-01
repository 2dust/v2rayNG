package com.v2ray.ang.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnderlyingNetworkStateTrackerTest {
    private val tracker = UnderlyingNetworkStateTracker<String>()

    @Test
    fun initialAndRepeatedAvailabilityAreNotHandovers() {
        assertFalse(tracker.onAvailable("wifi"))
        assertFalse(tracker.onAvailable("wifi"))
        assertTrue(tracker.isCurrent("wifi"))
    }

    @Test
    fun changingNetworksIsAHandover() {
        tracker.onAvailable("cellular")

        assertTrue(tracker.onAvailable("wifi"))
        assertTrue(tracker.isCurrent("wifi"))
    }

    @Test
    fun losingAndReacquiringAnUnderlayIsAHandover() {
        tracker.onAvailable("wifi")

        assertTrue(tracker.onLost("wifi"))
        assertTrue(tracker.onAvailable("wifi"))
    }

    @Test
    fun losingAStaleNetworkDoesNotClearTheCurrentUnderlay() {
        tracker.onAvailable("cellular")
        tracker.onAvailable("wifi")

        assertFalse(tracker.onLost("cellular"))
        assertTrue(tracker.isCurrent("wifi"))
    }

    @Test
    fun resetMakesTheNextNetworkInitialAgain() {
        tracker.onAvailable("wifi")
        tracker.reset()

        assertFalse(tracker.onAvailable("cellular"))
    }
}
