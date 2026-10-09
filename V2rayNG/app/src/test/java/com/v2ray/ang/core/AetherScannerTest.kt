package com.v2ray.ang.core

import com.v2ray.ang.dto.AetherEndpoint
import com.v2ray.ang.enums.AetherProtocol
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AetherScannerTest {

    private fun logLine(message: String) = "[2026-09-11T10:00:00.000Z INFO  aether] $message"

    @Test
    fun readsTheMasqueGatewayOutOfTheLog() {
        val found = AetherScanner.parse(AetherProtocol.MASQUE, logLine("[+] selected MASQUE gateway 162.159.197.3:443 (rtt 84ms)"))
        assertEquals(AetherEndpoint("162.159.197.3", 443), found?.endpoint)
        assertNull(found?.innerHop)
    }

    @Test
    fun readsAnIpv6MasqueGatewayWrittenWithoutBrackets() {
        val found = AetherScanner.parse(AetherProtocol.MASQUE, logLine("[+] selected MASQUE gateway 2606:4700:102::3:443 (rtt 90ms)"))
        assertEquals(AetherEndpoint("2606:4700:102::3", 443), found?.endpoint)
    }

    @Test
    fun readsTheWireguardEndpointOutOfTheLog() {
        val found = AetherScanner.parse(
            AetherProtocol.WIREGUARD,
            logLine("[+] selected WireGuard endpoint 162.159.192.1:894 using aethernoize profile 'balanced'")
        )
        assertEquals(AetherEndpoint("162.159.192.1", 894), found?.endpoint)

        val ipv6 = AetherScanner.parse(
            AetherProtocol.WIREGUARD,
            logLine("[+] selected WireGuard endpoint [2606:4700:d0::a29f:c001]:2408 using aethernoize profile 'light'")
        )
        assertEquals(AetherEndpoint("2606:4700:d0::a29f:c001", 2408), ipv6?.endpoint)
    }

    @Test
    fun readsBothGoolHopsOutOfOneLine() {
        val found = AetherScanner.parse(
            AetherProtocol.GOOL,
            logLine("[+] using cloudflare edge 162.159.192.1:2408 (outer) and 188.114.96.1:894 (inner)")
        )
        assertEquals(AetherEndpoint("162.159.192.1", 2408), found?.endpoint)
        assertEquals(AetherEndpoint("188.114.96.1", 894), found?.innerHop)
    }

    @Test
    fun goolWaitsForBothHopsInsteadOfTheFirstEndpoint() {
        assertNull(AetherScanner.parse(AetherProtocol.GOOL, logLine("[+] selected WireGuard endpoint 162.159.192.1:2408 (rtt 61ms)")))
        assertNull(AetherScanner.parse(AetherProtocol.GOOL, logLine("[+] using cloudflare edge 162.159.192.1:2408")))
    }

    @Test
    fun readsBothMimHopsOutOfTheReadyLine() {
        val found = AetherScanner.parse(
            AetherProtocol.MIM,
            logLine("[+] masque-in-masque ready: 162.159.192.1:443 (outer) and 188.114.96.1:443 (inner)")
        )
        assertEquals(AetherEndpoint("162.159.192.1", 443), found?.endpoint)
        assertEquals(AetherEndpoint("188.114.96.1", 443), found?.innerHop)

        val ipv6 = AetherScanner.parse(
            AetherProtocol.MIM,
            logLine("[+] masque-in-masque ready: [2606:4700:102::3]:443 (outer) and [2606:4700:103::4]:443 (inner)")
        )
        assertEquals(AetherEndpoint("2606:4700:102::3", 443), ipv6?.endpoint)
        assertEquals(AetherEndpoint("2606:4700:103::4", 443), ipv6?.innerHop)
    }

    @Test
    fun mimWaitsForThePairToCarryTrafficInsteadOfTheFirstHop() {
        assertNull(AetherScanner.parse(AetherProtocol.MIM, logLine("[+] selected MASQUE gateway 162.159.192.1:443 (rtt 84ms)")))
        assertNull(AetherScanner.parse(AetherProtocol.MIM, logLine("[*] establishing outer MASQUE tunnel to 162.159.192.1:443...")))
        assertNull(AetherScanner.parse(AetherProtocol.MIM, logLine("[+] inner MASQUE tunnel established through 188.114.96.1:443")))
        assertNull(
            AetherScanner.parse(
                AetherProtocol.MIM,
                logLine("[+] using cloudflare edge 162.159.192.1:2408 (outer) and 188.114.96.1:894 (inner)")
            )
        )
        assertNull(
            AetherScanner.parse(
                AetherProtocol.GOOL,
                logLine("[+] masque-in-masque ready: 162.159.192.1:443 (outer) and 188.114.96.1:443 (inner)")
            )
        )
    }

    @Test
    fun withAnExitRuleTheScanEndsOnTheEndpointWhoseExitTheCoreAccepted() {
        val match = AetherScanner.matcher(AetherProtocol.MASQUE, exitRuled = true)
        assertNull(match(logLine("[+] selected MASQUE gateway 162.159.197.3:443 (rtt 84ms)")))
        assertNull(match(logLine("[-] exit location IR rejected (not IR,RU)")))
        // The refused gateway is forgotten; a stray acceptance names nothing.
        assertNull(match(logLine("[+] exit location DE accepted (not IR,RU)")))
        assertNull(match(logLine("[+] selected MASQUE gateway 188.114.96.1:443 (rtt 90ms)")))
        assertEquals(AetherEndpoint("188.114.96.1", 443), match(logLine("[+] exit location DE accepted (not IR,RU)"))?.endpoint)

        val hops = AetherScanner.matcher(AetherProtocol.GOOL, exitRuled = true)
        assertNull(hops(logLine("[+] using cloudflare edge 162.159.192.1:2408 (outer) and 188.114.96.1:894 (inner)")))
        assertEquals(AetherEndpoint("188.114.96.1", 894), hops(logLine("[+] exit location SE accepted (DE,SE)"))?.innerHop)
    }

    @Test
    fun withoutAnExitRuleTheEndpointLineEndsTheScanAndMasqueInMasqueNeedsNoWait() {
        val plain = AetherScanner.matcher(AetherProtocol.MASQUE, exitRuled = false)
        assertEquals(AetherEndpoint("162.159.197.3", 443), plain(logLine("[+] selected MASQUE gateway 162.159.197.3:443 (rtt 84ms)"))?.endpoint)
        // The core names the masque-in-masque hops only after it has checked the exit, so the line stands on its own.
        val mim = AetherScanner.matcher(AetherProtocol.MIM, exitRuled = true)
        assertNull(mim(logLine("[+] exit location DE accepted (not IR)")))
        assertEquals(
            AetherEndpoint("162.159.192.1", 443),
            mim(logLine("[+] masque-in-masque ready: 162.159.192.1:443 (outer) and 188.114.96.1:443 (inner)"))?.endpoint
        )
    }

    @Test
    fun eachProtocolOnlyReadsItsOwnResult() {
        val masque = logLine("[+] selected MASQUE gateway 162.159.197.3:443 (rtt 84ms)")
        val wireguard = logLine("[+] selected WireGuard endpoint 162.159.192.1:894 (rtt 61ms)")
        assertNull(AetherScanner.parse(AetherProtocol.WIREGUARD, masque))
        assertNull(AetherScanner.parse(AetherProtocol.MASQUE, wireguard))
    }

    @Test
    fun aLineThatNamesNoUsableEndpointYieldsNothing() {
        assertNull(AetherScanner.parse(AetherProtocol.MASQUE, logLine("[+] selected protocol: MASQUE")))
        assertNull(AetherScanner.parse(AetherProtocol.MASQUE, logLine("[*] hunting for a working MASQUE gateway")))
        assertNull(AetherScanner.parse(AetherProtocol.MASQUE, logLine("[+] selected MASQUE gateway 162.159.197.3 (rtt 84ms)")))
        assertNull(AetherScanner.parse(AetherProtocol.MASQUE, ""))
        assertNull(
            AetherScanner.parse(
                AetherProtocol.GOOL,
                logLine("[+] using cloudflare edge 162.159.192.1:2408 (outer) and 188.114.96.1 (inner)")
            )
        )
    }
}
