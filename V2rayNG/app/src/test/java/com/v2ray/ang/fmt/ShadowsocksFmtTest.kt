package com.v2ray.ang.fmt

import android.util.Base64
import android.util.Log
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.dto.ProfileItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.mockStatic
import java.net.URLDecoder
import java.util.Base64 as JavaBase64

/**
 * Unit tests for ShadowsocksFmt class.
 *
 * Tests cover:
 * - Parsing SIP002 format URLs (modern format)
 * - Parsing legacy format URLs
 * - Converting ProfileItem to URI
 * - Various encryption methods
 */
class ShadowsocksFmtTest {

    companion object {
        private const val SS_SCHEME = "ss://"
    }

    private lateinit var mockBase64: MockedStatic<Base64>
    private lateinit var mockLog: MockedStatic<Log>

    /**
     * Helper function to create a SIP002 format Shadowsocks URL.
     */
    private fun createSip002Url(
        method: String,
        password: String,
        host: String,
        port: Int,
        remarks: String
    ): String {
        val methodPassword = "$method:$password"
        val base64UserInfo = JavaBase64.getUrlEncoder().withoutPadding()
            .encodeToString(methodPassword.toByteArray())
        return "$SS_SCHEME${base64UserInfo}@$host:$port#${remarks.replace(" ", "%20")}"
    }

    /**
     * Helper function to create a legacy format Shadowsocks URL.
     */
    private fun createLegacyUrl(
        method: String,
        password: String,
        host: String,
        port: Int,
        remarks: String
    ): String {
        val legacyContent = "$method:$password@$host:$port"
        val base64Encoded = JavaBase64.getEncoder().encodeToString(legacyContent.toByteArray())
        return "$SS_SCHEME${base64Encoded}#${remarks.replace(" ", "%20")}"
    }

    @Before
    fun setUp() {
        mockLog = mockStatic(Log::class.java, Mockito.RETURNS_DEFAULTS)

        mockBase64 = mockStatic(Base64::class.java)
        // Mock decode with proper flag handling and exception propagation
        mockBase64.`when`<ByteArray> {
            Base64.decode(Mockito.anyString(), Mockito.anyInt())
        }.thenAnswer { invocation ->
            val input = invocation.arguments[0] as String
            val flags = invocation.arguments[1] as Int
            val isUrlSafe = (flags and Base64.URL_SAFE) != 0

            val decoder = if (isUrlSafe) {
                JavaBase64.getUrlDecoder()
            } else {
                JavaBase64.getDecoder()
            }

            // Propagate exception on invalid input (matches Android behavior)
            decoder.decode(input)
        }
        // Mock encode with proper flag handling
        mockBase64.`when`<String> {
            Base64.encodeToString(Mockito.any(ByteArray::class.java), Mockito.anyInt())
        }.thenAnswer { invocation ->
            val input = invocation.arguments[0] as ByteArray
            val flags = invocation.arguments[1] as Int

            val isUrlSafe = (flags and Base64.URL_SAFE) != 0
            val noPadding = (flags and Base64.NO_PADDING) != 0

            var encoder = if (isUrlSafe) {
                JavaBase64.getUrlEncoder()
            } else {
                JavaBase64.getEncoder()
            }

            if (noPadding) {
                encoder = encoder.withoutPadding()
            }

            encoder.encodeToString(input)
        }
    }

    @After
    fun tearDown() {
        mockLog.close()
        mockBase64.close()
    }

    // ==================== SIP002 Format Tests ====================

    @Test
    fun test_parseSip002_validUrlWithBase64EncodedUserinfo() {
        val ssUrl = createSip002Url(
            method = "aes-256-gcm",
            password = "my-secret-password",
            host = "example.com",
            port = 8388,
            remarks = "Test Server"
        )

        val result = ShadowsocksFmt.parseSip002(ssUrl)

        assertNotNull(result)
        assertEquals("Test Server", result?.remarks)
        assertEquals("example.com", result?.server)
        assertEquals("8388", result?.serverPort)
        assertEquals("aes-256-gcm", result?.method)
        assertEquals("my-secret-password", result?.password)
    }

    @Test
    fun test_parseSip002_validUrlWithPlainTextUserinfo() {
        val ssUrl = "ss://aes-256-gcm:mypassword@example.com:8388#Plain%20Server"

        val result = ShadowsocksFmt.parseSip002(ssUrl)

        assertNotNull(result)
        assertEquals("Plain Server", result?.remarks)
        assertEquals("aes-256-gcm", result?.method)
        assertEquals("mypassword", result?.password)
    }

    @Test
    fun test_parseSip002_withChacha20Encryption() {
        val ssUrl = createSip002Url(
            method = "chacha20-ietf-poly1305",
            password = "secret123",
            host = "ss.example.com",
            port = 443,
            remarks = "ChaCha20"
        )

        val result = ShadowsocksFmt.parseSip002(ssUrl)

        assertNotNull(result)
        assertEquals("chacha20-ietf-poly1305", result?.method)
    }

    @Test
    fun test_parseSip002_returnsNullForEmptyHost() {
        // Manually construct URL with empty host (can't use helper)
        val methodPassword = "aes-256-gcm:password"
        val base64UserInfo = JavaBase64.getUrlEncoder().withoutPadding()
            .encodeToString(methodPassword.toByteArray())
        val ssUrl = "${SS_SCHEME}${base64UserInfo}@:8388#No%20Host"

        val result = ShadowsocksFmt.parseSip002(ssUrl)

        assertNull(result)
    }

    @Test
    fun test_parseSip002_returnsNullForInvalidPort() {
        // Manually construct URL with invalid port (can't use helper)
        val methodPassword = "aes-256-gcm:password"
        val base64UserInfo = JavaBase64.getUrlEncoder().withoutPadding()
            .encodeToString(methodPassword.toByteArray())
        val ssUrl = "${SS_SCHEME}${base64UserInfo}@example.com:-1#Invalid%20Port"

        val result = ShadowsocksFmt.parseSip002(ssUrl)

        assertNull(result)
    }

    // ==================== Legacy Format Tests ====================

    @Test
    fun test_parseLegacy_validUrl() {
        val ssUrl = createLegacyUrl(
            method = "aes-256-gcm",
            password = "password123",
            host = "legacy.example.com",
            port = 8388,
            remarks = "Legacy Server"
        )

        val result = ShadowsocksFmt.parseLegacy(ssUrl)

        assertNotNull(result)
        assertEquals("Legacy Server", result?.remarks)
        assertEquals("legacy.example.com", result?.server)
        assertEquals("8388", result?.serverPort)
        assertEquals("aes-256-gcm", result?.method)
        assertEquals("password123", result?.password)
    }

    @Test
    fun test_parseLegacy_withPartiallyEncodedUrl() {
        // Partially encoded legacy format (method:password encoded, host:port not)
        val methodPassword = "chacha20-ietf-poly1305:my-pass"
        val base64Part = JavaBase64.getEncoder().encodeToString(methodPassword.toByteArray())
        val ssUrl = "${SS_SCHEME}${base64Part}@partial.example.com:443#Partial%20Encoded"

        val result = ShadowsocksFmt.parseLegacy(ssUrl)

        assertNotNull(result)
        assertEquals("Partial Encoded", result?.remarks)
        assertEquals("partial.example.com", result?.server)
        assertEquals("chacha20-ietf-poly1305", result?.method)
    }

    @Test
    fun test_parseLegacy_returnsNullForInvalidFormat() {
        val invalidContent = "not-a-valid-format"
        val base64Encoded = JavaBase64.getEncoder().encodeToString(invalidContent.toByteArray())
        val ssUrl = "${SS_SCHEME}${base64Encoded}#Invalid"

        val result = ShadowsocksFmt.parseLegacy(ssUrl)

        assertNull(result)
    }

    @Test
    fun test_parseLegacy_handlesPasswordWithColon() {
        // Special case: password contains colons (can't use helper)
        val legacyContent = "aes-256-gcm:pass:word:with:colons@example.com:8388"
        val base64Encoded = JavaBase64.getEncoder().encodeToString(legacyContent.toByteArray())
        val ssUrl = "${SS_SCHEME}${base64Encoded}#Colon%20Password"

        val result = ShadowsocksFmt.parseLegacy(ssUrl)

        assertNotNull(result)
        assertEquals("pass:word:with:colons", result?.password)
    }

    // ==================== toUri Tests ====================

    @Test
    fun test_toUri_createsValidSip002Url() {
        val config = ProfileItem.create(EConfigType.SHADOWSOCKS).apply {
            remarks = "Test Server"
            server = "example.com"
            serverPort = "8388"
            method = "aes-256-gcm"
            password = "my-secret-password"
        }

        val uri = ShadowsocksFmt.toUri(config)

        // Verify URI does not include scheme (toUri returns without ss:// prefix)
        assertFalse("toUri should not include scheme prefix", uri.startsWith(SS_SCHEME))

        assertTrue(uri.contains("@example.com:8388"))
        assertTrue(uri.contains("#Test%20Server"))

        // Verify the base64 part (URL decode first, then Base64 decode)
        val base64Part = uri.substringBefore("@")
        val urlDecoded = URLDecoder.decode(base64Part, Charsets.UTF_8.name())
        val decoded = String(JavaBase64.getDecoder().decode(urlDecoded))
        assertEquals("aes-256-gcm:my-secret-password", decoded)
    }

    @Test
    fun test_toUri_handlesIpv6Address() {
        val config = ProfileItem.create(EConfigType.SHADOWSOCKS).apply {
            remarks = "IPv6 Server"
            server = "2001:db8::1"
            serverPort = "8388"
            method = "aes-256-gcm"
            password = "password"
        }

        val uri = ShadowsocksFmt.toUri(config)

        assertTrue(uri.contains("[2001:db8::1]:8388"))
    }

    // ==================== Round-trip Tests ====================

    @Test
    fun test_parseAndToUri_roundTripPreservesData() {
        val originalUrl = createSip002Url(
            method = "chacha20-ietf-poly1305",
            password = "round-trip-password",
            host = "roundtrip.example.com",
            port = 443,
            remarks = "Round Trip Test"
        )

        val parsed = ShadowsocksFmt.parse(originalUrl)
        assertNotNull(parsed)

        val regeneratedUri = ShadowsocksFmt.toUri(parsed!!)

        // Verify toUri returns without scheme, so we need to prepend it
        assertFalse(
            "toUri should return URI without scheme",
            regeneratedUri.startsWith(SS_SCHEME)
        )

        val reparsed = ShadowsocksFmt.parse("$SS_SCHEME$regeneratedUri")
        assertNotNull(reparsed)

        assertEquals(parsed.remarks, reparsed?.remarks)
        assertEquals(parsed.server, reparsed?.server)
        assertEquals(parsed.serverPort, reparsed?.serverPort)
        assertEquals(parsed.method, reparsed?.method)
        assertEquals(parsed.password, reparsed?.password)
    }

    // ==================== Edge Cases ====================

    @Test
    fun test_parse_handlesEmptyRemarksGracefully() {
        // Empty remarks edge case (can't use helper)
        val methodPassword = "aes-256-gcm:password"
        val base64UserInfo = JavaBase64.getUrlEncoder().withoutPadding()
            .encodeToString(methodPassword.toByteArray())
        val ssUrl = "${SS_SCHEME}${base64UserInfo}@example.com:8388#"

        val result = ShadowsocksFmt.parse(ssUrl)

        assertNotNull(result)
        assertEquals("none", result?.remarks)
    }

    @Test
    fun test_parseSip002_handlesDifferentEncryptionMethods() {
        val methods = listOf("aes-128-gcm", "aes-256-gcm", "chacha20-ietf-poly1305")

        for (method in methods) {
            val ssUrl = createSip002Url(
                method = method,
                password = "testpass",
                host = "example.com",
                port = 8388,
                remarks = method
            )

            val result = ShadowsocksFmt.parseSip002(ssUrl)

            assertNotNull("Failed for method: $method", result)
            assertEquals(method, result?.method)
        }
    }

    @Test
    fun test_parseLegacy_convertsMethodToLowercase() {
        // Uppercase method to test lowercase conversion (can't use helper)
        val legacyContent = "AES-256-GCM:password@example.com:8388"
        val base64Encoded = JavaBase64.getEncoder().encodeToString(legacyContent.toByteArray())
        val ssUrl = "${SS_SCHEME}${base64Encoded}#Uppercase%20Method"

        val result = ShadowsocksFmt.parseLegacy(ssUrl)

        assertNotNull(result)
        assertEquals("aes-256-gcm", result?.method)
    }
}
