package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppResources
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The resolvers and the domains the ECH fields of the Aether editor and of the WARP keys page offer, read from the
 * string arrays of the app's resources, as the unit tests run in the module's folder: each is a value the core takes,
 * and the default comes first.
 */
class AetherEchOptionsTest {

    @Test
    fun everyResolverOnOfferIsOneTheCoreTakesWithTheDefaultFirst() {
        val resolvers = AppResources.stringArray("aether_ech_dns_options")
        assertEquals(
            listOf(
                "udp://1.1.1.1",
                "udp://8.8.8.8",
                "https://1.1.1.1/dns-query@sni=www.microsoft.com",
                "https://doq.dns4all.eu/dns-query@address=194.0.5.3",
                "tcp://1.1.1.1",
                "tcp://8.8.8.8",
            ),
            resolvers
        )
        assertEquals(AppConfig.AETHER_ECH_DNS, resolvers.first())
        resolvers.forEach { assertTrue(AetherFmt.isEchDns(it), it) }
    }

    @Test
    fun everyDomainOnOfferIsOneTheCoreTakesWithTheDefaultFirst() {
        val domains = AppResources.stringArray("aether_ech_domain_options")
        assertEquals(
            listOf(
                "cloudflare-ech.com",
                "crypto.cloudflare.com",
                "ip.gs",
                "api.cloudflareclient.com",
                "consumer-masque.cloudflareclient.com",
                "consumer-masque-proxy.cloudflareclient.com",
            ),
            domains
        )
        assertEquals(AppConfig.AETHER_ECH_DOMAIN, domains.first())
        domains.forEach { assertTrue(AetherFmt.isEchDomain(it), it) }
    }
}
