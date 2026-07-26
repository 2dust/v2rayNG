package com.v2ray.ang

import com.v2ray.ang.util.Utils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class UtilsTest {

    @Test
    fun test_parseInt() {
        assertEquals(Utils.parseInt("1234"), 1234)
    }

    @Test
    fun test_isIpAddress() {
        assertFalse(Utils.isIpAddress("114.113.112.266"))
        assertFalse(Utils.isIpAddress("666.666.666.666"))
        assertFalse(Utils.isIpAddress("256.0.0.0"))
        assertFalse(Utils.isIpAddress("::ffff:127.0.0.0.1"))
        assertFalse(Utils.isIpAddress("baidu.com"))
        assertFalse(Utils.isIpAddress(""))

        assertTrue(Utils.isIpAddress("127.0.0.1"))
        assertTrue(Utils.isIpAddress("127.0.0.1:80"))
        assertTrue(Utils.isIpAddress("0.0.0.0/0"))
        assertTrue(Utils.isIpAddress("::1"))
        assertTrue(Utils.isIpAddress("[::1]:80"))
        assertTrue(Utils.isIpAddress("2605:2700:0:3::4713:93e3"))
        assertTrue(Utils.isIpAddress("[2605:2700:0:3::4713:93e3]:80"))
        assertTrue(Utils.isIpAddress("::ffff:192.168.173.22"))
        assertTrue(Utils.isIpAddress("[::ffff:192.168.173.22]:80"))
        assertTrue(Utils.isIpAddress("1::"))
        assertTrue(Utils.isIpAddress("::"))
        assertTrue(Utils.isIpAddress("::/0"))
        assertTrue(Utils.isIpAddress("10.24.56.0/24"))
        assertTrue(Utils.isIpAddress("2001:4321::1"))
        assertTrue(Utils.isIpAddress("240e:1234:abcd:12::6666"))
        assertTrue(Utils.isIpAddress("240e:1234:abcd:12::/64"))
    }

    @Test
    fun test_IsIpInCidr() {
        assertTrue(Utils.isIpInCidr("192.168.1.1", "192.168.1.0/24"))
        assertTrue(Utils.isIpInCidr("192.168.1.254", "192.168.1.0/24"))
        assertFalse(Utils.isIpInCidr("192.168.2.1", "192.168.1.0/24"))

        assertTrue(Utils.isIpInCidr("10.0.0.0", "10.0.0.0/8"))
        assertTrue(Utils.isIpInCidr("10.255.255.255", "10.0.0.0/8"))
        assertFalse(Utils.isIpInCidr("11.0.0.0", "10.0.0.0/8"))

        assertFalse(Utils.isIpInCidr("invalid-ip", "192.168.1.0/24"))
        assertFalse(Utils.isIpInCidr("192.168.1.1", "invalid-cidr"))
    }

    @Test
    fun test_fixIllegalUrl_noFragment_byteIdentical() {
        assertEquals("http://1.2.3.4/path", Utils.fixIllegalUrl("http://1.2.3.4/path"))
        assertEquals("https://example.com/to/abc", Utils.fixIllegalUrl("https://example.com/to/abc"))
        assertEquals("vless://uuid@1.2.3.4:443?enc=none", Utils.fixIllegalUrl("vless://uuid@1.2.3.4:443?enc=none"))
        assertEquals("vless://uuid@1.2.3.4:443?enc=a%20b", Utils.fixIllegalUrl("vless://uuid@1.2.3.4:443?enc=a b"))
    }

    @Test
    fun test_fixIllegalUrl_innerHashRemark_makesUriParseable() {
        val raw =
            "vless://11111111-2222-3333-4444-555555555555@10.0.0.1:443" +
                "?encryption=none&type=grpc&security=reality&sni=example.com&fp=qq" +
                "&pbk=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA0&sid=1111111111111111" +
                "&serviceName=grpc#\uD83C\uDDF7\uD83C\uDDFA \u0411\u0435\u043B\u044B\u0439 \u0438\u043D\u0442\u0435\u0440\u043D\u0435\u0442 #1 | \u0412\u0441\u0435 \u043E\u043F\u0435\u0440\u0430\u0442\u043E\u0440\u044B"
        val fixed = Utils.fixIllegalUrl(raw)
        val uri = URI(fixed)
        assertEquals("vless", uri.scheme)
        assertEquals("10.0.0.1", uri.host)
        assertEquals(443, uri.port)
        val decoded = java.net.URLDecoder.decode(uri.rawFragment ?: "", "UTF-8")
        assertEquals("\uD83C\uDDF7\uD83C\uDDFA \u0411\u0435\u043B\u044B\u0439 \u0438\u043D\u0442\u0435\u0440\u043D\u0435\u0442 #1 | \u0412\u0441\u0435 \u043E\u043F\u0435\u0440\u0430\u0442\u043E\u0440\u044B", decoded)
    }

    @Test
    fun test_fixIllegalUrl_alreadyEncodedIdempotent() {
        val pre = "vless://uuid@host:443?encryption=none#%D0%91%D0%B5%D0%BB%D1%8B%D0%B9%20Plain%20%231"
        assertEquals(pre, Utils.fixIllegalUrl(pre))
        val realRaw =
            "hysteria2://11111111-2222-3333-4444-555555555555@10.0.0.2:443?sni=example.com&fp=firefox&alpn=h3" +
                "#\uD83C\uDDF7\uD83C\uDDFA \u041F\u0438\u043D\u0433 #7 | \u041E\u043F\u0435\u0440\u0430\u0442\u043E\u0440"
        val once = Utils.fixIllegalUrl(realRaw)
        val twice = Utils.fixIllegalUrl(once)
        assertEquals(once, twice)
    }

    @Test
    fun test_fixIllegalUrl_barePercentInRemark_escapedToPct25() {
        val raw = "vless://uuid@1.2.3.4:443?enc=none#Save 50% off"
        val uri = URI(Utils.fixIllegalUrl(raw))
        assertEquals("Save 50% off", java.net.URLDecoder.decode(uri.rawFragment ?: "", "UTF-8"))
    }

    @Test
    fun test_fixIllegalUrl_legacySpacePipeEscapePreserved() {
        assertEquals("ss://a%20b@1.2.3.4:c%7Cd", Utils.fixIllegalUrl("ss://a b@1.2.3.4:c|d"))
    }

    @Test
    fun test_splitConfigEntries_newlineJoined_identicalToLines() {
        val blob = "vless://uuid@h1:443#a\nvmess://uuid@h2:443#b\nss://pass@h3:c#t"
        assertEquals(listOf("vless://uuid@h1:443#a", "vmess://uuid@h2:443#b", "ss://pass@h3:c#t"), Utils.splitConfigEntries(blob))
    }

    @Test
    fun test_splitConfigEntries_spaceJoined_splitsAllSchemes() {
        val blob = buildString {
            append("vless://uuid@1.2.3.4:443?a=1#rv1")
            append(" vmess://uuid@5.6.7.8:443#nl2")
            append(" trojan://u:p@9.10.11.12:443#us3")
            append(" hysteria2://u@13.14.15.16:443?sni=x#de4")
            append(" hy2://u@17.18.19.20:443#fr5")
            append(" ss://c:D2@21.22.23.24:8388#jp6")
            append(" socks4://p@25.26.27.28:1080#uk7")
            append(" socks5://p@29.30.31.32:1080#ch8")
            append(" socks://p@41.42.43.44:1080#bare10")
            append(" wireguard://u@33.34.35.36:51820?pk=Q#se9")
            append(" v2rayn://xyz@37.38.39.40:443#custom")
        }
        val out = Utils.splitConfigEntries(blob)
        assertEquals(11, out.size)
        assertTrue(out[0].startsWith("vless://"))
        assertTrue(out[3].startsWith("hysteria2://"))
        assertTrue(out[4].startsWith("hy2://"))
        assertTrue(out[7].startsWith("socks5://"))
        assertTrue(out[8].startsWith("socks://"))
        assertTrue(out[10].startsWith("v2rayn://"))
    }

    @Test
    fun test_splitConfigEntries_httpNotSplit_subscriptionUrlsIntact() {
        val subBlob = "https://sub.example.com/list1 http://sub.example.com/list2"
        val out = Utils.splitConfigEntries(subBlob)
        assertEquals(1, out.size)
        assertEquals(subBlob, out[0])
    }

    @Test
    fun test_splitConfigEntries_blankTrimmed_distinctFiltered() {
        val blob = "  vless://a@1.2.3.4:443#x\n\n  \n vless://a@1.2.3.4:443#x  \nss://b@c:1#y"
        assertEquals(listOf("vless://a@1.2.3.4:443#x", "ss://b@c:1#y"), Utils.splitConfigEntries(blob))
    }

    @Test
    fun test_splitConfigEntries_interspersedCommentLine_keepsConfigsSeparate() {
        val blob = "vless://uuid@h1:443?enc=none\n# provider note\nvmess://uuid@h2:443?enc=none#b"
        assertEquals(
            listOf("vless://uuid@h1:443?enc=none", "# provider note", "vmess://uuid@h2:443?enc=none#b"),
            Utils.splitConfigEntries(blob)
        )
        URI(Utils.fixIllegalUrl("vless://uuid@h1:443?enc=none"))
    }
}