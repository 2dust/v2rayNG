package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.TlsSettingsCheck.Error
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The TLS settings the editor refuses to save, as the Xray-core fork would not apply them or would not connect. */
class TlsSettingsCheckTest {

    private fun profile(
        fingerprint: String? = null,
        cipherSuites: String? = null,
        network: String? = "ws",
        alpn: String? = null,
        type: EConfigType = EConfigType.VLESS,
        security: String? = AppConfig.TLS,
    ) = ProfileItem.create(type).apply {
        this.security = security
        this.fingerPrint = fingerprint
        this.cipherSuites = cipherSuites
        this.network = network
        this.alpn = alpn
    }

    private val suites = "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384:TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"

    @Test
    fun cipherSuitesNeedTheUnsafeFingerprint() {
        assertNull(TlsSettingsCheck.validate(profile(fingerprint = "unsafe", cipherSuites = suites)))
        // The fork lowercases the fingerprint.
        assertNull(TlsSettingsCheck.validate(profile(fingerprint = "Unsafe", cipherSuites = suites)))
        assertEquals(Error.CIPHER_SUITES_NEED_UNSAFE, TlsSettingsCheck.validate(profile(fingerprint = "chrome", cipherSuites = suites)))
        // An empty fingerprint is Chrome's.
        assertEquals(Error.CIPHER_SUITES_NEED_UNSAFE, TlsSettingsCheck.validate(profile(fingerprint = "", cipherSuites = suites)))
        assertEquals(Error.CIPHER_SUITES_NEED_UNSAFE, TlsSettingsCheck.validate(profile(fingerprint = null, cipherSuites = suites)))
        assertEquals(Error.CIPHER_SUITES_NEED_UNSAFE, TlsSettingsCheck.validate(profile(fingerprint = "randomized", cipherSuites = suites, network = "tcp")))
        // No cipherSuites, nothing to refuse.
        assertNull(TlsSettingsCheck.validate(profile(fingerprint = "chrome", cipherSuites = "")))
        assertNull(TlsSettingsCheck.validate(profile(fingerprint = "chrome", cipherSuites = " \n")))
        assertNull(TlsSettingsCheck.validate(profile(fingerprint = "chrome")))
    }

    @Test
    fun webSocketAndHttpUpgradeTakeOnlyHttp1OrNoAlpnWhateverTheFingerprint() {
        for (network in listOf("ws", "httpupgrade")) {
            for (fingerprint in listOf("unsafe", "chrome", "", "firefox", null)) {
                for (alpn in listOf("h2", "h2,http/1.1", "h3,h2,http/1.1", "h3,h2", "h3", "http/1.1,h2", " h2 , http/1.1 ", "http/1.1,h3")) {
                    assertEquals(
                        Error.WEBSOCKET_ALPN_NOT_HTTP1,
                        TlsSettingsCheck.validate(profile(fingerprint = fingerprint, network = network, alpn = alpn)),
                        "$network $fingerprint $alpn"
                    )
                }
                // Read as CoreOutboundBuilder reads it: names trimmed, empty ones skipped.
                for (alpn in listOf(null, "", " ", "http/1.1", " http/1.1 ", "http/1.1,")) {
                    assertNull(TlsSettingsCheck.validate(profile(fingerprint = fingerprint, network = network, alpn = alpn)), "$network $fingerprint $alpn")
                }
            }
        }
    }

    @Test
    fun theAlpnRuleKeepsToWebSocketAndHttpUpgrade() {
        for (network in listOf("tcp", "grpc", "xhttp", null)) {
            for (alpn in listOf("h2,http/1.1", "h3", "h2")) {
                assertNull(TlsSettingsCheck.validate(profile(fingerprint = "chrome", network = network, alpn = alpn)), "$network $alpn")
            }
        }
        // cipherSuites with the unsafe fingerprint still meet the alpn rule, and without it they are refused first.
        assertEquals(Error.WEBSOCKET_ALPN_NOT_HTTP1, TlsSettingsCheck.validate(profile(fingerprint = "unsafe", cipherSuites = suites, network = "ws", alpn = "h2")))
        assertEquals(Error.CIPHER_SUITES_NEED_UNSAFE, TlsSettingsCheck.validate(profile(fingerprint = "chrome", cipherSuites = suites, network = "ws", alpn = "h2")))
    }

    @Test
    fun theCheckKeepsToTheEditorsThatShowTheseSettingsUnderTls() {
        for (type in listOf(EConfigType.VMESS, EConfigType.VLESS, EConfigType.SHADOWSOCKS, EConfigType.TROJAN)) {
            assertEquals(Error.CIPHER_SUITES_NEED_UNSAFE, TlsSettingsCheck.validate(profile(fingerprint = "chrome", cipherSuites = suites, type = type)), "$type")
        }
        // Hysteria2's editor shows neither cipherSuites nor alpn, so a value an imported link left there is not refused.
        assertNull(TlsSettingsCheck.validate(profile(fingerprint = "chrome", cipherSuites = suites, type = EConfigType.HYSTERIA2)))
        assertNull(TlsSettingsCheck.validate(profile(fingerprint = "unsafe", alpn = "h2", type = EConfigType.HYSTERIA2)))
        // Nor under REALITY or without security, where the editor hides them.
        assertNull(TlsSettingsCheck.validate(profile(fingerprint = "chrome", cipherSuites = suites, security = AppConfig.REALITY)))
        assertNull(TlsSettingsCheck.validate(profile(fingerprint = "unsafe", alpn = "h2", security = "")))
        assertNull(TlsSettingsCheck.validate(profile(fingerprint = "chrome", cipherSuites = suites, security = null)))
    }

    @Test
    fun anImportedWebSocketOrHttpUpgradeLinkGetsHttp1InPlaceOfAnyOtherAlpn() {
        for (network in listOf("ws", "httpupgrade")) {
            for (fingerprint in listOf("chrome", "unsafe", null)) {
                for (alpn in listOf("h2,http/1.1", "h2", "h3", "h3,h2,http/1.1", "h3,h2", "http/1.1,h2")) {
                    val imported = profile(fingerprint = fingerprint, network = network, alpn = alpn)
                    TlsSettingsCheck.fixImportedAlpn(imported)
                    assertEquals("http/1.1", imported.alpn, "$network $fingerprint $alpn")
                    assertNull(TlsSettingsCheck.validate(imported))
                }
            }
            // A link without alpn, or with http/1.1 alone, stays as it came.
            for (alpn in listOf(null, "", " ", "http/1.1", " http/1.1 ", "http/1.1,")) {
                val imported = profile(fingerprint = "chrome", network = network, alpn = alpn)
                TlsSettingsCheck.fixImportedAlpn(imported)
                assertEquals(alpn, imported.alpn, "$network $alpn")
            }
        }
    }

    @Test
    fun anImportedLinkOfAnotherTransportOrOutsideTlsKeepsItsAlpn() {
        val kept = listOf(
            profile(network = "tcp", alpn = "h2,http/1.1"),
            profile(network = "grpc", alpn = "h2"),
            profile(network = "xhttp", alpn = "h3"),
            profile(network = "ws", alpn = "h2,http/1.1", security = AppConfig.REALITY),
            profile(network = "ws", alpn = "h2,http/1.1", security = ""),
            profile(network = "ws", alpn = "h2,http/1.1", security = null),
            profile(network = "ws", alpn = "h2,http/1.1", type = EConfigType.HYSTERIA2),
        )
        for (imported in kept) {
            val alpn = imported.alpn
            TlsSettingsCheck.fixImportedAlpn(imported)
            assertEquals(alpn, imported.alpn, "${imported.configType} ${imported.network} ${imported.security}")
        }
    }
}
