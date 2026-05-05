package com.v2ray.ang.fmt

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClashYamlFmtTest {
    @Test
    fun test_parseAnytlsProxy_generatesCustomSingBoxProfile() {
        val yaml = """
            proxies:
              - name: US
                type: anytls
                server: a70.topzz.cc
                port: 42370
                password: 5a101599-a536-4cee-a004-17ee96022cac
                client-fingerprint: chrome
                alpn:
                  - h2
                  - http/1.1
                sni: osxapps.itunes.apple.com
                skip-cert-verify: true
        """.trimIndent()

        val result = ClashYamlFmt.parse(yaml)

        assertEquals(1, result.size)
        val parsed = result.first()
        assertEquals("[sing-box] US", parsed.profile.remarks)
        assertEquals("a70.topzz.cc", parsed.profile.server)
        assertEquals("42370", parsed.profile.serverPort)
        assertEquals("chrome", parsed.profile.fingerPrint)
        assertTrue(parsed.profile.insecure == true)

        val rawJson = JsonParser.parseString(parsed.rawConfig).asJsonObject
        val outbound = rawJson.getAsJsonArray("outbounds")[0].asJsonObject
        val tls = outbound.getAsJsonObject("tls")
        assertEquals("anytls", outbound.get("type").asString)
        assertEquals("a70.topzz.cc", outbound.get("server").asString)
        assertEquals(42370, outbound.get("server_port").asInt)
        assertEquals("osxapps.itunes.apple.com", tls.get("server_name").asString)
        assertTrue(tls.get("insecure").asBoolean)
        assertEquals("chrome", tls.getAsJsonObject("utls").get("fingerprint").asString)
    }

    @Test
    fun test_parseVmessWsTlsProxy_preservesTransportAndTls() {
        val yaml = """
            proxies:
              - name: VMess WS
                type: vmess
                server: vmess.example.com
                port: 443
                uuid: aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee
                alterId: 0
                cipher: auto
                tls: true
                servername: cdn.example.com
                client-fingerprint: chrome
                network: ws
                ws-opts:
                  path: /ws
                  headers:
                    Host: edge.example.com
        """.trimIndent()

        val result = ClashYamlFmt.parse(yaml)

        assertEquals(1, result.size)
        val rawJson = JsonParser.parseString(result.first().rawConfig).asJsonObject
        val outbound = rawJson.getAsJsonArray("outbounds")[0].asJsonObject
        val tls = outbound.getAsJsonObject("tls")
        val transport = outbound.getAsJsonObject("transport")

        assertEquals("vmess", outbound.get("type").asString)
        assertEquals("auto", outbound.get("security").asString)
        assertEquals(0, outbound.get("alter_id").asInt)
        assertEquals("cdn.example.com", tls.get("server_name").asString)
        assertEquals("ws", transport.get("type").asString)
        assertEquals("/ws", transport.get("path").asString)
        assertEquals("edge.example.com", transport.getAsJsonObject("headers").get("Host").asString)
    }

    @Test
    fun test_isClashYaml_onlyMatchesProxyDocuments() {
        assertTrue(
            ClashYamlFmt.isClashYaml(
                """
                port: 7890
                proxies:
                  - name: demo
                    type: http
                    server: example.com
                    port: 80
                """.trimIndent()
            )
        )
        assertFalse(ClashYamlFmt.isClashYaml("""{"outbounds": []}"""))
        assertFalse(ClashYamlFmt.isClashYaml("vmess://example"))
        assertTrue(ClashYamlFmt.parse("proxies: []").isEmpty())
    }
}
