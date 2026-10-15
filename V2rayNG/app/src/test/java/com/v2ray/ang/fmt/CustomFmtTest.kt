package com.v2ray.ang.fmt

import android.util.Base64
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import java.util.Base64 as JavaBase64

/**
 * Unit tests for CustomFmt.
 *
 * Tests cover bare outbound detection, the wrapper that turns a single
 * outbound into a complete core configuration, and profile extraction.
 */
class CustomFmtTest {

    private lateinit var mockLog: MockedStatic<Log>
    private lateinit var mockBase64: MockedStatic<Base64>

    @BeforeEach
    fun setUp() {
        mockLog = mockStatic(Log::class.java, Mockito.RETURNS_DEFAULTS)
        reset(settings)
        whenever(settings.decodeString(any())).thenReturn(null)

        mockBase64 = mockStatic(Base64::class.java)
        mockBase64.`when`<ByteArray> {
            Base64.decode(Mockito.anyString(), Mockito.anyInt())
        }.thenAnswer { invocation ->
            val input = invocation.arguments[0] as String
            val flags = invocation.arguments[1] as Int
            val isUrlSafe = (flags and Base64.URL_SAFE) != 0
            val noPadding = (flags and Base64.NO_PADDING) != 0

            val decoder = if (isUrlSafe) {
                JavaBase64.getUrlDecoder()
            } else {
                JavaBase64.getDecoder()
            }
            val padded = if (noPadding) {
                input.padEnd((input.length + 3) / 4 * 4, '=')
            } else {
                input
            }
            decoder.decode(padded)
        }
        mockBase64.`when`<String> {
            Base64.encodeToString(Mockito.any(ByteArray::class.java), Mockito.anyInt())
        }.thenAnswer { invocation ->
            val input = invocation.arguments[0] as ByteArray
            val flags = invocation.arguments[1] as Int

            var encoder = if ((flags and Base64.URL_SAFE) != 0) {
                JavaBase64.getUrlEncoder()
            } else {
                JavaBase64.getEncoder()
            }
            if ((flags and Base64.NO_PADDING) != 0) {
                encoder = encoder.withoutPadding()
            }
            encoder.encodeToString(input)
        }
    }

    @AfterEach
    fun tearDown() {
        mockBase64.close()
        mockLog.close()
    }

    private fun outboundJson(
        protocol: String = "vless",
        tag: String? = "my-out",
        address: String = "example.com",
        port: Int = 443
    ): String {
        val outbound = JsonObject().apply {
            addProperty("protocol", protocol)
            tag?.let { addProperty("tag", it) }
            add(
                "settings", JsonObject().apply {
                    addProperty("address", address)
                    addProperty("port", port)
                    addProperty("id", "11111111-2222-3333-4444-555555555555")
                    addProperty("encryption", "none")
                }
            )
        }
        return JsonUtil.toJson(outbound)
    }

    private fun fullConfigJson(): String = JsonUtil.toJson(
        JsonObject().apply {
            addProperty("remarks", "Complete")
            add("inbounds", JsonArray())
            add(
                "outbounds", JsonArray().apply {
                    add(
                        JsonObject().apply {
                            addProperty("tag", "proxy")
                            addProperty("protocol", "vmess")
                            add(
                                "settings", JsonObject().apply {
                                    addProperty("address", "1.2.3.4")
                                    addProperty("port", 10086)
                                }
                            )
                        }
                    )
                }
            )
            add(
                "routing", JsonObject().apply {
                    addProperty("domainStrategy", "AsIs")
                    add("rules", JsonArray())
                }
            )
        }
    )

    // ==================== isOutboundObject ====================

    @Test
    fun test_isOutboundObject_trueForBareOutbound() {
        val json = JsonUtil.parseString(outboundJson())!!

        assertTrue(CustomFmt.isOutboundObject(json))
    }

    @Test
    fun test_isOutboundObject_falseForCompleteConfig() {
        val json = JsonUtil.parseString(fullConfigJson())!!

        assertFalse(CustomFmt.isOutboundObject(json))
    }

    @Test
    fun test_isOutboundObject_falseWithoutProtocol() {
        val json = JsonUtil.parseString("""{"tag":"no-protocol","settings":{}}""")!!

        assertFalse(CustomFmt.isOutboundObject(json))
    }

    @Test
    fun test_isOutboundObject_falseForBlankProtocol() {
        val json = JsonUtil.parseString("""{"protocol":"   "}""")!!

        assertFalse(CustomFmt.isOutboundObject(json))
    }

    @Test
    fun test_isOutboundObject_falseForNonStringProtocol() {
        val json = JsonUtil.parseString("""{"protocol":7}""")!!

        assertFalse(CustomFmt.isOutboundObject(json))
    }

    // ==================== isOutboundJson ====================

    @Test
    fun test_isOutboundJson_trueForOutbound() {
        assertTrue(CustomFmt.isOutboundJson(outboundJson()))
    }

    @Test
    fun test_isOutboundJson_falseForNonJson() {
        assertFalse(CustomFmt.isOutboundJson("vmess://not-json"))
    }

    // ==================== normalize ====================

    @Test
    fun test_normalize_returnsNullForNonJson() {
        assertNull(CustomFmt.normalize("not json at all"))
    }

    @Test
    fun test_normalize_keepsCompleteConfigUnchanged() {
        val input = fullConfigJson()

        assertEquals(input, CustomFmt.normalize(input))
    }

    @Test
    fun test_normalize_wrapsBareOutboundIntoCompleteConfig() {
        val normalized = CustomFmt.normalize(outboundJson())
        assertNotNull(normalized)

        val json = JsonUtil.parseString(normalized!!)!!
        assertTrue(json.has("inbounds"))
        assertTrue(json.has("outbounds"))
        assertTrue(json.has("routing"))
        assertTrue(json.get("routing").asJsonObject.has("rules"))
    }

    @Test
    fun test_normalize_preservesOutboundSettingsAndStreamSettings() {
        val outbound = JsonObject().apply {
            addProperty("protocol", "trojan")
            addProperty("tag", "my-out")
            add("settings", JsonObject().apply { addProperty("password", "secret") })
            add(
                "streamSettings", JsonObject().apply {
                    addProperty("network", "ws")
                    addProperty("security", "tls")
                }
            )
        }

        val json = JsonUtil.parseString(CustomFmt.normalize(JsonUtil.toJson(outbound))!!)!!
        val wrapped = json.getAsJsonArray("outbounds")[0].asJsonObject

        assertEquals("trojan", wrapped.get("protocol").asString)
        assertEquals("my-out", wrapped.get("tag").asString)
        assertEquals("secret", wrapped.getAsJsonObject("settings").get("password").asString)
        assertEquals(
            "ws",
            wrapped.getAsJsonObject("streamSettings").get("network").asString
        )
    }

    @Test
    fun test_normalize_defaultsTagToProxy() {
        val json = JsonUtil.parseString(CustomFmt.normalize(outboundJson(tag = null))!!)!!
        val wrapped = json.getAsJsonArray("outbounds")[0].asJsonObject

        assertEquals("proxy", wrapped.get("tag").asString)
    }

    @Test
    fun test_normalize_blankTagFallsBackToProxy() {
        val json = JsonUtil.parseString(CustomFmt.normalize(outboundJson(tag = "  "))!!)!!
        val wrapped = json.getAsJsonArray("outbounds")[0].asJsonObject

        assertEquals("proxy", wrapped.get("tag").asString)
    }

    @Test
    fun test_normalize_keepsImportedOutboundFirst() {
        val json = JsonUtil.parseString(CustomFmt.normalize(outboundJson())!!)!!
        val outbounds = json.getAsJsonArray("outbounds")

        assertEquals("vless", outbounds[0].asJsonObject.get("protocol").asString)
    }

    @Test
    fun test_normalize_isIdempotent() {
        val once = CustomFmt.normalize(outboundJson())!!

        assertEquals(once, CustomFmt.normalize(once))
    }

    @Test
    fun test_normalize_reducesMultiVnextToOne() {
        val multiVnext = JsonObject().apply {
            addProperty("protocol", "vless")
            addProperty("tag", "multi")
            add(
                "settings", JsonObject().apply {
                    val vnext = JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("address", "server1.example.com")
                            addProperty("port", 443)
                            add("users", JsonArray().apply {
                                add(JsonObject().apply {
                                    addProperty("id", "11111111-2222-3333-4444-555555555555")
                                    addProperty("encryption", "none")
                                })
                            })
                        })
                        add(JsonObject().apply {
                            addProperty("address", "server2.example.com")
                            addProperty("port", 443)
                            add("users", JsonArray().apply {
                                add(JsonObject().apply {
                                    addProperty("id", "11111111-2222-3333-4444-555555555555")
                                    addProperty("encryption", "none")
                                })
                            })
                        })
                    }
                    add("vnext", vnext)
                }
            )
        }
        val result = CustomFmt.normalize(JsonUtil.toJson(multiVnext))!!
        val json = JsonUtil.parseString(result)!!
        val vnext = json.getAsJsonArray("outbounds")[0].asJsonObject.getAsJsonObject("settings").getAsJsonArray("vnext")
        assertEquals(1, vnext.size())
    }

    @Test
    fun test_normalize_reducesMultiVnextToOneInFullConfig() {
        val fullConfig = JsonObject().apply {
            addProperty("remarks", "multi-vless")
            add("inbounds", JsonArray())
            add(
                "outbounds", JsonArray().apply {
                    add(
                        JsonObject().apply {
                            addProperty("tag", "proxy")
                            addProperty("protocol", "vless")
                            add(
                                "settings", JsonObject().apply {
                                    val vnext = JsonArray().apply {
                                        add(JsonObject().apply {
                                            addProperty("address", "s1.example.com")
                                            addProperty("port", 443)
                                            add("users", JsonArray().apply {
                                                add(JsonObject().apply {
                                                    addProperty("id", "11111111-2222-3333-4444-555555555555")
                                                    addProperty("encryption", "none")
                                                })
                                            })
                                        })
                                        add(JsonObject().apply {
                                            addProperty("address", "s2.example.com")
                                            addProperty("port", 443)
                                            add("users", JsonArray().apply {
                                                add(JsonObject().apply {
                                                    addProperty("id", "11111111-2222-3333-4444-555555555555")
                                                    addProperty("encryption", "none")
                                                })
                                            })
                                        })
                                    }
                                    add("vnext", vnext)
                                }
                            )
                        }
                    )
                }
            )
            add(
                "routing", JsonObject().apply {
                    addProperty("domainStrategy", "AsIs")
                    add("rules", JsonArray())
                }
            )
        }
        val result = CustomFmt.normalize(JsonUtil.toJson(fullConfig))!!
        val json = JsonUtil.parseString(result)!!
        val vnext = json.getAsJsonArray("outbounds")[0].asJsonObject.getAsJsonObject("settings").getAsJsonArray("vnext")
        assertEquals(1, vnext.size())
    }

    @Test
    fun test_simplifyOutboundEndpoints_keepsFirstVnextMember() {
        val outbound = JsonObject().apply {
            addProperty("protocol", "vless")
            add(
                "settings", JsonObject().apply {
                    add(
                        "vnext", JsonArray().apply {
                            add(JsonObject().apply {
                                addProperty("address", "first.example.com")
                                addProperty("port", 443)
                            })
                            add(JsonObject().apply {
                                addProperty("address", "second.example.com")
                                addProperty("port", 443)
                            })
                        }
                    )
                }
            )
        }

        CustomFmt.simplifyOutboundEndpoints(outbound)

        val vnext = outbound.getAsJsonObject("settings").getAsJsonArray("vnext")
        assertEquals(1, vnext.size())
        assertEquals("first.example.com", vnext[0].asJsonObject.get("address").asString)
    }

    @Test
    fun test_simplifyOutboundEndpoints_ignoresSingleEndpoint() {
        val outbound = JsonObject().apply {
            addProperty("protocol", "vless")
            add(
                "settings", JsonObject().apply {
                    add(
                        "vnext", JsonArray().apply {
                            add(JsonObject().apply {
                                addProperty("address", "only.example.com")
                                addProperty("port", 443)
                            })
                        }
                    )
                }
            )
        }

        CustomFmt.simplifyOutboundEndpoints(outbound)

        val vnext = outbound.getAsJsonObject("settings").getAsJsonArray("vnext")
        assertEquals(1, vnext.size())
        assertEquals("only.example.com", vnext[0].asJsonObject.get("address").asString)
    }

    @Test
    fun test_outboundBean_roundTripKeepsVnextForCustomOutbound() {
        // Mirrors CoreOutboundBuilder.convert: a stored outbound object is
        // deserialized into the typed DTO and re-serialized for the core.
        val stored = JsonObject().apply {
            addProperty("protocol", "vless")
            addProperty("tag", "proxy")
            add(
                "settings", JsonObject().apply {
                    add(
                        "vnext", JsonArray().apply {
                            add(
                                JsonObject().apply {
                                    addProperty("address", "vpn.example.com")
                                    addProperty("port", 8443)
                                    add(
                                        "users", JsonArray().apply {
                                            add(
                                                JsonObject().apply {
                                                    addProperty(
                                                        "id",
                                                        "11111111-2222-3333-4444-555555555555"
                                                    )
                                                    addProperty("encryption", "none")
                                                }
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }

        val bean = JsonUtil.fromJson(JsonUtil.toJson(stored), V2rayConfig.OutboundBean::class.java)
        assertNotNull(bean)

        val settings = JsonUtil.parseString(JsonUtil.toJsonPretty(bean))!!
            .getAsJsonObject("settings")
        val vnext = settings.getAsJsonArray("vnext")
        assertEquals(1, vnext.size())
        assertEquals("vpn.example.com", vnext[0].asJsonObject.get("address").asString)
        assertEquals(8443, vnext[0].asJsonObject.get("port").asInt)
    }

    // ==================== toUri ====================

    @Test
    fun test_toUri_producesV2raynLinkCarryingTheOutbound() {
        val profile = ProfileItem.create(EConfigType.CUSTOM_OUTBOUND).apply {
            remarks = "my-out"
            server = "vpn.example.com"
            serverPort = "8443"
        }
        val raw = requireNotNull(CustomFmt.normalize(outboundJson(address = "vpn.example.com", port = 8443)))

        val uri = CustomFmt.toUri(profile, raw)

        assertTrue(uri.startsWith(AppConfig.V2RAYNFMTS))
        val payload = requireNotNull(JsonUtil.parseString(Utils.decode(uri.substringAfterLast('/')).trim()))
        assertEquals(
            "8443",
            payload.asJsonObject.getAsJsonObject("CustomOutboundObj")
                .getAsJsonObject("settings").get("port").asInt.toString()
        )
    }

    @Test
    fun test_toUri_isEmptyWhenRawIsBlank() {
        val profile = ProfileItem.create(EConfigType.CUSTOM_OUTBOUND)

        assertEquals("", CustomFmt.toUri(profile, ""))
    }

    @Test
    fun test_toUri_survivesRoundTripImport() {
        val vless = """{"protocol":"vless","tag":"my-out","settings":{"vnext":[{"address":"vpn.example.com","port":8443,"users":[{"id":"11111111-2222-3333-4444-555555555555","encryption":"none"}]}]}}"""
        val stored = requireNotNull(CustomFmt.normalize(vless))
        val profile = ProfileItem.create(EConfigType.CUSTOM_OUTBOUND).apply { remarks = "my-out" }

        val shared = CustomFmt.toUri(profile, stored)

        // Reproduces what the importer reads out of a v2rayn:// link.
        val payload = Utils.decode(shared.substringAfterLast('/')).trim()
        val outboundJson = JsonUtil.parseString(payload)!!
            .getAsJsonObject("CustomOutboundObj")
            .let { if (it.isJsonObject) it.toString() else it.asString }

        val config = CustomFmt.parse(outboundJson, EConfigType.CUSTOM_OUTBOUND)
        assertEquals("vpn.example.com", config.server)
        assertEquals("8443", config.serverPort)
    }

    // ==================== parse ====================

    @Test
    fun test_parse_outboundObjectReadsAddressAndPort() {
        val config = CustomFmt.parse(outboundJson(address = "vpn.example.com", port = 8443))

        assertEquals("my-out", config.remarks)
        assertEquals("vpn.example.com", config.server)
        assertEquals("8443", config.serverPort)
    }

    @Test
    fun test_parse_readsVLESSAddressFromVnext() {
        val vless = """{"protocol":"vless","tag":"my-out","settings":{"vnext":[{"address":"vpn.example.com","port":8443,"users":[{"id":"11111111-2222-3333-4444-555555555555","encryption":"none"}]}]}}"""

        val config = CustomFmt.parse(vless, EConfigType.CUSTOM_OUTBOUND)

        assertEquals("vpn.example.com", config.server)
        assertEquals("8443", config.serverPort)
    }

    @Test
    fun test_parse_readsTrojanAddressFromServers() {
        val trojan = """{"protocol":"trojan","tag":"my-out","settings":{"servers":[{"address":"vpn.example.com","port":8443,"password":"secret"}]}}"""

        val config = CustomFmt.parse(trojan, EConfigType.CUSTOM_OUTBOUND)

        assertEquals("vpn.example.com", config.server)
        assertEquals("8443", config.serverPort)
    }

    @Test
    fun test_parse_outboundObjectUsesTagAsRemarks() {
        val config = CustomFmt.parse(outboundJson(tag = null))

        assertEquals("proxy", config.remarks)
    }

    @Test
    fun test_parse_completeConfigReadsAddressAndPort() {
        val config = CustomFmt.parse(fullConfigJson())

        assertEquals("Complete", config.remarks)
        assertEquals("1.2.3.4", config.server)
        assertEquals("10086", config.serverPort)
    }

    companion object {
        private val settings: MMKV = mock()

        @BeforeAll
        @JvmStatic
        fun initializeStorage() {
            // LogUtil reads the log level from MMKV when a parse failure is logged.
            mockStatic(MMKV::class.java).use {
                it.`when`<MMKV> { MMKV.mmkvWithID("SETTING", MMKV.MULTI_PROCESS_MODE) }
                    .thenReturn(settings)
                MmkvManager.decodeSettingsString("test-initialize")
            }
        }
    }
}
