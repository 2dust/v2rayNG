package com.v2ray.ang.core

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboundProbeConfigBuilderTest {
    @Test
    fun namespacesOutboundsAndRemapsDialerChains() {
        val plan = OutboundProbeConfigBuilder.build(
            sources = listOf(
                OutboundProbeConfigBuilder.Source(
                    "normal-a",
                    """
                    {
                      "outbounds": [
                        {
                          "tag": "proxy",
                          "protocol": "freedom",
                          "streamSettings": {"sockopt": {"dialerProxy": "hop"}}
                        },
                        {"tag": "hop", "protocol": "freedom"}
                      ],
                      "routing": {"rules": []}
                    }
                    """.trimIndent(),
                ),
                OutboundProbeConfigBuilder.Source(
                    "normal-b",
                    """{"outbounds":[{"tag":"proxy","protocol":"freedom"}]}""",
                ),
            ),
            destination = "https://example.com/generate_204",
        )

        assertEquals(listOf("probe-0-proxy", "probe-1-proxy"), plan.outboundTags)
        assertEquals(
            listOf(listOf("probe-0-proxy"), listOf("probe-1-proxy")),
            plan.probeGroups,
        )
        val root = JsonParser.parseString(plan.content).asJsonObject
        val outbounds = root.getAsJsonArray("outbounds")
        assertEquals(3, outbounds.size())
        val firstSockopt = outbounds[0].asJsonObject
            .getAsJsonObject("streamSettings")
            .getAsJsonObject("sockopt")
        assertEquals("probe-0-hop", firstSockopt.get("dialerProxy").asString)
        assertEquals(0, root.getAsJsonObject("burstObservatory").getAsJsonArray("subjectSelector").size())
    }

    @Test
    fun retainsPrimaryPolicyBalancerAndMapsItsMembers() {
        val plan = OutboundProbeConfigBuilder.build(
            sources = listOf(
                OutboundProbeConfigBuilder.Source(
                    "policy-guid",
                    """
                    {
                      "outbounds": [
                        {"tag":"proxy-proxy-1-a","protocol":"freedom"},
                        {"tag":"proxy-proxy-2-b","protocol":"freedom"},
                        {"tag":"direct","protocol":"freedom"}
                      ],
                      "routing": {
                        "rules": [{"type":"field","network":"tcp,udp","balancerTag":"balancer-main"}],
                        "balancers": [{
                          "tag":"balancer-main",
                          "selector":["proxy-proxy-"],
                          "strategy":{"type":"leastPing"}
                        }]
                      }
                    }
                    """.trimIndent(),
                )
            ),
            destination = "https://example.com/generate_204",
        )

        assertTrue(plan.failedGuids.isEmpty())
        assertEquals("probe-0-balancer-main", plan.profiles.single().balancerTag)
        assertEquals(
            listOf("proxy-proxy-1-a", "proxy-proxy-2-b"),
            plan.profiles.single().candidates.map { it.runtimeTag },
        )
        assertEquals(
            listOf(listOf("probe-0-proxy-proxy-1-a", "probe-0-proxy-proxy-2-b")),
            plan.probeGroups,
        )
        val balancer = JsonParser.parseString(plan.content).asJsonObject
            .getAsJsonObject("routing")
            .getAsJsonArray("balancers")[0].asJsonObject
        assertEquals("probe-0-proxy-proxy-", balancer.getAsJsonArray("selector")[0].asString)
    }

    @Test
    fun preservesLeastLoadProbeRequirementsForTheWholeBatch() {
        val plan = OutboundProbeConfigBuilder.build(
            sources = listOf(
                OutboundProbeConfigBuilder.Source(
                    "policy-guid",
                    """
                    {
                      "outbounds": [
                        {"tag":"proxy-a","protocol":"freedom"},
                        {"tag":"proxy-b","protocol":"freedom"}
                      ],
                      "routing": {
                        "rules": [{"type":"field","balancerTag":"balancer-main"}],
                        "balancers": [{
                          "tag":"balancer-main",
                          "selector":["proxy-"],
                          "strategy":{"type":"leastLoad"}
                        }]
                      },
                      "burstObservatory": {
                        "subjectSelector":["proxy-"],
                        "pingConfig": {
                          "destination":"https://ignored.example",
                          "httpMethod":"HEAD",
                          "interval":"5m",
                          "sampling":3,
                          "timeout":"30s"
                        }
                      }
                    }
                    """.trimIndent(),
                ),
                OutboundProbeConfigBuilder.Source(
                    "normal-guid",
                    """{"outbounds":[{"tag":"proxy","protocol":"freedom"}]}""",
                ),
            ),
            destination = "https://example.com/generate_204",
        )

        assertEquals(3, plan.samples)
        val pingConfig = JsonParser.parseString(plan.content).asJsonObject
            .getAsJsonObject("burstObservatory")
            .getAsJsonObject("pingConfig")
        assertEquals("HEAD", pingConfig.get("httpMethod").asString)
        assertEquals("30s", pingConfig.get("timeout").asString)
        assertEquals(3, pingConfig.get("sampling").asInt)
        assertEquals("https://example.com/generate_204", pingConfig.get("destination").asString)
    }

    @Test
    fun rejectsPolicyStrategiesWithoutOneStableViableTarget() {
        val plan = OutboundProbeConfigBuilder.build(
            sources = listOf(
                OutboundProbeConfigBuilder.Source(
                    "random-policy",
                    """
                    {
                      "outbounds": [
                        {"tag":"proxy-a","protocol":"freedom"},
                        {"tag":"proxy-b","protocol":"freedom"}
                      ],
                      "routing": {
                        "rules": [{"type":"field","balancerTag":"balancer-main"}],
                        "balancers": [{
                          "tag":"balancer-main",
                          "selector":["proxy-"],
                          "strategy":{"type":"random"}
                        }]
                      }
                    }
                    """.trimIndent(),
                )
            ),
            destination = "https://example.com/generate_204",
        )

        assertEquals(listOf("random-policy"), plan.failedGuids)
        assertTrue(plan.profiles.isEmpty())
        assertEquals(0, JsonParser.parseString(plan.content).asJsonObject.getAsJsonArray("outbounds").size())
    }

    @Test
    fun rejectsAmbiguousOrBrokenSourceReferencesBeforeMerge() {
        val plan = OutboundProbeConfigBuilder.build(
            sources = listOf(
                OutboundProbeConfigBuilder.Source(
                    "duplicate-tags",
                    """{"outbounds":[
                      {"tag":"proxy","protocol":"freedom"},
                      {"tag":"proxy","protocol":"freedom"}
                    ]}""",
                ),
                OutboundProbeConfigBuilder.Source(
                    "missing-dialer",
                    """{"outbounds":[{
                      "tag":"proxy",
                      "protocol":"freedom",
                      "streamSettings":{"sockopt":{"dialerProxy":"missing"}}
                    }]}""",
                ),
                OutboundProbeConfigBuilder.Source(
                    "conditional-route",
                    """{
                      "outbounds":[{"tag":"proxy","protocol":"freedom"}],
                      "routing":{"rules":[{
                        "type":"field",
                        "domain":["example.com"],
                        "outboundTag":"proxy"
                      }]}
                    }""",
                ),
            ),
            destination = "https://example.com/generate_204",
        )

        assertEquals(
            listOf("duplicate-tags", "missing-dialer", "conditional-route"),
            plan.failedGuids,
        )
        assertTrue(plan.profiles.isEmpty())
        assertEquals(0, JsonParser.parseString(plan.content).asJsonObject.getAsJsonArray("outbounds").size())
    }

    @Test
    fun isolatesMalformedProfilesFromTheViableBatch() {
        val plan = OutboundProbeConfigBuilder.build(
            sources = listOf(
                OutboundProbeConfigBuilder.Source("broken", "{}"),
                OutboundProbeConfigBuilder.Source(
                    "valid",
                    """{"outbounds":[{"tag":"proxy","protocol":"freedom"}]}""",
                ),
            ),
            destination = "https://example.com/generate_204",
        )

        assertEquals(listOf("broken"), plan.failedGuids)
        assertEquals(listOf("valid"), plan.profiles.map { it.guid })
        assertFalse(plan.content.isBlank())
    }
}
