package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class SubscriptionInfoParserTest {
    private val decode: (String) -> String = { String(Base64.getDecoder().decode(it), Charsets.UTF_8) }

    private fun parse(
        userInfo: String? = null,
        title: String? = null,
        support: String? = null,
        web: String? = null,
    ) = SubscriptionInfoParser.parse(userInfo, title, support, web, decode)

    @Test
    fun noHeadersGivesNull() {
        assertNull(parse())
        assertNull(parse(userInfo = "  ", title = "", support = "ftp://x", web = "javascript:alert(1)"))
    }

    @Test
    fun parsesStandardUserInfo() {
        val info = parse(userInfo = "upload=100; download=200; total=1000; expire=1893456000")!!
        assertEquals(100, info.upload)
        assertEquals(200, info.download)
        assertEquals(300, info.used)
        assertEquals(1000, info.total)
        assertEquals(1893456000, info.expire)
    }

    @Test
    fun toleratesCaseSpacesFloatsAndUnknownKeys() {
        val info = parse(userInfo = "Upload=1.5e3;DOWNLOAD = 10 ;foo=bar;total=-5;expire=")!!
        assertEquals(1500, info.upload)
        assertEquals(10, info.download)
        assertEquals(0, info.total)
        assertEquals(0, info.expire)
    }

    @Test
    fun expireInMillisecondsIsNormalizedToSeconds() {
        assertEquals(1893456000, parse(userInfo = "expire=1893456000000")!!.expire)
    }

    @Test
    fun garbageUserInfoWithoutOtherHeadersGivesNull() {
        assertNull(parse(userInfo = "hello world"))
    }

    @Test
    fun decodesBase64PlainAndPercentEncodedTitles() {
        val b64 = Base64.getEncoder().encodeToString("اشتراک من".toByteArray())
        assertEquals("اشتراک من", parse(title = "base64:$b64")!!.title)
        assertEquals("My Plan", parse(title = "My Plan")!!.title)
        assertEquals("My Plan", parse(title = "My%20Plan")!!.title)
    }

    @Test
    fun onlyHttpHttpsAndTgLinksAreAccepted() {
        assertEquals("https://t.me/support", parse(support = " https://t.me/support ")!!.supportUrl)
        assertEquals("tg://resolve?domain=x", parse(support = "tg://resolve?domain=x")!!.supportUrl)
        assertNull(SubscriptionInfoParser.sanitizeLink("javascript:alert(1)"))
        assertNull(SubscriptionInfoParser.sanitizeLink("intent://x#Intent;end"))
        assertNull(SubscriptionInfoParser.sanitizeLink("file:///etc/passwd"))
        assertNull(SubscriptionInfoParser.sanitizeLink("https://"))
        assertNull(SubscriptionInfoParser.sanitizeLink(null))
    }

    @Test
    fun detectsTelegramLinks() {
        assertTrue(SubscriptionInfoParser.isTelegramLink("https://t.me/abc"))
        assertTrue(SubscriptionInfoParser.isTelegramLink("tg://resolve?domain=abc"))
        assertFalse(SubscriptionInfoParser.isTelegramLink("https://example.com/t.me"))
        assertFalse(SubscriptionInfoParser.isTelegramLink(null))
    }

    @Test
    fun webPageFallsBackToSubscriptionUrl() {
        val base = SubscriptionUsageInfo(total = 1)
        assertEquals("https://p.example/sub/abc", SubscriptionInfoParser.withWebPageFallback(base, "https://p.example/sub/abc").webPageUrl)
        val own = base.copy(webPageUrl = "https://p.example/info")
        assertEquals("https://p.example/info", SubscriptionInfoParser.withWebPageFallback(own, "https://p.example/sub/abc").webPageUrl)
        assertNull(SubscriptionInfoParser.withWebPageFallback(base, "not a url").webPageUrl)
    }
}
