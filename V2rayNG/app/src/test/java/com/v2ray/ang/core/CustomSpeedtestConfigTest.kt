package com.v2ray.ang.core

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomSpeedtestConfigTest {

    @Test
    fun keepsOnlyLogAndOutboundsWithoutMux() {
        val raw = """
            {
              "log": {"loglevel": "debug", "access": "/x"},
              "inbounds": [{"tag": "socks", "port": 10808, "protocol": "socks"}],
              "dns": {"servers": ["1.1.1.1"]},
              "routing": {"rules": [{"outboundTag": "direct", "domain": ["geosite:cn"]}]},
              "observatory": {"subjectSelector": ["proxy"]},
              "outbounds": [
                {"tag": "proxy", "protocol": "vless", "mux": {"enabled": true},
                 "settings": {"vnext": [{"address": "a.example", "port": 443}]}},
                {"tag": "direct", "protocol": "freedom"}
              ]
            }
        """.trimIndent()

        val trimmed = CoreConfigManager.trimCustomConfigForSpeedtest(raw, "warning")!!
        val json = JsonParser.parseString(trimmed).asJsonObject

        assertEquals(setOf("log", "outbounds"), json.keySet())
        assertEquals("warning", json.getAsJsonObject("log").get("loglevel").asString)
        val outbounds = json.getAsJsonArray("outbounds")
        assertEquals(2, outbounds.size())
        val proxy = outbounds[0].asJsonObject
        assertEquals("proxy", proxy.get("tag").asString)
        assertFalse(proxy.has("mux"))
        assertTrue(proxy.has("settings"))
        assertEquals("direct", outbounds[1].asJsonObject.get("tag").asString)
    }

    @Test
    fun rejectsConfigWithoutOutbounds() {
        assertNull(CoreConfigManager.trimCustomConfigForSpeedtest("""{"inbounds": []}""", "warning"))
        assertNull(CoreConfigManager.trimCustomConfigForSpeedtest("""{"outbounds": []}""", "warning"))
    }
}
