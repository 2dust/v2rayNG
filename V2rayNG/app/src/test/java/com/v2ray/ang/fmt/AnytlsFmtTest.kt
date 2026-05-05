package com.v2ray.ang.fmt

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnytlsFmtTest {
    @Test
    fun test_parseNodeUri_generatesCustomSingBoxProfile() {
        val uri = "anytls://fbf033ed-6818-4640-af09-12783be9db45@a99.zongse001.top:43399/?insecure=1&sni=osxapps.itunes.apple.com#A3%7C%E7%BE%8E%E5%9B%BD-%E5%8E%9F%E7%94%9FIP-US99%7C2X"

        val result = AnytlsFmt.parse(uri)

        assertNotNull(result)
        val parsed = result!!
        assertEquals("[sing-box] A3|美国-原生IP-US99|2X", parsed.profile.remarks)
        assertEquals("a99.zongse001.top", parsed.profile.server)
        assertEquals("43399", parsed.profile.serverPort)
        assertEquals("osxapps.itunes.apple.com", parsed.profile.sni)
        assertTrue(parsed.profile.insecure == true)

        val rawJson = JsonParser.parseString(parsed.rawConfig).asJsonObject
        val outbound = rawJson.getAsJsonArray("outbounds")[0].asJsonObject
        val tls = outbound.getAsJsonObject("tls")
        assertEquals("anytls", outbound.get("type").asString)
        assertEquals("fbf033ed-6818-4640-af09-12783be9db45", outbound.get("password").asString)
        assertEquals("osxapps.itunes.apple.com", tls.get("server_name").asString)
        assertTrue(tls.get("insecure").asBoolean)
    }

    @Test
    fun test_parseInfoUri_skipsSubscriptionMetadata() {
        val uri = "anytls://fbf033ed-6818-4640-af09-12783be9db45@a99.zongse001.top:43399/?insecure=1&sni=osxapps.itunes.apple.com#%E5%89%A9%E4%BD%99%E6%B5%81%E9%87%8F%EF%BC%9A992.77%20GB"

        val result = AnytlsFmt.parse(uri)

        assertNull(result)
    }

    @Test
    fun test_parseWithFingerprintAndAlpn_preservesTlsOptions() {
        val uri = "anytls://password@example.com:443/?insecure=0&sni=cdn.example.com&alpn=h2,http%2F1.1&fp=chrome#demo"

        val result = AnytlsFmt.parse(uri)

        assertNotNull(result)
        val rawJson = JsonParser.parseString(result!!.rawConfig).asJsonObject
        val tls = rawJson.getAsJsonArray("outbounds")[0].asJsonObject.getAsJsonObject("tls")
        val alpn = tls.getAsJsonArray("alpn")
        assertFalse(tls.get("insecure").asBoolean)
        assertEquals("chrome", tls.getAsJsonObject("utls").get("fingerprint").asString)
        assertEquals("h2", alpn[0].asString)
        assertEquals("http/1.1", alpn[1].asString)
    }
}
