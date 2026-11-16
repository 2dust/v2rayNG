package com.v2ray.ang.fmt

import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.NetworkType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.URI

class XdriveFmtTest {

    private val link = "vless://11111111-2222-3333-4444-555555555555@drive.example:443" +
        "?encryption=mlkem768x25519plus.native.0rtt.key" +
        "&type=xdrive&service=s3&path=%2Ftunnel&secrets=AK%2CSK" +
        "&extra=%7B%22s3%22%3A%7B%22bucket%22%3A%22b%22%7D%7D&security=tls&sni=s3.example#xd"

    @Test
    fun parsesXdriveLink() {
        val profile = requireNotNull(VlessFmt.parse(link))

        assertEquals(EConfigType.VLESS, profile.configType)
        assertEquals(NetworkType.XDRIVE.type, profile.network)
        assertEquals("mlkem768x25519plus.native.0rtt.key", profile.method)
        assertEquals("s3", profile.xdriveService)
        assertEquals("/tunnel", profile.path)
        assertEquals("AK,SK", profile.xdriveSecrets)
        assertEquals("""{"s3":{"bucket":"b"}}""", profile.xdriveExtra)
        assertNull(profile.xhttpExtra)
        assertEquals("tls", profile.security)
    }

    @Test
    fun xdriveLinkWithoutOptionalParametersLeavesFieldsEmpty() {
        val profile = requireNotNull(VlessFmt.parse("vless://id@drive.example:443?encryption=x&type=xdrive#xd"))

        assertEquals(NetworkType.XDRIVE.type, profile.network)
        assertNull(profile.xdriveService)
        assertNull(profile.xdriveSecrets)
        assertNull(profile.xdriveExtra)
        assertNull(profile.path)
    }

    @Test
    fun nonXdriveLinkKeepsExtraOnXhttpOnly() {
        val profile = requireNotNull(
            VlessFmt.parse("vless://id@example.com:443?type=xhttp&extra=%7B%7D&service=s3&secrets=a#x")
        )

        assertEquals("{}", profile.xhttpExtra)
        assertNull(profile.xdriveExtra)
        assertNull(profile.xdriveService)
        assertNull(profile.xdriveSecrets)
    }

    @Test
    fun exportsXdriveParametersAndRoundTrips() {
        val profile = requireNotNull(VlessFmt.parse(link))
        val uri = VlessFmt.toUri(profile)
        val query = object : FmtBase() {}.getQueryParam(URI("vless://$uri"))

        assertEquals("xdrive", query["type"])
        assertEquals("s3", query["service"])
        assertEquals("/tunnel", query["path"])
        assertEquals("AK,SK", query["secrets"])
        assertEquals("""{"s3":{"bucket":"b"}}""", query["extra"])
        assertNull(query["host"])

        val reparsed = requireNotNull(VlessFmt.parse("vless://$uri"))
        assertEquals(profile.copy(addedTime = 0), reparsed.copy(addedTime = 0))
    }
}
