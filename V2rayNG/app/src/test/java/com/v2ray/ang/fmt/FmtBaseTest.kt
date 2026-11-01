package com.v2ray.ang.fmt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.URI

class FmtBaseTest {

    private val formatter = FmtBase()

    @Test
    fun queryPreservesEqualsInValues() {
        val query = formatter.getQueryParam(URI("vless://example.com?path=/ws?token=a=b=="))

        assertEquals("/ws?token=a=b==", query["path"])
    }

    @Test
    fun queryDecodesValuesAfterSplittingParameters() {
        val query = formatter.getQueryParam(
            URI("vless://example.com?path=%2Fws%3Ftoken%3Da%26b%3Dc&host=example.org")
        )

        assertEquals(mapOf("path" to "/ws?token=a&b=c", "host" to "example.org"), query)
    }

    @Test
    fun queryDecodesEscapesOnceAndPreservesLiteralPlus() {
        val query = formatter.getQueryParam(URI("vless://example.com?path=%253D+a%2Bb%3D%20c"))

        assertEquals("%3D+a+b= c", query["path"])
    }

    @Test
    fun queryPreservesExplicitEmptyValue() {
        val query = formatter.getQueryParam(URI("vless://example.com?sid=&type=ws"))

        assertEquals(mapOf("sid" to "", "type" to "ws"), query)
    }

    @Test
    fun queryKeepsLastValueForRepeatedKey() {
        val query = formatter.getQueryParam(URI("vless://example.com?path=/old&path=%2Fnew%3Da%3Db"))

        assertEquals(mapOf("path" to "/new=a=b"), query)
    }

    @Test
    fun queryStillRejectsParameterWithoutEquals() {
        assertThrows(IndexOutOfBoundsException::class.java) {
            formatter.getQueryParam(URI("vless://example.com?type=ws&flag"))
        }
    }

    @Test
    fun vlessImportPreservesWebSocketPath() {
        val profile = VlessFmt.parse(
            "vless://test@example.com:443?encryption=none&type=ws&path=/ws?token=a=b=="
        )

        assertNotNull(profile)
        assertEquals("ws", profile?.network)
        assertEquals("/ws?token=a=b==", profile?.path)

        val reparsed = VlessFmt.parse("vless://${VlessFmt.toUri(profile!!)}")
        assertEquals(profile.path, reparsed?.path)
    }

    @Test
    fun wireguardImportPreservesBase64Padding() {
        // Synthetic 32-byte sequences, not deployment keys.
        val publicKey = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
        val presharedKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        val profile = WireguardFmt.parse(
            "wireguard://$presharedKey@example.com:51820?publickey=$publicKey&presharedkey=$presharedKey"
        )

        assertNotNull(profile)
        assertEquals(publicKey, profile?.publicKey)
        assertEquals(presharedKey, profile?.preSharedKey)
    }
}
