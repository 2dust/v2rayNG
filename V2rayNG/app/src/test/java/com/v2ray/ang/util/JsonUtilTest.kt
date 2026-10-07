package com.v2ray.ang.util

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JsonUtilTest {

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
