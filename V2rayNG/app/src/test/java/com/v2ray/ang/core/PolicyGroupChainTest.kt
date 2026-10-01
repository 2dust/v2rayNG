package com.v2ray.ang.core

import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.CoreConfigContext
import com.v2ray.ang.dto.V2rayConfig.OutboundBean
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyGroupChainTest {
    private fun node(id: String, subscription: String = "source", remark: String = id) =
        CoreConfigContext.ResolvedProfile(id, ProfileItem(
            configType = EConfigType.SOCKS, subscriptionId = subscription,
            remarks = remark, server = "192.0.2.1", serverPort = "1080",
        ))

    private val a = node("a")
    private val b = node("b")
    private val landing = node("landing", "endpoints")
    private val preproxy = node("preproxy", "endpoints")
    private val group = ProfileItem(
        configType = EConfigType.POLICYGROUP, subscriptionId = "owner",
        policyGroupSubscriptionId = "source",
    )

    private fun resolve(
        subscription: SubscriptionItem = SubscriptionItem(nextProfile = "landing"),
        servers: List<CoreConfigContext.ResolvedProfile> = listOf(a, b, landing, preproxy),
        profile: ProfileItem = group,
        tag: String = AppConfig.TAG_PROXY,
        guid: String = "group",
    ) = CoreConfigContextBuilder.resolvePolicyGroup(tag, guid, profile, servers, subscription)

    private fun generate(resolved: CoreConfigContext.ResolvedOutbound) =
        CoreConfigManager.buildPolicyGroupOutbounds(resolved, emptySet()) {
            OutboundBean(protocol = "socks", settings = OutboundBean.OutSettingsBean(address = it.remarks))
        }

    private fun paths(generated: CoreConfigManager.PolicyGroupOutbounds): List<List<String?>> {
        val byTag = generated.outbounds.associateBy { it.tag }
        return generated.rootTags.map { root ->
            val path = mutableListOf<String?>()
            var current: String? = root
            val visited = mutableSetOf<String>()
            while (current != null) {
                assertTrue("Cycle in generated chain", visited.add(current))
                val outbound = requireNotNull(byTag[current])
                path.add(outbound.settings?.address?.toString())
                current = outbound.streamSettings?.sockopt?.dialerProxy
            }
            path
        }
    }

    @Test
    fun everyCandidateDialsLandingThroughItsOwnEntry() {
        val generated = generate(resolve())
        assertEquals(listOf(listOf("landing", "a"), listOf("landing", "b")), paths(generated))
        assertEquals(4, generated.outbounds.size)
        assertEquals(generated.rootTags, generated.outbounds.filter { it.tag.startsWith(generated.selector) }.map { it.tag })
    }

    @Test
    fun bothSubscriptionNeighborsKeepDestinationFirstDialerOrder() {
        val generated = generate(resolve(SubscriptionItem(nextProfile = "landing", prevProfile = "preproxy")))
        assertEquals(listOf(listOf("landing", "a", "preproxy"), listOf("landing", "b", "preproxy")), paths(generated))
    }

    @Test
    fun preproxyOnlyAndUnchainedGroupsKeepTheirEntryAsRoot() {
        assertEquals(listOf(listOf("a", "preproxy"), listOf("b", "preproxy")),
            paths(generate(resolve(SubscriptionItem(prevProfile = "preproxy")))))
        assertEquals(listOf(listOf("a"), listOf("b")), paths(generate(resolve(SubscriptionItem()))))
    }

    @Test
    fun candidateSourceDoesNotReplaceOwnerSubscription() {
        val resolved = resolve()
        assertTrue(resolved.policyGroup!!.hasSubscriptionChain)
        assertEquals("owner", resolved.profile.subscriptionId)
        assertEquals(setOf("source"), resolved.resolvedProfiles.map { it.subscriptionId }.toSet())
        assertEquals(listOf("landing", "a"), resolved.policyGroup.members.first().chain.map { it.guid })
    }

    @Test
    fun chainEndpointsCannotBecomeTheirOwnCandidate() {
        val resolved = resolve(servers = listOf(a, landing.copy(profile = landing.profile.copy(subscriptionId = "source"))))
        assertEquals(listOf("a"), resolved.policyGroup!!.members.map { it.guid })
    }

    @Test
    fun stableIdsSurviveReorderingAndDuplicateRemarks() {
        val servers = listOf(a.copy(profile = a.profile.copy(remarks = "same")), b.copy(profile = b.profile.copy(remarks = "same")), landing)
        val first = generate(resolve(servers = servers))
        val second = generate(resolve(servers = servers.reversed()))
        assertEquals(first.rootTags.toSet(), second.rootTags.toSet())
        assertEquals(2, first.rootTags.toSet().size)
    }

    @Test
    fun separateGroupsAndRoutingInstancesNeverMatchEachOthersSelector() {
        val first = generate(resolve())
        val second = generate(resolve(guid = "group-other"))
        val routed = generate(resolve(tag = "proxy-similar"))
        for (other in listOf(second, routed)) {
            assertFalse(other.outbounds.any { it.tag.startsWith(first.selector) })
            assertFalse(first.outbounds.any { it.tag.startsWith(other.selector) })
            assertTrue(first.outbounds.map { it.tag }.intersect(other.outbounds.map { it.tag }.toSet()).isEmpty())
        }
    }

    @Test
    fun missingOrComplexEndpointsFailInsteadOfDroppingLanding() {
        assertThrows(PolicyGroupConfigException::class.java) { resolve(SubscriptionItem(nextProfile = "missing")) }
        for (type in listOf(EConfigType.CUSTOM, EConfigType.POLICYGROUP, EConfigType.PROXYCHAIN)) {
            assertThrows(PolicyGroupConfigException::class.java) {
                resolve(servers = listOf(a, landing.copy(profile = landing.profile.copy(configType = type))))
            }
        }
    }

    @Test
    fun absentSubscriptionAndRepeatedEndpointsFail() {
        assertThrows(PolicyGroupConfigException::class.java) {
            CoreConfigContextBuilder.resolvePolicyGroup("proxy", "group", group, listOf(a), null)
        }
        assertThrows(PolicyGroupConfigException::class.java) {
            resolve(SubscriptionItem(nextProfile = "landing", prevProfile = "landing"))
        }
    }

    @Test
    fun emptyCandidatesIncludingOnlyLandingFail() {
        assertThrows(PolicyGroupConfigException::class.java) { resolve(servers = listOf(landing)) }
        assertThrows(PolicyGroupConfigException::class.java) {
            resolve(servers = listOf(landing), profile = group.copy(policyGroupSubscriptionId = null))
        }
    }

    @Test
    fun regexAndLiteralFiltersKeepTheirExistingMeaning() {
        assertEquals(listOf(a.profile), resolve(profile = group.copy(policyGroupFilter = "^a$")).resolvedProfiles)
        val literal = a.copy(profile = a.profile.copy(remarks = "[a"))
        assertEquals(listOf(literal.profile), resolve(servers = listOf(literal, b, landing), profile = group.copy(policyGroupFilter = "[")).resolvedProfiles)
    }

    @Test
    fun firstMatchingLegacyRemarkStillResolvesTheEndpoint() {
        val duplicate = landing.copy(guid = "other-landing")
        assertEquals("other-landing", resolve(servers = listOf(a, duplicate, landing)).policyGroup!!.members.first().chain.first().guid)
    }

    @Test
    fun ordinaryFallbackUsesSameChainButIsNotACandidate() {
        val resolved = resolve(profile = group.copy(policyGroupType = "2", policyGroupFallbackTag = "b"))
        val generated = generate(resolved)
        val root = generated.outbounds.single { it.tag == generated.fallbackTag }
        assertEquals("landing", root.settings!!.address)
        assertFalse(root.tag.startsWith(generated.selector))
        val entry = generated.outbounds.single { it.tag == root.streamSettings!!.sockopt!!.dialerProxy }
        assertEquals("b", entry.settings!!.address)
    }

    @Test
    fun explicitDirectAndBlockRemainUserOverrides() {
        for (tag in listOf(AppConfig.TAG_DIRECT, AppConfig.TAG_BLOCKED, AppConfig.TAG_PROXY, "")) {
            val resolved = resolve(profile = group.copy(policyGroupType = "3", policyGroupFallbackTag = tag))
            assertNull(resolved.policyGroup!!.fallback)
            assertEquals(tag, resolved.profile.policyGroupFallbackTag)
        }
    }

    @Test
    fun disabledHealthCheckDoesNotResolveUnusedFallback() {
        val resolved = resolve(profile = group.copy(policyGroupType = "2", policyGroupTestOutbounds = false, policyGroupFallbackTag = "missing"))
        assertNull(resolved.policyGroup!!.fallback)
    }

    @Test
    fun chainedFallbackCannotBeMissingComplexOrAnEndpoint() {
        for (tag in listOf("missing", "landing", "complex")) {
            assertThrows(PolicyGroupConfigException::class.java) {
                resolve(servers = listOf(a, landing, node("complex").copy(profile = ProfileItem(configType = EConfigType.PROXYCHAIN, remarks = "complex"))),
                    profile = group.copy(policyGroupType = "2", policyGroupFallbackTag = tag))
            }
        }
    }

    @Test
    fun aFailedHopNeverProducesAPartialChain() {
        assertThrows(PolicyGroupConfigException::class.java) {
            CoreConfigManager.buildPolicyGroupOutbounds(resolve(), emptySet()) {
                if (it.remarks == "landing") null else OutboundBean(protocol = "socks")
            }
        }
        assertThrows(PolicyGroupConfigException::class.java) {
            CoreConfigManager.buildPolicyGroupOutbounds(resolve(), emptySet()) { error("sensitive converter input") }
        }.also { assertFalse(it.message.orEmpty().contains("sensitive")) }
    }

    @Test
    fun generatedTagsAreCheckedAgainstExistingOutbounds() {
        val resolved = resolve()
        val first = generate(resolved)
        assertThrows(PolicyGroupConfigException::class.java) {
            CoreConfigManager.buildPolicyGroupOutbounds(resolved, setOf(first.rootTags.first())) { OutboundBean(protocol = "socks") }
        }
    }

    @Test
    fun xhttpIndependentDownloadUsesSameDialerWithoutMutatingProfileExtra() {
        val extra = """{"downloadSettings":{"address":"192.0.2.2","sockopt":{"tcpFastOpen":true,"dialerProxy":"old"}}} """.trim()
        val profile = a.profile.copy(xhttpExtra = extra)
        val outbound = OutboundBean(protocol = "vless", streamSettings = OutboundBean.StreamSettingsBean(
            xhttpSettings = OutboundBean.StreamSettingsBean.XhttpSettingsBean(extra = JsonParser.parseString(extra)),
        ))
        CoreOutboundBuilder.applyChainDialer(outbound, profile, "entry")
        assertEquals("entry", outbound.streamSettings!!.sockopt!!.dialerProxy)
        val download = (outbound.streamSettings!!.xhttpSettings!!.extra as com.google.gson.JsonObject).getAsJsonObject("downloadSettings")
        assertEquals("entry", download.getAsJsonObject("sockopt").get("dialerProxy").asString)
        assertTrue(download.getAsJsonObject("sockopt").get("tcpFastOpen").asBoolean)
        assertEquals(extra, profile.xhttpExtra)
    }

    @Test
    fun globalFragmentStaysOnPhysicalDialerAndExplicitMasksArePreserved() {
        val mask = JsonParser.parseString("""{"tcp":[{"type":"fragment"}]}""")
        val outbound = OutboundBean(protocol = "vless", streamSettings = OutboundBean.StreamSettingsBean(finalmask = mask))
        CoreOutboundBuilder.applyChainDialer(outbound, a.profile, "entry")
        assertNull(outbound.streamSettings!!.finalmask)
        outbound.streamSettings!!.finalmask = mask
        CoreOutboundBuilder.applyChainDialer(outbound, a.profile.copy(finalMask = mask.toString()), "entry")
        assertEquals(mask, outbound.streamSettings!!.finalmask)
    }
}
