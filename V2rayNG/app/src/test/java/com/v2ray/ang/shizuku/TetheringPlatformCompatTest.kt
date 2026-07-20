package com.v2ray.ang.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertNull(TetheringPlatformCompat.inferLegacyTetheringType("eth0"))
    }

    @Test
    fun buildsOnlyValidTetheringTypeBits() {
        assertEquals(1, tetheringTypeBit(0))
        assertEquals(1 shl 15, tetheringTypeBit(15))
        assertEquals(0, tetheringTypeBit(-1))
        assertEquals(0, tetheringTypeBit(31))
    }

    @Test
    fun parsesTheCurrentTetheringUpstream() {
        assertEquals(
            "testtun17",
            TetheringPlatformCompat.parseUpstreamInterfaceName(
                "    Current upstream interface(s): [testtun17]",
            ),
        )
        assertEquals(
            "eth0, rmnet0",
            TetheringPlatformCompat.parseUpstreamInterfaceName(
                "Current upstream interface(s): [eth0, rmnet0]",
            ),
        )
        assertEquals(
            "",
            TetheringPlatformCompat.parseUpstreamInterfaceName(
                "    Current upstream interface(s): null",
            ),
        )
        assertNull(TetheringPlatformCompat.parseUpstreamInterfaceName("Upstream wanted: true"))
    }
}
