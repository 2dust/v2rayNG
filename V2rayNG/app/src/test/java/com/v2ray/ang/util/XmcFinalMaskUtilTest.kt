package com.v2ray.ang.util

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XmcFinalMaskUtilTest {

    @Test
    fun extractReadsXmcSettings() {
        val finalMask = """
            {
              "tcp": [
                {
                  "type": "xmc",
                  "settings": {
                    "hostname": "mc.example.com",
                    "usernames": ["Steve", "Alex"],
                    "password": "secret"
                  }
                }
              ]
            }
        """.trimIndent()

        assertEquals(
            XmcFinalMaskUtil.Settings(
                hostname = "mc.example.com",
                usernames = listOf("Steve", "Alex"),
                password = "secret",
            ),
            XmcFinalMaskUtil.extract(finalMask),
        )
    }

    @Test
    fun updateAddsXmcAndPreservesOtherMasks() {
        val finalMask = """
            {
              "tcp": [{"type":"fragment","settings":{"packets":"tlshello"}}],
              "quicParams": {"congestion":"bbr"}
            }
        """.trimIndent()

        val updated = XmcFinalMaskUtil.update(
            finalMask,
            XmcFinalMaskUtil.Settings(
                hostname = "mc.example.com",
                usernames = listOf("Steve", "Alex"),
                password = "secret",
            ),
        )
        val root = JsonParser.parseString(updated).asJsonObject
        val tcp = root.getAsJsonArray("tcp")

        assertEquals(2, tcp.size())
        assertEquals("fragment", tcp[0].asJsonObject.get("type").asString)
        assertEquals("xmc", tcp[1].asJsonObject.get("type").asString)
        assertEquals("bbr", root.getAsJsonObject("quicParams").get("congestion").asString)
    }

    @Test
    fun updateReplacesDuplicateXmcMasksAtOriginalPosition() {
        val finalMask = """
            {
              "tcp": [
                {"type":"xmc","settings":{"password":"old-1"}},
                {"type":"fragment","settings":{"packets":"tlshello"}},
                {"type":"xmc","settings":{"password":"old-2"}}
              ]
            }
        """.trimIndent()

        val updated = XmcFinalMaskUtil.update(
            finalMask,
            XmcFinalMaskUtil.Settings(password = "new-password"),
        )
        val tcp = JsonParser.parseString(updated).asJsonObject.getAsJsonArray("tcp")

        assertEquals(2, tcp.size())
        assertEquals("xmc", tcp[0].asJsonObject.get("type").asString)
        assertEquals(
            "new-password",
            tcp[0].asJsonObject.getAsJsonObject("settings").get("password").asString,
        )
        assertEquals("fragment", tcp[1].asJsonObject.get("type").asString)
    }

    @Test
    fun updateRemovesOnlyXmc() {
        val withOtherMask = """{"tcp":[{"type":"xmc","settings":{"password":"secret"}},{"type":"fragment"}]}"""
        val withoutOtherMask = """{"tcp":[{"type":"xmc","settings":{"password":"secret"}}]}"""

        val retained = JsonParser.parseString(XmcFinalMaskUtil.update(withOtherMask, null)).asJsonObject
        assertEquals("fragment", retained.getAsJsonArray("tcp")[0].asJsonObject.get("type").asString)
        assertFalse(retained.getAsJsonArray("tcp").any { it.asJsonObject.get("type").asString == "xmc" })
        assertNull(XmcFinalMaskUtil.update(withoutOtherMask, null))
    }

    @Test
    fun updateKeepsInvalidRawJsonUnchanged() {
        val invalid = "{invalid"

        assertEquals(
            invalid,
            XmcFinalMaskUtil.update(invalid, XmcFinalMaskUtil.Settings(password = "secret")),
        )
        assertNull(XmcFinalMaskUtil.extract(invalid))
    }

    @Test
    fun updateOmitsBlankOptionalFields() {
        val updated = XmcFinalMaskUtil.update(
            null,
            XmcFinalMaskUtil.Settings(
                hostname = "  ",
                usernames = listOf(" "),
                password = "secret",
            ),
        )
        val settings = JsonParser.parseString(updated).asJsonObject
            .getAsJsonArray("tcp")[0].asJsonObject
            .getAsJsonObject("settings")

        assertFalse(settings.has("hostname"))
        assertFalse(settings.has("usernames"))
        assertTrue(settings.has("password"))
    }
}
