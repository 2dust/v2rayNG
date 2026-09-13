package com.v2ray.ang.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class MainGroupTabTest {
    @Test
    fun fullyVisibleTabDoesNotScroll() {
        assertEquals(0, groupTabScrollDistance(0, 100, -16, 284))
        assertEquals(0, groupTabScrollDistance(-16, 300, -16, 284))
    }

    @Test
    fun clippedTabScrollsOnlyAsFarAsNecessaryAtEitherEdge() {
        assertEquals(-24, groupTabScrollDistance(-40, 100, -16, 284))
        assertEquals(46, groupTabScrollDistance(230, 100, -16, 284))
    }

    @Test
    fun oversizedTabShowsItsStart() {
        assertEquals(56, groupTabScrollDistance(40, 400, -16, 284))
        assertEquals(-24, groupTabScrollDistance(-40, 400, -16, 284))
        assertEquals(0, groupTabScrollDistance(-16, 400, -16, 284))
    }
}
