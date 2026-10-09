package com.v2ray.ang.ui.xfinder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class XfinderParserTest {
    private val vless =
        "vless://e5cc16a6-ea42-46b2-82ae-ad2157e1641b@198.41.197.63:8080?security=false&type=ws#Xfinder%20%E2%80%A2%20VLESS%20%E2%80%A2%2049ms%20%E2%80%A2%20001"

    @Test
    fun emptyAndBlankInputGiveNoConfigs() {
        assertTrue(XfinderParser.parse(null).isEmpty())
        assertTrue(XfinderParser.parse("").isEmpty())
        assertTrue(XfinderParser.parse("  \n\n  ").isEmpty())
    }

    @Test
    fun parsesVlessNameServerPortAndPing() {
        val c = XfinderParser.parseLine(vless)!!
        assertEquals("VLESS", c.protocol)
        assertEquals("Xfinder • VLESS • 49ms • 001", c.name)
        assertEquals("198.41.197.63", c.server)
        assertEquals("8080", c.port)
        assertEquals(49, c.pingMs)
        assertEquals(vless, c.raw)
    }

    @Test
    fun encodedHashInsideQueryDoesNotBecomeTheName() {
        val line = "vless://id@162.159.130.234:443?path=%2Ffdip%3Dx%23Telegram&type=ws#Xfinder%20%E2%80%A2%20VLESS%20%E2%80%A2%2050ms%20%E2%80%A2%20113"
        val c = XfinderParser.parseLine(line)!!
        assertEquals("Xfinder • VLESS • 50ms • 113", c.name)
        assertEquals(50, c.pingMs)
        assertEquals("162.159.130.234", c.server)
    }

    @Test
    fun parsesTrojanAndShadowsocksUserInfoAuthority() {
        val trojan = XfinderParser.parseLine("trojan://humanity@141.193.213.172:443?security=tls#Xfinder%20%E2%80%A2%20TROJAN%20%E2%80%A2%20429ms%20%E2%80%A2%20125")!!
        assertEquals("Trojan", trojan.protocol)
        assertEquals("141.193.213.172", trojan.server)
        assertEquals("443", trojan.port)
        assertEquals(429, trojan.pingMs)

        val ss = XfinderParser.parseLine("ss://YWVzLTEyOC1nY206cGFzcw@66.23.204.214:16999#Xfinder%20%E2%80%A2%20SS%20%E2%80%A2%20247ms%20%E2%80%A2%20122")!!
        assertEquals("SS", ss.protocol)
        assertEquals("66.23.204.214", ss.server)
        assertEquals("16999", ss.port)
        assertEquals(247, ss.pingMs)
    }

    @Test
    fun parsesLegacyBase64ShadowsocksAuthority() {
        val body = Base64.getEncoder().encodeToString("aes-128-gcm:pass@10.0.0.9:8388".toByteArray())
        val c = XfinderParser.parseLine("ss://$body#Name%20120ms")!!
        assertEquals("10.0.0.9", c.server)
        assertEquals("8388", c.port)
        assertEquals(120, c.pingMs)
    }

    @Test
    fun vmessNameServerAndPortComeFromTheBase64Json() {
        val json = """{"v":"2","ps":"Xfinder • VMESS • 573ms • 133","add":"us16-4.example.best","port":"443","id":"x"}"""
        val line = "vmess://" + Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
        val c = XfinderParser.parseLine(line)!!
        assertEquals("VMess", c.protocol)
        assertEquals("Xfinder • VMESS • 573ms • 133", c.name)
        assertEquals("us16-4.example.best", c.server)
        assertEquals("443", c.port)
        assertEquals(573, c.pingMs)
    }

    @Test
    fun vmessWithNumericPortAndBrokenPayloadStayUsable() {
        val json = """{"ps":"A 90ms","add":"h.example","port":8443}"""
        val ok = XfinderParser.parseLine("vmess://" + Base64.getEncoder().encodeToString(json.toByteArray()))!!
        assertEquals("8443", ok.port)

        val broken = XfinderParser.parseLine("vmess://!!!not-base64!!!#Fallback%2077ms")!!
        assertEquals("Fallback 77ms", broken.name)
        assertEquals(77, broken.pingMs)
        assertEquals("", broken.server)
    }

    @Test
    fun ipv6HostKeepsBracketsOutAndSplitsPort() {
        val c = XfinderParser.parseLine("trojan://pw@[2606:4700::1]:443?x=1#n")!!
        assertEquals("2606:4700::1", c.server)
        assertEquals("443", c.port)
    }

    @Test
    fun nameWithoutPingHasNullPing() {
        val c = XfinderParser.parseLine("vless://id@1.2.3.4:443#Plain")!!
        assertNull(c.pingMs)
    }

    @Test
    fun parseSkipsNonLinksAndDuplicates() {
        val list = XfinderParser.parse("garbage line\n$vless\n$vless\n\n")
        assertEquals(1, list.size)
    }

    @Test
    fun pingBucketsFollowTheColourRule() {
        assertEquals(PingLevel.NONE, pingLevelOf(null))
        assertEquals(PingLevel.GOOD, pingLevelOf(0))
        assertEquals(PingLevel.GOOD, pingLevelOf(499))
        assertEquals(PingLevel.MEDIUM, pingLevelOf(500))
        assertEquals(PingLevel.MEDIUM, pingLevelOf(1000))
        assertEquals(PingLevel.BAD, pingLevelOf(1001))
    }
}
