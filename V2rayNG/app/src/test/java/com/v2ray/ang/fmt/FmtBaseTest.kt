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
}
