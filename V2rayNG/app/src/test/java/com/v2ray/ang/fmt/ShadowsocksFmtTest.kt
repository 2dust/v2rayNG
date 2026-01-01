package com.v2ray.ang.fmt

import android.util.Base64
import android.util.Log
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ProfileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.mockStatic
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

    private lateinit var mockBase64: MockedStatic<Base64>
    private lateinit var mockLog: MockedStatic<Log>

    @Before
    fun setUp() {
        mockLog = mockStatic(Log::class.java, Mockito.RETURNS_DEFAULTS)

        mockBase64 = mockStatic(Base64::class.java)
        // Mock decode
        mockBase64.`when`<ByteArray> {
            Base64.decode(Mockito.anyString(), Mockito.anyInt())
        }.thenAnswer { invocation ->
            val input = invocation.arguments[0] as String
            try {
                JavaBase64.getDecoder().decode(input)
            } catch (e: Exception) {
                try {
                    JavaBase64.getUrlDecoder().decode(input)
                } catch (e2: Exception) {
                    ByteArray(0)
                }
            }
        }
        // Mock encode
        mockBase64.`when`<String> {
            Base64.encodeToString(Mockito.any(ByteArray::class.java), Mockito.anyInt())
        }.thenAnswer { invocation ->
            val input = invocation.arguments[0] as ByteArray
            JavaBase64.getEncoder().withoutPadding().encodeToString(input)
        }
    }

    @After
    fun tearDown() {
        mockLog.close()
        mockBase64.close()
    }

    // ==================== SIP002 Format Tests ====================

    @Test
    fun `parseSip002 valid URL with base64 encoded userinfo`() {
        val methodPassword = "aes-256-gcm:my-secret-password"
        val base64UserInfo = JavaBase64.getUrlEncoder().withoutPadding().encodeToString(methodPassword.toByteArray())
        val ssUrl = "ss://${base64UserInfo}@example.com:8388#Test%20Server"

        val result = ShadowsocksFmt.parseSip002(ssUrl)

        assertNotNull(result)
        assertEquals("Test Server", result?.remarks)
        assertEquals("example.com", result?.server)
        assertEquals("8388", result?.serverPort)
        assertEquals("aes-256-gcm", result?.method)
        assertEquals("my-secret-password", result?.password)
    }

    @Test
    fun `parseSip002 valid URL with plain text userinfo`() {
        val ssUrl = "ss://aes-256-gcm:mypassword@example.com:8388#Plain%20Server"

        val result = ShadowsocksFmt.parseSip002(ssUrl)

        assertNotNull(result)
        assertEquals("Plain Server", result?.remarks)
        assertEquals("aes-256-gcm", result?.method)
        assertEquals("mypassword", result?.password)
    }

    @Test
    fun `parseSip002 with chacha20 encryption`() {
        val methodPassword = "chacha20-ietf-poly1305:secret123"
        val base64UserInfo = JavaBase64.getUrlEncoder().withoutPadding().encodeToString(methodPassword.toByteArray())
        val ssUrl = "ss://${base64UserInfo}@ss.example.com:443#ChaCha20"

        val result = ShadowsocksFmt.parseSip002(ssUrl)

        assertNotNull(result)
        assertEquals("chacha20-ietf-poly1305", result?.method)
    }

    @Test
    fun `parseSip002 returns null for empty host`() {
        val methodPassword = "aes-256-gcm:password"
        val base64UserInfo = JavaBase64.getUrlEncoder().withoutPadding().encodeToString(methodPassword.toByteArray())
        val ssUrl = "ss://${base64UserInfo}@:8388#No%20Host"

        val result = ShadowsocksFmt.parseSip002(ssUrl)

        assertNull(result)
    }

    @Test
    fun `parseSip002 returns null for invalid port`() {
        val methodPassword = "aes-256-gcm:password"
        val base64UserInfo = JavaBase64.getUrlEncoder().withoutPadding().encodeToString(methodPassword.toByteArray())
        val ssUrl = "ss://${base64UserInfo}@example.com:-1#Invalid%20Port"

        val result = ShadowsocksFmt.parseSip002(ssUrl)

        assertNull(result)
    }

    // ==================== Legacy Format Tests ====================

    @Test
    fun `parseLegacy valid URL`() {
        val legacyContent = "aes-256-gcm:password123@legacy.example.com:8388"
        val base64Encoded = JavaBase64.getEncoder().encodeToString(legacyContent.toByteArray())
        val ssUrl = "ss://${base64Encoded}#Legacy%20Server"

        val result = ShadowsocksFmt.parseLegacy(ssUrl)

        assertNotNull(result)
        assertEquals("Legacy Server", result?.remarks)
        assertEquals("legacy.example.com", result?.server)
        assertEquals("8388", result?.serverPort)
        assertEquals("aes-256-gcm", result?.method)
        assertEquals("password123", result?.password)
    }

    @Test
    fun `parseLegacy with partially encoded URL`() {
        val methodPassword = "chacha20-ietf-poly1305:my-pass"
        val base64Part = JavaBase64.getEncoder().encodeToString(methodPassword.toByteArray())
        val ssUrl = "ss://${base64Part}@partial.example.com:443#Partial%20Encoded"

        val result = ShadowsocksFmt.parseLegacy(ssUrl)

        assertNotNull(result)
        assertEquals("Partial Encoded", result?.remarks)
        assertEquals("partial.example.com", result?.server)
        assertEquals("chacha20-ietf-poly1305", result?.method)
    }

    @Test
    fun `parseLegacy returns null for invalid format`() {
        val invalidContent = "not-a-valid-format"
        val base64Encoded = JavaBase64.getEncoder().encodeToString(invalidContent.toByteArray())
        val ssUrl = "ss://${base64Encoded}#Invalid"

        val result = ShadowsocksFmt.parseLegacy(ssUrl)

        assertNull(result)
    }

    @Test
    fun `parseLegacy handles password with colon`() {
        val legacyContent = "aes-256-gcm:pass:word:with:colons@example.com:8388"
        val base64Encoded = JavaBase64.getEncoder().encodeToString(legacyContent.toByteArray())
        val ssUrl = "ss://${base64Encoded}#Colon%20Password"

        val result = ShadowsocksFmt.parseLegacy(ssUrl)

        assertNotNull(result)
        assertEquals("pass:word:with:colons", result?.password)
    }

    // ==================== toUri Tests ====================

    @Test
    fun `toUri creates valid SIP002 URL`() {
        val config = ProfileItem.create(EConfigType.SHADOWSOCKS).apply {
            remarks = "Test Server"
            server = "example.com"
            serverPort = "8388"
            method = "aes-256-gcm"
            password = "my-secret-password"
        }

        val uri = ShadowsocksFmt.toUri(config)

        assertTrue(uri.contains("@example.com:8388"))
        assertTrue(uri.contains("#Test%20Server"))

        // Verify the base64 part
        val base64Part = uri.substringBefore("@")
        val decoded = String(JavaBase64.getDecoder().decode(base64Part))
        assertEquals("aes-256-gcm:my-secret-password", decoded)
    }

    @Test
    fun `toUri handles IPv6 address`() {
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
    fun `parse and toUri round-trip preserves data`() {
        val methodPassword = "chacha20-ietf-poly1305:round-trip-password"
        val base64UserInfo = JavaBase64.getUrlEncoder().withoutPadding().encodeToString(methodPassword.toByteArray())
        val originalUrl = "ss://${base64UserInfo}@roundtrip.example.com:443#Round%20Trip%20Test"

        val parsed = ShadowsocksFmt.parse(originalUrl)
        assertNotNull(parsed)

        val regeneratedUri = ShadowsocksFmt.toUri(parsed!!)
        val reparsed = ShadowsocksFmt.parse("ss://$regeneratedUri")
        assertNotNull(reparsed)

        assertEquals(parsed.remarks, reparsed?.remarks)
        assertEquals(parsed.server, reparsed?.server)
        assertEquals(parsed.serverPort, reparsed?.serverPort)
        assertEquals(parsed.method, reparsed?.method)
        assertEquals(parsed.password, reparsed?.password)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `parse handles empty remarks gracefully`() {
        val methodPassword = "aes-256-gcm:password"
        val base64UserInfo = JavaBase64.getUrlEncoder().withoutPadding().encodeToString(methodPassword.toByteArray())
        val ssUrl = "ss://${base64UserInfo}@example.com:8388#"

        val result = ShadowsocksFmt.parse(ssUrl)

        assertNotNull(result)
        assertEquals("none", result?.remarks)
    }

    @Test
    fun `parseSip002 handles different encryption methods`() {
        val methods = listOf("aes-128-gcm", "aes-256-gcm", "chacha20-ietf-poly1305")

        for (method in methods) {
            val methodPassword = "$method:testpass"
            val base64UserInfo = JavaBase64.getUrlEncoder().withoutPadding().encodeToString(methodPassword.toByteArray())
            val ssUrl = "ss://${base64UserInfo}@example.com:8388#$method"

            val result = ShadowsocksFmt.parseSip002(ssUrl)

            assertNotNull("Failed for method: $method", result)
            assertEquals(method, result?.method)
        }
    }

    @Test
    fun `parseLegacy converts method to lowercase`() {
        val legacyContent = "AES-256-GCM:password@example.com:8388"
        val base64Encoded = JavaBase64.getEncoder().encodeToString(legacyContent.toByteArray())
        val ssUrl = "ss://${base64Encoded}#Uppercase%20Method"

        val result = ShadowsocksFmt.parseLegacy(ssUrl)

        assertNotNull(result)
        assertEquals("aes-256-gcm", result?.method)
    }
}
