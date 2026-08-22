package com.v2ray.ang.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TetheringPlatformCompatTest {

    @Test
    fun infersKnownLegacyTetheringInterfaces() {
        assertEquals(
            ShizukuTetheringService.TETHERING_TYPE_WIFI,
            TetheringPlatformCompat.inferLegacyTetheringType("wlan0"),
        )
        assertEquals(
            ShizukuTetheringService.TETHERING_TYPE_WIFI,
            TetheringPlatformCompat.inferLegacyTetheringType("softap1"),
        )
        assertEquals(
            ShizukuTetheringService.TETHERING_TYPE_USB,
            TetheringPlatformCompat.inferLegacyTetheringType("rndis0"),
        )
        assertEquals(2, TetheringPlatformCompat.inferLegacyTetheringType("bt-pan"))
        assertEquals(3, TetheringPlatformCompat.inferLegacyTetheringType("p2p-wlan0-0"))
        assertEquals(4, TetheringPlatformCompat.inferLegacyTetheringType("ncm0"))
        assertEquals(5, TetheringPlatformCompat.inferLegacyTetheringType("eth0"))
        assertNull(TetheringPlatformCompat.inferLegacyTetheringType("vendor0"))
    }

    @Test
    fun rejectsUnknownActiveLegacyInterface() {
        val error = assertThrows(IllegalStateException::class.java) {
            TetheringPlatformCompat.requireLegacyTetheringType("vendor0", emptyMap())
        }
        assertEquals("Unknown active tethering interface: vendor0", error.message)
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
            true,
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
