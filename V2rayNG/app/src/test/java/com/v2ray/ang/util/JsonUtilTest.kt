package com.v2ray.ang.util

import com.google.gson.JsonParser
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JsonUtilTest {

    @Test
    fun fromJsonSafeReadsStoredProfile() {
        val storedJson = """
            {"configVersion":4,"configType":"VLESS","subscriptionId":"subscription-1",
             "addedTime":1700000000123,"remarks":"Stored <profile>","server":"example.com",
             "serverPort":"443","network":"xhttp","insecure":false,
             "xhttpExtra":"{\"headers\":{\"X-Test\":\"value\"}}"}
        """.trimIndent()

        val profile = requireNotNull(JsonUtil.fromJsonSafe(storedJson, ProfileItem::class.java))

        assertEquals(4, profile.configVersion)
        assertEquals(EConfigType.VLESS, profile.configType)
        assertEquals("subscription-1", profile.subscriptionId)
        assertEquals(1700000000123L, profile.addedTime)
        assertEquals("Stored <profile>", profile.remarks)
        assertEquals("example.com", profile.server)
        assertEquals("443", profile.serverPort)
        assertEquals("xhttp", profile.network)
        assertEquals(false, profile.insecure)
        assertEquals("""{"headers":{"X-Test":"value"}}""", profile.xhttpExtra)
        assertNull(profile.description)
        assertEquals(profile, JsonUtil.fromJsonSafe(JsonUtil.toJson(profile), ProfileItem::class.java))
    }

    @Test
    fun toJsonPrettyPreservesNestedConfigArrayElements() {
        val source = """
            [{"outbounds":[{"protocol":"socks","settings":{"servers":[{"address":"example.com","port":443}]}}],
              "metrics":{"enabled":true,"weight":0.4,"values":[true,null,0.4]}}]
        """.trimIndent()
        val configs = requireNotNull(JsonUtil.fromJson(source, Array<Any>::class.java))

        val output = JsonParser.parseString(JsonUtil.toJsonPretty(configs))
        val settings = output.asJsonArray[0].asJsonObject.getAsJsonArray("outbounds")[0]
            .asJsonObject.getAsJsonObject("settings")

        assertEquals(JsonParser.parseString(source), output)
        assertEquals("443", settings.getAsJsonArray("servers")[0].asJsonObject["port"].asString)
    }

    @Test
    fun toJsonPrettyKeepsNumbersOfConfigArrayElements() {
        val configs = JsonUtil.fromJson(
            """[{"port":443,"tolerance":0.4,"offset":-0.5,"timestamp":1700000000000,"name":"0.4"}]""",
            Array<Any>::class.java
        )

        val output = JsonParser.parseString(JsonUtil.toJsonPretty(configs!![0])).asJsonObject

        assertEquals("443", output["port"].asString)
        assertEquals("0.4", output["tolerance"].asString)
        assertEquals("-0.5", output["offset"].asString)
        assertEquals("1700000000000", output["timestamp"].asString)
        assertEquals("0.4", output["name"].asString)
    }
}
