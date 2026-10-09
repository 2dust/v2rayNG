package com.v2ray.ang.core

import com.v2ray.ang.AppResources
import com.v2ray.ang.enums.AetherFingerprint
import com.v2ray.ang.enums.AetherKeyKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AetherKeysTest {

    private fun valueAfter(arguments: List<String>, flag: String): String? =
        arguments.lastIndexOf(flag).takeIf { it >= 0 }?.let { arguments.getOrNull(it + 1) }

    /** The number of each TLS 1.2 suite by the name BoringSSL gives it, for the ones the fingerprints name. */
    private val suiteIds = mapOf(
        "ECDHE-ECDSA-AES128-GCM-SHA256" to 0xc02b,
        "ECDHE-RSA-AES128-GCM-SHA256" to 0xc02f,
        "ECDHE-ECDSA-AES256-GCM-SHA384" to 0xc02c,
        "ECDHE-RSA-AES256-GCM-SHA384" to 0xc030,
        "ECDHE-ECDSA-CHACHA20-POLY1305" to 0xcca9,
        "ECDHE-RSA-CHACHA20-POLY1305" to 0xcca8,
        "ECDHE-RSA-AES128-SHA" to 0xc013,
        "ECDHE-RSA-AES256-SHA" to 0xc014,
        "ECDHE-ECDSA-AES128-SHA" to 0xc009,
        "ECDHE-ECDSA-AES256-SHA" to 0xc00a,
        "ECDHE-ECDSA-AES128-SHA256" to 0xc023,
        "ECDHE-RSA-AES128-SHA256" to 0xc027,
        "AES128-GCM-SHA256" to 0x009c,
        "AES256-GCM-SHA384" to 0x009d,
        "AES128-SHA" to 0x002f,
        "AES256-SHA" to 0x0035,
    )

    /** The number of each TLS 1.2 suite by the name Xray's cipherSuites takes, Go's, for the ones its presets name. */
    private val xraySuiteIds = mapOf(
        "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256" to 0xc02b,
        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256" to 0xc02f,
        "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384" to 0xc02c,
        "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384" to 0xc030,
        "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256" to 0xcca9,
        "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256" to 0xcca8,
        "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA" to 0xc013,
        "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA" to 0xc014,
        "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA" to 0xc00a,
        "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256" to 0xc023,
        "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256" to 0xc027,
        "TLS_RSA_WITH_AES_128_GCM_SHA256" to 0x009c,
        "TLS_RSA_WITH_AES_256_GCM_SHA384" to 0x009d,
        "TLS_RSA_WITH_AES_128_CBC_SHA" to 0x002f,
        "TLS_RSA_WITH_AES_256_CBC_SHA" to 0x0035,
    )

    /** The TLS 1.2 suites of a ClientHello's cipher_suites in hex, GREASE and the TLS 1.3 suites left out. */
    private fun tls12Of(captured: String): List<Int> =
        captured.chunked(4).map { it.toInt(16) }.filter { it and 0x0f0f != 0x0a0a && it !in 0x1301..0x1305 }

    private fun suitesOf(fingerprint: AetherFingerprint): List<Int> = fingerprint.ciphers.split(':').map(suiteIds::getValue)

    @Test
    fun eachFingerprintOffersTheTls12SuitesOfItsCapturedClientHello() {
        // The ClientHellos captured with Wireshark. Chrome's, eaea130113021303c02bc02fc02cc030cca9cca8c013c014009c009d002f0035,
        // comes from Chrome's own rule, which BoringSSL orders by the phone's AES instructions as it does the TLS 1.3
        // suites: these twelve with them, ChaCha20 first without (both seen on boring 5.2.0). So Chrome names its rule,
        // as Chromium sets it, and keeps GREASE.
        assertEquals("ALL:!aPSK:!ECDSA+SHA1:!3DES", AetherFingerprint.CHROME.ciphers)
        assertEquals(listOf("--tls-ciphers", "ALL:!aPSK:!ECDSA+SHA1:!3DES"), AetherFingerprint.CHROME.arguments)
        assertEquals(tls12Of("130113031302c02bc02fcca9cca8c02cc030c013c014009c009d002f0035"), suitesOf(AetherFingerprint.FIREFOX))
        // Python's first ten, with c00a and c014 (AES-256-CBC with SHA-1) in place of c024 and c028 (with SHA-384),
        // which BoringSSL does not have; its four DHE suites after them are left out.
        val nearest = mapOf(0xc024 to 0xc00a, 0xc028 to 0xc014)
        assertEquals(
            tls12Of("130213031301c02cc030c02bc02fcca9cca8c024c028c023c027009f009e006b0067").take(10).map { nearest[it] ?: it },
            suitesOf(AetherFingerprint.SEMI_PYTHON)
        )
        // Go lists its TLS 1.3 suites last, which BoringSSL cannot do; its TLS 1.2 suites are all there.
        assertEquals(tls12Of("c02bc02fc02cc030cca9cca8c009c013c00ac014130113021303"), suitesOf(AetherFingerprint.GO))
    }

    @Test
    fun eachCipherSuitesPresetOffersTheTls12SuitesOfTheAetherFingerprintOfTheSameClient() {
        // The lists the cipherSuites field of the other protocols offers, as the app's resources give them.
        val presets = AppResources.stringArray("cipher_suites_preset_names")
            .zip(AppResources.stringArray("cipher_suites_preset_values")).toMap()
        fun presetSuites(name: String) = presets.getValue(name).split(':').map(xraySuiteIds::getValue)
        assertEquals(suitesOf(AetherFingerprint.FIREFOX), presetSuites("real-firefox-cipherSuites"))
        assertEquals(tls12Of("130113031302c02bc02fcca9cca8c02cc030c013c014009c009d002f0035"), presetSuites("real-firefox-cipherSuites"))
        assertEquals(suitesOf(AetherFingerprint.SEMI_PYTHON), presetSuites("semi-python-cipherSuites"))
    }

    @Test
    fun onlyChromeSendsGrease() {
        assertTrue(AetherFingerprint.CHROME.grease)
        assertFalse(AetherFingerprint.FIREFOX.grease)
        assertFalse(AetherFingerprint.SEMI_PYTHON.grease)
        assertFalse(AetherFingerprint.GO.grease)

        for (fingerprint in AetherFingerprint.entries) {
            val arguments = AetherKeys.arguments(AetherKeysSettings(fingerprint = fingerprint))
            assertEquals(fingerprint.ciphers, valueAfter(arguments, "--tls-ciphers"))
            assertEquals(!fingerprint.grease, "--disable-grease" in arguments)
        }
    }

    @Test
    fun theFingerprintListNamesEveryFingerprintInItsOrder() {
        // The dropdowns of the Aether editor and of the WARP keys page, read from the app's resources as the unit tests
        // run in the module's folder.
        assertEquals(AetherFingerprint.entries.map { it.type }, AppResources.stringArray("aether_fingerprint_values"))
        assertEquals(listOf("Chrome", "Firefox", "Semi-Python", "Go"), AppResources.stringArray("aether_fingerprint_entries"))
        assertEquals(AetherFingerprint.SEMI_PYTHON, AetherFingerprint.fromString("semi-python"))
    }

    @Test
    fun theDefaultsGetEveryKeyFromTheApisOwnAddressWithChromesRule() {
        assertEquals(
            listOf(
                "--register", "all",
                "--enroll-address", "api.cloudflareclient.com",
                "--tls-ciphers", "ALL:!aPSK:!ECDSA+SHA1:!3DES",
            ),
            AetherKeys.arguments(AetherKeysSettings())
        )
        assertEquals("aether --register all --enroll-address api.cloudflareclient.com --tls-ciphers ALL:!aPSK:!ECDSA+SHA1:!3DES", AetherKeys.builtCommand(AetherKeysSettings()))
        // A plain exit-node.
        assertEquals(AetherExit(), AetherKeysSettings().exit)
    }

    @Test
    fun eachKindRegistersItsKeysAndReadsBack() {
        for (kind in AetherKeyKind.entries) {
            val arguments = AetherKeys.arguments(AetherKeysSettings(kind = kind))
            assertEquals(listOf("--register", kind.type), arguments.take(2))
            assertEquals(kind, AetherKeys.kindOf(arguments))
        }
    }

    @Test
    fun theKindIsReadAsTheCoreReadsIt() {
        assertEquals(AetherKeyKind.WIREGUARD, AetherKeys.kindOf(listOf("--register", "WireGuard")))
        assertEquals(AetherKeyKind.WIREGUARD, AetherKeys.kindOf(listOf("--register", "warp")))
        assertEquals(AetherKeyKind.GOOL, AetherKeys.kindOf(listOf("--register", "warp-in-warp")))
        assertEquals(AetherKeyKind.GOOL, AetherKeys.kindOf(listOf("--register", " wiw ")))
        assertEquals(AetherKeyKind.MIM, AetherKeys.kindOf(listOf("--register", "masque-in-masque")))
        // The last one counts.
        assertEquals(AetherKeyKind.MASQUE, AetherKeys.kindOf(listOf("--register", "all", "--register", "masque")))
        assertNull(AetherKeys.kindOf(listOf("--register", "everything")))
        assertNull(AetherKeys.kindOf(listOf("--register")))
        assertNull(AetherKeys.kindOf(listOf("--protocol", "wg")))
    }

    @Test
    fun echAsksForTheKeyWhereTheSettingsSayAndKeepsTheDefaultsForBlanks() {
        val ech = AetherKeys.arguments(AetherKeysSettings(ech = true, echDns = "https://1.1.1.1/dns-query", echDomain = " example.com "))
        assertEquals("auto", valueAfter(ech, "--ech"))
        assertEquals("https://1.1.1.1/dns-query", valueAfter(ech, "--ech-dns"))
        assertEquals("example.com", valueAfter(ech, "--ech-domain"))

        val defaults = AetherKeys.arguments(AetherKeysSettings(ech = true, echDns = " ", echDomain = ""))
        assertEquals("udp://1.1.1.1", valueAfter(defaults, "--ech-dns"))
        assertEquals("cloudflare-ech.com", valueAfter(defaults, "--ech-domain"))

        // Off, nothing about ECH goes along, whatever the fields hold.
        assertFalse(AetherKeys.arguments(AetherKeysSettings(echDns = "tcp://8.8.8.8")).any { it.startsWith("--ech") })
    }

    @Test
    fun aBlankRequestAddressLeavesTheCoresOwn() {
        assertFalse("--enroll-address" in AetherKeys.arguments(AetherKeysSettings(enrollAddress = "  ")))
        assertEquals("188.114.97.6:443", valueAfter(AetherKeys.arguments(AetherKeysSettings(enrollAddress = " 188.114.97.6:443 ")), "--enroll-address"))
    }

    @Test
    fun theRequestAddressIsWhatTheCoreTakes() {
        for (address in listOf(
            "api.cloudflareclient.com", "api.cloudflareclient.com:443", "api.cloudflareclient.com.", "188.114.97.6",
            "188.114.97.6:2053", "2606:4700::1", "[2606:4700::1]", "[2606:4700::1]:8443", "[188.114.97.6]:443",
        )) {
            assertTrue(AetherKeys.isEnrollAddress(address), address)
        }
        for (address in listOf(
            "", "-x", "--upstream", "a b", "188.114.97.6:0", "188.114.97.6:65536", "188.114.97.6:", ":443",
            "188.114.97.6:4a3", "2606:4700::1:", "[2606:4700::1", "[2606:4700::1]:", "https://api.cloudflareclient.com",
            "api.-cloudflareclient.com",
            // An IPv6 address takes brackets before a port.
            "1:2:3:4:5:6:7:8:443",
        )) {
            assertFalse(AetherKeys.isEnrollAddress(address), address)
        }
    }

    @Test
    fun eachProblemIsFoundAndNothingElse() {
        val fine = AetherKeysSettings()
        assertNull(AetherKeys.problem(fine))
        assertEquals(AetherKeys.Problem.INVALID_ENROLL_ADDRESS, AetherKeys.problem(fine.copy(enrollAddress = "-x")))
        assertNull(AetherKeys.problem(fine.copy(enrollAddress = "")))
        // The ECH fields count while ECH is on.
        assertNull(AetherKeys.problem(fine.copy(echDns = "dns.google")))
        assertEquals(AetherKeys.Problem.INVALID_ECH_DNS, AetherKeys.problem(fine.copy(ech = true, echDns = "dns.google")))
        assertEquals(AetherKeys.Problem.INVALID_ECH_DOMAIN, AetherKeys.problem(fine.copy(ech = true, echDomain = "-x")))
        assertNull(AetherKeys.problem(fine.copy(ech = true, echDns = "", echDomain = "")))
        assertEquals(AetherKeys.Problem.INVALID_FINAL_MASK, AetherKeys.problem(fine.copy(finalMask = "{not json")))
        assertNull(AetherKeys.problem(fine.copy(finalMask = """{"tcp": []}""", dialMode = "anything")))
    }

    @Test
    fun aCommandWrittenByHandRunsAsWritten() {
        val settings = AetherKeysSettings(enrollAddress = "-x", command = "aether --register wg --tor-reverse")
        assertTrue(AetherKeys.isCustom(settings))
        assertEquals(listOf("--register", "wg", "--tor-reverse"), AetherKeys.runArguments(settings))
        // The settings it replaces do not count; what it registers does, and so does the exit-node it dials out through.
        assertNull(AetherKeys.problem(settings))
        assertEquals(AetherKeys.Problem.INVALID_COMMAND, AetherKeys.problem(settings.copy(command = "aether --protocol wg")))
        assertEquals(AetherKeys.Problem.INVALID_COMMAND, AetherKeys.problem(settings.copy(command = "--register every")))
        assertEquals(AetherKeys.Problem.INVALID_FINAL_MASK, AetherKeys.problem(settings.copy(finalMask = "[")))

        // The command the settings give, or none, is no command of its own.
        val built = AetherKeysSettings(kind = AetherKeyKind.MIM)
        assertFalse(AetherKeys.isCustom(built.copy(command = " " + AetherKeys.builtCommand(built) + " ")))
        assertFalse(AetherKeys.isCustom(built.copy(command = "")))
        assertEquals(AetherKeys.arguments(built), AetherKeys.runArguments(built.copy(command = AetherKeys.builtCommand(built))))
    }

    @Test
    fun anExitNodeTakesABlankFinalMaskOrAJsonObject() {
        assertTrue(AetherExit.takesFinalMask(null))
        assertTrue(AetherExit.takesFinalMask(" "))
        assertTrue(AetherExit.takesFinalMask("""{"tcp": []}"""))
        assertFalse(AetherExit.takesFinalMask("[]"))
        assertFalse(AetherExit.takesFinalMask("{not json"))
        assertFalse(AetherExit.takesFinalMask("ForceIP"))
    }

    @Test
    fun theExitNodeTakesTheFinalMaskAndTheDialMode() {
        val settings = AetherKeysSettings(finalMask = """{"tcp": []}""", dialMode = "ForceIP")
        assertEquals(AetherExit("""{"tcp": []}""", "ForceIP"), settings.exit)
        assertEquals(AetherExit(), settings.copy(finalMask = " ", dialMode = "").exit)
    }
}
