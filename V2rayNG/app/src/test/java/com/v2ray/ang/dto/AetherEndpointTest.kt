package com.v2ray.ang.dto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AetherEndpointTest {

    @Test
    fun anIpv4AddressAndPortFormAnEndpoint() {
        assertEquals("162.159.192.1:2408", AetherEndpoint.of("162.159.192.1", "2408").toString())
        assertEquals("162.159.192.1:2408", AetherEndpoint.of(" 162.159.192.1 ", " 2408 ").toString())
    }

    @Test
    fun anIpv6AddressIsBracketedWhenWrittenOut() {
        val endpoint = AetherEndpoint.of("2606:4700:d0::a29f:c001", "443")
        assertEquals("2606:4700:d0::a29f:c001", endpoint?.host)
        assertEquals("[2606:4700:d0::a29f:c001]:443", endpoint.toString())
        assertEquals(endpoint, AetherEndpoint.of("[2606:4700:d0::a29f:c001]", "443"))
    }

    @Test
    fun anIpv6AddressIsWrittenInItsCanonicalForm() {
        assertEquals("2606:4700::1", AetherEndpoint.of("2606:4700:0:0:0:0:0:1", "443")?.host)
        assertEquals("2606:4700::1", AetherEndpoint.of("2606:4700:0000::0001", "443")?.host)
        assertEquals("2606:4700::abcd", AetherEndpoint.of("2606:4700::ABCD", "443")?.host)
        assertEquals("1:0:0:2::3", AetherEndpoint.of("1:0:0:2:0:0:0:3", "443")?.host)
        assertEquals("1::2:0:0:3:4", AetherEndpoint.of("1:0:0:2:0:0:3:4", "443")?.host)
        assertEquals("1:0:2:3:4:5:6:7", AetherEndpoint.of("1:0:2:3:4:5:6:7", "443")?.host)
        assertEquals("::", AetherEndpoint.of("::", "443")?.host)
        assertEquals("::ffff:a29f:c001", AetherEndpoint.of("::ffff:162.159.192.1", "443")?.host)
    }

    @Test
    fun digitsFromOtherScriptsAreReadAsAsciiDigits() {
        assertEquals("162.159.192.1:2408", AetherEndpoint.of("۱۶۲.۱۵۹.۱۹۲.۱", "۲۴۰۸").toString())
    }

    @Test
    fun onlyAnIpLiteralIsAnAddress() {
        assertNull(AetherEndpoint.of("engage.cloudflareclient.com", "2408"))
        assertNull(AetherEndpoint.of("162.159.192", "2408"))
        assertNull(AetherEndpoint.of("162.159.192.256", "2408"))
        assertNull(AetherEndpoint.of("162.159.192.01", "2408"))
        assertNull(AetherEndpoint.of("1::2::3", "2408"))
        assertNull(AetherEndpoint.of("12345::1", "2408"))
        assertNull(AetherEndpoint.of("1:2:3:4:5:6:7:8:9", "2408"))
        assertNull(AetherEndpoint.of("1:2:3:4:5:6:7", "2408"))
        assertNull(AetherEndpoint.of("fe80::1%wlan0", "2408"))
        assertNull(AetherEndpoint.of("162.159.192.1::1", "2408"))
        assertNull(AetherEndpoint.of("", "2408"))
        assertNull(AetherEndpoint.of(null, "2408"))
    }

    @Test
    fun thePortMustBeAUsablePortNumber() {
        assertNull(AetherEndpoint.of("162.159.192.1", "0"))
        assertNull(AetherEndpoint.of("162.159.192.1", "65536"))
        assertNull(AetherEndpoint.of("162.159.192.1", "+443"))
        assertNull(AetherEndpoint.of("162.159.192.1", "http"))
        assertNull(AetherEndpoint.of("162.159.192.1", ""))
        assertNull(AetherEndpoint.of("162.159.192.1", null))
        assertEquals(65535, AetherEndpoint.of("162.159.192.1", "65535")?.port)
    }

    @Test
    fun anEndpointIsReadFromTheWayTheCoreWritesIt() {
        assertEquals(AetherEndpoint("162.159.192.1", 2408), AetherEndpoint.parse("162.159.192.1:2408"))
        assertEquals(AetherEndpoint("2606:4700::1", 2408), AetherEndpoint.parse("[2606:4700::1]:2408"))
        assertEquals(AetherEndpoint("162.159.192.1", 2408), AetherEndpoint.parse(" 162.159.192.1:2408 "))
    }

    @Test
    fun anEndpointWithoutItsPortOrBracketsIsRefused() {
        assertNull(AetherEndpoint.parse("162.159.192.1"))
        assertNull(AetherEndpoint.parse("162.159.192.1:"))
        assertNull(AetherEndpoint.parse(":2408"))
        assertNull(AetherEndpoint.parse("2606:4700::1:2408"))
        assertNull(AetherEndpoint.parse("[2606:4700::1]"))
        assertNull(AetherEndpoint.parse(""))
        assertNull(AetherEndpoint.parse(null))
    }
}
