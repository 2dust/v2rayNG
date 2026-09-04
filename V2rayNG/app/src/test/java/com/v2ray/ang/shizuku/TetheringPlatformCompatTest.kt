package com.v2ray.ang.shizuku

import org.junit.Assert.assertEquals
import org.junit.Test

class TetheringPlatformCompatTest {

    @Test
    fun usesPublicTetheringApiStartingAtApi36() {
        assertEquals(false, isPublicTetheringApiLevel(33))
        assertEquals(false, isPublicTetheringApiLevel(35))
        assertEquals(true, isPublicTetheringApiLevel(36))
        assertEquals(true, isPublicTetheringApiLevel(37))
    }

    @Test
    fun buildsOnlyValidTetheringTypeBits() {
        assertEquals(1, tetheringTypeBit(0))
        assertEquals(1 shl 15, tetheringTypeBit(15))
        assertEquals(0, tetheringTypeBit(-1))
        assertEquals(0, tetheringTypeBit(31))
    }

    @Test
    fun acceptsOnlyTheOwnedUpstreamInterface() {
        assertEquals(true, TetheringPlatformCompat.isProtectedUpstream("testtun17", "testtun17"))
        assertEquals(
            false,
            TetheringPlatformCompat.isProtectedUpstream("testtun17, testtun17", "testtun17"),
        )
        assertEquals(false, TetheringPlatformCompat.isProtectedUpstream("", "testtun17"))
        assertEquals(false, TetheringPlatformCompat.isProtectedUpstream("eth0", "testtun17"))
        assertEquals(
            false,
            TetheringPlatformCompat.isProtectedUpstream("testtun17, eth0", "testtun17"),
        )
    }
}
