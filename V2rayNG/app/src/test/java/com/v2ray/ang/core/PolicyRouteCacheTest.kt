package com.v2ray.ang.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyRouteCacheTest {
    @After
    fun tearDown() = PolicyRouteCache.clear()

    @Test
    fun keepsRoutesSeparateByNetworkAndProfile() {
        assertTrue(PolicyRouteCache.remember("wifi:home", "group-a", "proxy-a"))
        assertTrue(PolicyRouteCache.remember("cellular:310260", "group-a", "proxy-b"))
        assertTrue(PolicyRouteCache.remember("wifi:home", "group-b", "proxy-c"))

        assertEquals("proxy-a", PolicyRouteCache.lookup("wifi:home", "group-a"))
        assertEquals("proxy-b", PolicyRouteCache.lookup("cellular:310260", "group-a"))
        assertEquals("proxy-c", PolicyRouteCache.lookup("wifi:home", "group-b"))
    }

    @Test
    fun clearInvalidatesInFlightWrites() {
        PolicyRouteCache.setCurrentNetwork("wifi:home", 100L)
        val snapshot = PolicyRouteCache.snapshot()

        PolicyRouteCache.clear()

        assertFalse(PolicyRouteCache.remember("wifi:home", "group-a", "proxy-a", snapshot.generation))
        assertNull(PolicyRouteCache.snapshot().networkKey)
        assertNull(PolicyRouteCache.lookup("wifi:home", "group-a"))
    }

    @Test
    fun freshObservatoryTargetReplacesPreviousRoute() {
        assertTrue(PolicyRouteCache.remember("wifi:home", "group-a", "proxy-old"))
        assertTrue(PolicyRouteCache.remember("wifi:home", "group-a", "proxy-fresh"))

        assertEquals("proxy-fresh", PolicyRouteCache.lookup("wifi:home", "group-a"))
    }

    @Test
    fun networkSwitchInvalidatesInFlightCurrentRouteWrite() {
        PolicyRouteCache.setCurrentNetwork("wifi:home", 100L)
        val snapshot = PolicyRouteCache.snapshot()

        PolicyRouteCache.setCurrentNetwork("cellular:310260", 200L)

        assertFalse(PolicyRouteCache.rememberCurrent(snapshot, "group-a", "proxy-a"))
        assertNull(PolicyRouteCache.lookup("wifi:home", "group-a"))
    }

    @Test
    fun batchResultMustMatchExactNetworkInstance() {
        PolicyRouteCache.setCurrentNetwork("wifi:home", 100L)

        assertFalse(PolicyRouteCache.rememberObserved("wifi:home", 99L, "group-a", "proxy-old"))
        assertFalse(PolicyRouteCache.rememberObserved("cellular:310260", 100L, "group-a", "proxy-old"))
        assertTrue(PolicyRouteCache.rememberObserved("wifi:home", 100L, "group-a", "proxy-current"))
        assertEquals("proxy-current", PolicyRouteCache.lookup("wifi:home", "group-a"))
    }
}
