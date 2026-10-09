package com.v2ray.ang.ui.server

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.AetherFingerprint
import com.v2ray.ang.enums.AetherIpVersion
import com.v2ray.ang.enums.AetherObfuscation
import com.v2ray.ang.enums.AetherProtocol
import com.v2ray.ang.enums.AetherPsiphonCdnSet
import com.v2ray.ang.enums.AetherScanMode
import com.v2ray.ang.enums.AetherTransport
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerUiStateTest {

    @Test
    fun aetherOptionsAreNormalizedToValuesTheDropdownsKnow() {
        val profile = ProfileItem.create(EConfigType.AETHER).apply {
            aetherProtocol = "gool"
            aetherTransport = "h2"
            aetherScanMode = "from-a-newer-version"
            aetherObfuscation = null
            aetherIpVersion = "both"
        }

        val state = ServerUiState.from(profile)

        assertEquals(AetherProtocol.GOOL.type, state.aetherProtocol)
        assertEquals(AetherTransport.HTTP2.type, state.aetherTransport)
        assertEquals(AetherScanMode.BALANCED.type, state.aetherScanMode)
        assertEquals(AetherObfuscation.AUTO.type, state.aetherObfuscation)
        assertEquals(AetherIpVersion.DUAL.type, state.aetherIpVersion)
    }

    @Test
    fun aNewAetherProfileStartsWithTheDefaultsAndNoPort() {
        val state = ServerUiState.from(ProfileItem.create(EConfigType.AETHER))

        assertEquals(AetherProtocol.WIREGUARD.type, state.aetherProtocol)
        assertEquals(AetherTransport.HTTP3.type, state.aetherTransport)
        assertEquals("", state.port)
    }

    @Test
    fun theEchOutboundIsCarriedToTheProfileAndStoredOnlyWhenSet() {
        val json = """{"tag": "ech-out", "protocol": "freedom"}"""
        val profile = ProfileItem.create(EConfigType.VLESS).apply { echOutbound = json }

        val state = ServerUiState.from(profile)
        assertEquals(json, state.echOutbound)
        assertEquals(json, state.toProfileItem(profile).echOutbound)

        state.echOutbound = "  "
        assertNull(state.toProfileItem(profile).echOutbound)
    }

    @Test
    fun theTargetStrategyDefaultsToAsIsAndIsStoredOnlyWhenChanged() {
        val profile = ProfileItem.create(EConfigType.VLESS)

        val untouched = ServerUiState.from(profile)
        assertEquals(AppConfig.TARGET_STRATEGY_AS_IS, untouched.targetStrategy)
        assertNull(untouched.toProfileItem(profile).targetStrategy)

        untouched.targetStrategy = "UseIPv4v6"
        assertEquals("UseIPv4v6", untouched.toProfileItem(profile).targetStrategy)

        val stored = ServerUiState.from(ProfileItem.create(EConfigType.AETHER).apply { targetStrategy = "ForceIP" })
        assertEquals("ForceIP", stored.targetStrategy)
    }

    @Test
    fun aListenPortAProfileStillCarriesIsKeptAsItWasStored() {
        // Profiles stored while each profile had a listen port of its own may carry one still; the editor neither shows nor drops it.
        val stored = ProfileItem.create(EConfigType.AETHER).apply { aetherListenPort = "20808" }
        assertEquals("20808", ServerUiState.from(stored).toProfileItem(stored).aetherListenPort)

        val fresh = ProfileItem.create(EConfigType.AETHER)
        assertNull(ServerUiState.from(fresh).toProfileItem(fresh).aetherListenPort)
    }

    @Test
    fun psiphonIsOffByDefaultAndItsSettingsAreStoredOnlyWhileItIsOn() {
        val profile = ProfileItem.create(EConfigType.AETHER)
        val state = ServerUiState.from(profile)
        assertEquals("off", state.aetherPsiphon)
        assertEquals("auto", state.aetherPsiphonMode)
        assertEquals(true, state.aetherPsiphonBundledList)
        assertNull(state.toProfileItem(profile).aetherPsiphon)
        assertNull(state.toProfileItem(profile).aetherPsiphonMode)

        state.aetherPsiphonMode = "cdn"
        state.aetherPsiphonRegion = "DE"
        state.aetherPsiphonBundledList = false
        // Settings of a Psiphon that is off are not kept.
        assertNull(state.toProfileItem(profile).aetherPsiphonMode)
        assertNull(state.toProfileItem(profile).aetherPsiphonRegion)
        assertNull(state.toProfileItem(profile).aetherPsiphonBundledList)

        state.aetherPsiphon = "chain"
        val stored = state.toProfileItem(profile)
        assertEquals("chain", stored.aetherPsiphon)
        assertEquals("cdn", stored.aetherPsiphonMode)
        assertEquals("DE", stored.aetherPsiphonRegion)
        assertEquals(false, stored.aetherPsiphonBundledList)
        assertNull(stored.aetherPsiphonCdnIps)

        val reloaded = ServerUiState.from(stored)
        assertEquals("chain", reloaded.aetherPsiphon)
        assertEquals("cdn", reloaded.aetherPsiphonMode)
        assertEquals("DE", reloaded.aetherPsiphonRegion)
        assertEquals(false, reloaded.aetherPsiphonBundledList)
        // Yes is the default and is stored as nothing.
        reloaded.aetherPsiphonBundledList = true
        assertNull(reloaded.toProfileItem(stored).aetherPsiphonBundledList)
        assertEquals("", reloaded.aetherPsiphonCdnIps)
    }

    @Test
    fun torIsOffByDefaultAndItsSettingsAreStoredOnlyWhileItIsOn() {
        val profile = ProfileItem.create(EConfigType.AETHER)
        val state = ServerUiState.from(profile)
        assertEquals("off", state.aetherTor)
        assertEquals("auto", state.aetherTorBridges)
        assertEquals("", state.aetherTorBridgeLines)
        assertNull(state.toProfileItem(profile).aetherTor)

        state.aetherTorBridges = "own"
        state.aetherTorBridgeLines = "obfs4 192.0.2.55:38114 316E64 cert=abc iat-mode=0"
        // Settings of a Tor that is off are not kept.
        assertNull(state.toProfileItem(profile).aetherTorBridges)
        assertNull(state.toProfileItem(profile).aetherTorBridgeLines)

        state.aetherTor = "reverse"
        val stored = state.toProfileItem(profile)
        assertEquals("reverse", stored.aetherTor)
        assertEquals("own", stored.aetherTorBridges)
        assertEquals("obfs4 192.0.2.55:38114 316E64 cert=abc iat-mode=0", stored.aetherTorBridgeLines)

        val reloaded = ServerUiState.from(stored)
        assertEquals("reverse", reloaded.aetherTor)
        assertEquals("own", reloaded.aetherTorBridges)
        assertEquals("obfs4 192.0.2.55:38114 316E64 cert=abc iat-mode=0", reloaded.aetherTorBridgeLines)
    }

    @Test
    fun theTuningFieldsStartEmptyAndAreStoredOnlyWhenSet() {
        val profile = ProfileItem.create(EConfigType.AETHER)
        val state = ServerUiState.from(profile)
        assertEquals(AetherObfuscation.AUTO.type, state.aetherObfuscation)
        assertEquals(false, state.aetherEch)
        assertEquals("", state.aetherDns)
        assertEquals("", state.aetherExitLoc)
        assertEquals("auto", state.aetherTorRelays)
        val stored = state.toProfileItem(profile)
        assertEquals(false, stored.aetherEch)
        assertNull(stored.aetherDns)
        assertNull(stored.aetherExitLoc)
        assertNull(stored.aetherTorRelays)

        state.aetherEch = true
        state.aetherDns = "1.1.1.1"
        state.aetherExitLoc = "!IR"
        state.aetherTorRelays = "only"
        // The bridge pool belongs to Tor and is kept only while Tor is on.
        assertNull(state.toProfileItem(profile).aetherTorRelays)
        state.aetherTor = "chain"
        val tuned = state.toProfileItem(profile)
        assertEquals(true, tuned.aetherEch)
        assertEquals("1.1.1.1", tuned.aetherDns)
        assertEquals("!IR", tuned.aetherExitLoc)
        assertEquals("only", tuned.aetherTorRelays)

        val reloaded = ServerUiState.from(tuned)
        assertEquals(true, reloaded.aetherEch)
        assertEquals("1.1.1.1", reloaded.aetherDns)
        assertEquals("!IR", reloaded.aetherExitLoc)
        assertEquals("only", reloaded.aetherTorRelays)
    }

    @Test
    fun theCdnSetsAreChosenOneByOneAndStoredInTheOrderTheCoreTriesThem() {
        val blank = ProfileItem.create(EConfigType.AETHER)
        val state = ServerUiState.from(blank.apply { aetherPsiphon = "chain" })
        assertTrue(state.aetherPsiphonCdnSetChoice.isEmpty())

        state.setPsiphonCdnSet(AetherPsiphonCdnSet.FASTLY, true)
        state.setPsiphonCdnSet(AetherPsiphonCdnSet.CLOUDFLARE, true)
        assertEquals(setOf(AetherPsiphonCdnSet.CLOUDFLARE, AetherPsiphonCdnSet.FASTLY), state.aetherPsiphonCdnSetChoice)
        assertEquals("cloudflare,fastly", state.toProfileItem(blank).aetherPsiphonCdnSets)

        state.setPsiphonCdnSet(AetherPsiphonCdnSet.FASTLY, false)
        assertEquals("cloudflare", state.aetherPsiphonCdnSets)
        state.setPsiphonCdnSet(AetherPsiphonCdnSet.CLOUDFLARE, false)
        assertNull(state.toProfileItem(blank).aetherPsiphonCdnSets)

        // A choice from before comes back as it was, and goes with Psiphon when Psiphon goes.
        val reloaded = ServerUiState.from(blank.apply { aetherPsiphonCdnSets = "github,vercel" })
        assertEquals(setOf(AetherPsiphonCdnSet.VERCEL, AetherPsiphonCdnSet.GITHUB), reloaded.aetherPsiphonCdnSetChoice)
        reloaded.aetherPsiphon = "off"
        assertNull(reloaded.toProfileItem(blank).aetherPsiphonCdnSets)
    }

    @Test
    fun theFoldedSettingsAnnounceThemselvesOnlyWhenOneHoldsAValue() {
        val state = ServerUiState.from(ProfileItem.create(EConfigType.AETHER))
        assertEquals(AetherIpVersion.V4.type, state.aetherIpVersion)
        assertEquals(false, state.hasOtherAetherSettings)

        // The IP version stands outside the fold, so it does not count.
        state.aetherIpVersion = AetherIpVersion.DUAL.type
        assertEquals(false, state.hasOtherAetherSettings)

        state.aetherDns = "1.1.1.1"
        assertEquals(true, state.hasOtherAetherSettings)
        state.aetherDns = ""
        state.aetherExitLoc = "!IR"
        assertEquals(true, state.hasOtherAetherSettings)
        state.aetherExitLoc = ""
        state.targetStrategy = "UseIPv4v6"
        assertEquals(true, state.hasOtherAetherSettings)
        state.targetStrategy = ""
        // The exit-node's finalMask and dialMode stand outside the fold, after the fingerprint.
        state.finalMask = """{"tcp": []}"""
        state.dialMode = "custom"
        assertEquals(false, state.hasOtherAetherSettings)
    }

    @Test
    fun theFingerprintStartsAsChromesAndIsStoredAsChosen() {
        val state = ServerUiState.from(ProfileItem.create(EConfigType.AETHER))
        assertEquals(AetherFingerprint.CHROME.type, state.aetherFingerprint)

        state.aetherFingerprint = AetherFingerprint.GO.type
        val stored = state.toProfileItem(ProfileItem.create(EConfigType.AETHER))
        assertEquals("go", stored.aetherFingerprint)
        assertEquals("go", ServerUiState.from(stored).aetherFingerprint)

        // A value no build wrote reads as Chrome's.
        assertEquals("chrome", ServerUiState.from(stored.copy(aetherFingerprint = "lynx")).aetherFingerprint)
    }

    @Test
    fun theEchResolverAndDomainStartAtTheirDefaultsAndAreKeptWhetherEchIsOnOrOff() {
        val profile = ProfileItem.create(EConfigType.AETHER)
        val state = ServerUiState.from(profile)
        assertEquals(AppConfig.AETHER_ECH_DNS, state.aetherEchDns)
        assertEquals(AppConfig.AETHER_ECH_DOMAIN, state.aetherEchDomain)

        state.aetherEchDns = "https://doq.dns4all.eu/dns-query"
        state.aetherEchDomain = "ip.gs"
        for (ech in listOf(true, false)) {
            state.aetherEch = ech
            val stored = state.toProfileItem(profile)
            assertEquals("https://doq.dns4all.eu/dns-query", stored.aetherEchDns)
            assertEquals("ip.gs", stored.aetherEchDomain)
            val reloaded = ServerUiState.from(stored)
            assertEquals(ech, reloaded.aetherEch)
            assertEquals("https://doq.dns4all.eu/dns-query", reloaded.aetherEchDns)
            assertEquals("ip.gs", reloaded.aetherEchDomain)
        }

        // A field left empty is the default again.
        state.aetherEchDns = " "
        assertNull(state.toProfileItem(profile).aetherEchDns)
        assertEquals(AppConfig.AETHER_ECH_DNS, ServerUiState.from(state.toProfileItem(profile)).aetherEchDns)
    }

    @Test
    fun aCommandIsStoredOnlyWhenItSaysMoreThanTheSettings() {
        val profile = ProfileItem.create(EConfigType.AETHER)
        val state = ServerUiState.from(profile)
        assertEquals("", state.aetherCommand)
        assertNull(state.toProfileItem(profile).aetherCommand)

        // The command the settings build, typed back in, is no command of its own.
        val built = com.v2ray.ang.core.AetherCore.of(state.toProfileItem(profile)).command
        state.aetherCommand = " $built "
        assertNull(state.toProfileItem(profile).aetherCommand)

        state.aetherCommand = "$built --dns 1.1.1.1"
        assertEquals("$built --dns 1.1.1.1", state.toProfileItem(profile).aetherCommand)

        val reloaded = ServerUiState.from(state.toProfileItem(profile))
        assertEquals("$built --dns 1.1.1.1", reloaded.aetherCommand)
    }

    @Test
    fun aCommandIsWeighedAgainstTheSettingsOnTheListenPortTheScreenHolds() {
        val profile = ProfileItem.create(EConfigType.AETHER)
        val state = ServerUiState.from(profile)
        val built = com.v2ray.ang.core.AetherCore.of(state.toProfileItem(profile, 20808), 20808).command
        assertTrue("127.0.0.1:20808" in built, built)
        state.aetherCommand = built
        assertNull(state.toProfileItem(profile, 20808).aetherCommand)
        // On another port the same words say something else than the settings do.
        assertEquals(built, state.toProfileItem(profile, 10819).aetherCommand)
    }
}
