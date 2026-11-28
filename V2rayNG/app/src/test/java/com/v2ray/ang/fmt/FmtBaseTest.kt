package com.v2ray.ang.fmt

import com.v2ray.ang.enums.NetworkType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.net.URI

class FmtBaseTest {

    private val fmt = object : FmtBase() {}

    @Test
    fun queryValueKeepsUnencodedEqualsSigns() {
        val params = fmt.getQueryParam(
            URI("vless://id@example.com:443?type=ws&path=/ws?ed=2048&ech=AEX+/AEE==&security=tls")
        )

        assertEquals("/ws?ed=2048", params["path"])
        assertEquals("AEX+/AEE==", params["ech"])
        assertEquals("tls", params["security"])
    }

    @Test
    fun pairsWithoutValueAreSkipped() {
        val params = fmt.getQueryParam(URI("vless://id@example.com:443?security=tls&&flag&type=ws&"))

        assertEquals(mapOf("security" to "tls", "type" to "ws"), params)
        assertFalse(params.containsKey("flag"))
    }

    @Test
    fun vlessLinkWithTrailingAmpersandIsParsed() {
        val profile = VlessFmt.parse("vless://id@example.com:443?security=tls&type=ws&path=%2Fws&#name")

        assertNotNull(profile)
        assertEquals("tls", profile!!.security)
        assertEquals(NetworkType.WS.type, profile.network)
        assertEquals("/ws", profile.path)
        assertEquals("name", profile.remarks)
    }

    @Test
    fun tcpHttpPathIsKeptWhenSharingAVlessLink() {
        val profile = VlessFmt.parse(
            "vless://id@example.com:443?type=tcp&headerType=http&host=cdn.example.com&path=%2Fcamouflage#camouflage"
        )
        assertNotNull(profile)
        assertEquals(NetworkType.TCP.type, profile!!.network)
        assertEquals("http", profile.headerType)
        assertEquals("/camouflage", profile.path)

        val shared = "vless://${VlessFmt.toUri(profile)}"
        val reparsed = VlessFmt.parse(shared)

        assertNotNull(reparsed)
        assertEquals("/camouflage", reparsed!!.path, "TCP/HTTP path lost in $shared")
    }

    @Test
    fun tcpHttpPathIsKeptWhenSharingATrojanLink() {
        val profile = TrojanFmt.parse(
            "trojan://pw@example.com:443?type=tcp&headerType=http&host=cdn.example.com&path=%2Fcamouflage#camouflage"
        )
        assertEquals(NetworkType.TCP.type, profile.network)
        assertEquals("http", profile.headerType)
        assertEquals("/camouflage", profile.path)

        val shared = "trojan://${TrojanFmt.toUri(profile)}"
        val reparsed = TrojanFmt.parse(shared)

        assertEquals("/camouflage", reparsed.path, "TCP/HTTP path lost in $shared")
    }
}
