package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.NetworkType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class VlessFmtXdriveTest {

    private fun xdriveProfile() = ProfileItem.create(EConfigType.VLESS).apply {
        remarks = "drive"
        server = "127.0.0.1"
        serverPort = "443"
        password = "27848739-7e62-4138-9fd3-098a63964b6b"
        method = "mlkem768x25519plus.native.0rtt.key"
        network = NetworkType.XDRIVE.type
        path = "folder-id"
        xdriveService = AppConfig.XDRIVE_SERVICE_GOOGLE_DRIVE
        xdriveSecrets = "id,se/cr+et=,1//token"
        xdriveExtra = """{"segmentBytes":262144}"""
    }

    @Test
    fun xdriveFieldsSurviveRoundTrip() {
        val uri = VlessFmt.toUri(xdriveProfile())
        val parsed = VlessFmt.parse("vless://$uri")

        assertNotNull(parsed)
        parsed!!
        assertEquals(NetworkType.XDRIVE.type, parsed.network)
        assertEquals("folder-id", parsed.path)
        assertEquals(AppConfig.XDRIVE_SERVICE_GOOGLE_DRIVE, parsed.xdriveService)
        assertEquals("id,se/cr+et=,1//token", parsed.xdriveSecrets)
        assertEquals("""{"segmentBytes":262144}""", parsed.xdriveExtra)
        assertNull(parsed.xhttpExtra)
        assertEquals("mlkem768x25519plus.native.0rtt.key", parsed.method)
    }

    @Test
    fun extraStaysXhttpForOtherNetworks() {
        val parsed = VlessFmt.parse(
            "vless://27848739-7e62-4138-9fd3-098a63964b6b@127.0.0.1:443?type=xhttp&extra=%7B%7D&service=x&secrets=y#n"
        )

        assertNotNull(parsed)
        assertEquals("{}", parsed!!.xhttpExtra)
        assertNull(parsed.xdriveExtra)
        assertNull(parsed.xdriveService)
        assertNull(parsed.xdriveSecrets)
    }
}
