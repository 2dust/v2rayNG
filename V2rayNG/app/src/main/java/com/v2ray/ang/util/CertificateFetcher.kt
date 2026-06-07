package com.v2ray.ang.util

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.TlsVersion
import org.conscrypt.Conscrypt
import java.security.MessageDigest
import java.security.Security
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Utility for fetching and validating SSL/TLS certificates from remote servers.
 * Supports TLS 1.2+ on all Android versions including Android 7.0 via Conscrypt.
 */
object CertificateFetcher {

    data class CertificateResult(
        val fingerprints: List<String>,
        val error: String? = null
    )

    /**
     * Fetches the certificate chain from a domain and returns SHA-256 fingerprints.
     *
     * @param domain The domain with optional port (e.g., "example.com" or "example.com:443")
     * @param serverName The SNI hostname to use (defaults to domain without port)
     * @param allowInsecure Whether to skip certificate validation
     * @return CertificateResult containing fingerprints or error message
     */
    suspend fun fetchCertificateFingerprints(
        domain: String,
        serverName: String? = null,
        allowInsecure: Boolean = false
    ): CertificateResult = withContext(Dispatchers.IO) {
        installConscryptIfNeeded()

        var capturedCertificates: Array<X509Certificate>? = null

        try {
            val (host, port) = parseDomainAndPort(domain)
            val sni = serverName?.takeIf { it.isNotBlank() }?.trimEnd('.') ?: host.trimEnd('.')

            val capturingTrustManager = createCapturingTrustManager(allowInsecure) { chain ->
                capturedCertificates = chain
            }

            val client = createOkHttpClient(capturingTrustManager, sni, host)
            val url = okhttp3.HttpUrl.Builder()
                .scheme("https")
                .host(host)
                .port(port)
                .build()
            val request = Request.Builder().url(url).build()

            runCatching {
                client.newCall(request).execute().close()
            }.onFailure { exception ->
                if (exception is javax.net.ssl.SSLHandshakeException && capturedCertificates.isNullOrEmpty()) {
                    return@withContext CertificateResult(
                        fingerprints = emptyList(),
                        error = "SSL handshake failed: ${exception.message}"
                    )
                }
            }

            val certificates = capturedCertificates
            if (certificates.isNullOrEmpty()) {
                return@withContext CertificateResult(
                    fingerprints = emptyList(),
                    error = "Failed to capture certificates from $domain"
                )
            }

            CertificateResult(fingerprints = certificates.map { calculateSha256Fingerprint(it) })
        } catch (e: Exception) {
            CertificateResult(
                fingerprints = emptyList(),
                error = "${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    /**
     * Concatenates multiple fingerprints into a comma-separated string.
     */
    fun concatenateFingerprints(fingerprints: List<String>): String =
        fingerprints.joinToString(",")

    private fun installConscryptIfNeeded() {
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.N..Build.VERSION_CODES.N_MR1) {
            runCatching {
                Security.insertProviderAt(Conscrypt.newProvider(), 1)
            }
        }
    }

    private fun parseDomainAndPort(domain: String): Pair<String, Int> {
        val parts = domain.split(":", limit = 2)
        return if (parts.size == 2) {
            Pair(parts[0], parts[1].toIntOrNull() ?: 443)
        } else {
            Pair(domain, 443)
        }
    }

    private fun createCapturingTrustManager(
        allowInsecure: Boolean,
        onCertificatesCaptured: (Array<X509Certificate>) -> Unit
    ): X509TrustManager {
        val defaultTrustManager = runCatching { createDefaultTrustManager() }.getOrNull()

        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                if (!allowInsecure) {
                    defaultTrustManager?.checkClientTrusted(chain, authType)
                }
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                chain?.let { onCertificatesCaptured(it.map { cert -> cert as X509Certificate }.toTypedArray()) }
                if (!allowInsecure) {
                    defaultTrustManager?.checkServerTrusted(chain, authType)
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> =
                defaultTrustManager?.acceptedIssuers ?: emptyArray()
        }
    }

    private fun createDefaultTrustManager(): X509TrustManager {
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(null as java.security.KeyStore?)
        return trustManagerFactory.trustManagers
            .firstOrNull { it is X509TrustManager } as? X509TrustManager
            ?: throw IllegalStateException("No X509TrustManager found")
    }

    private fun createOkHttpClient(
        trustManager: X509TrustManager,
        sni: String,
        host: String
    ): OkHttpClient {
        val sslContext = SSLContext.getInstance("TLS", Conscrypt.newProvider())
        sslContext.init(null, arrayOf(trustManager), null)

        val connectionSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)
            .build()

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .connectionSpecs(listOf(connectionSpec, ConnectionSpec.COMPATIBLE_TLS))
            .hostnameVerifier { hostname, _ -> hostname == sni || hostname == host }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private fun calculateSha256Fingerprint(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
