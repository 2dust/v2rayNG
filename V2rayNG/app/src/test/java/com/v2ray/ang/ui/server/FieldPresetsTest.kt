package com.v2ray.ang.ui.server

import com.v2ray.ang.AppResources
import com.v2ray.ang.core.AetherExit
import com.v2ray.ang.ui.server.FieldPresets.Match
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The ready-made values the finalMask and cipherSuites fields offer: which one a field holds, whether a pick asks before
 * it takes the field's place, and the lists themselves as the app's resources give them, read from the module's folder
 * the unit tests run in.
 */
class FieldPresetsTest {

    private val fragment = """{"tcp": [{"type": "fragment", "settings": {"packets": "tlshello"}}]}"""
    private val noise = """{"udp": [{"type": "noise", "settings": {"noise": [{"rand": "10-20", "delay": "10"}]}}]}"""
    private val masks = FieldPresets(listOf(fragment, noise), Match.JSON)
    private val ecdsa = "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384"
    private val rsa = "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"
    private val chacha = "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256"
    private val suites = FieldPresets(listOf("$ecdsa:$rsa", chacha), Match.NAMES)

    @Test
    fun aFinalMaskFieldHoldsOneOfTheListInWhateverSpacingOrKeyOrder() {
        assertEquals(0, masks.indexOf(fragment))
        assertEquals(1, masks.indexOf(noise))
        assertEquals(1, masks.indexOf(noise.replace(" ", "")))
        assertEquals(0, masks.indexOf("{\n  \"tcp\": [\n    {\"settings\": {\"packets\": \"tlshello\"}, \"type\": \"fragment\"}\n  ]\n}"))
    }

    @Test
    fun aFinalMaskFieldThatIsBlankEditedOrNotJsonHoldsNoneOfThem() {
        assertEquals(-1, masks.indexOf(""))
        assertEquals(-1, masks.indexOf(" \n "))
        assertEquals(-1, masks.indexOf(fragment.replace("tlshello", "1-3")))
        assertEquals(-1, masks.indexOf("""{"tcp": [{"type": "fragment""""))
        assertEquals(-1, masks.indexOf("[]"))
        // The masks run in the order they are written, so the same two in the other order are another finalMask.
        val both = FieldPresets(listOf("""{"tcp": [{"type": "a"}, {"type": "b"}]}"""), Match.JSON)
        assertEquals(0, both.indexOf("""{"tcp": [{"type": "a"}, {"type": "b"}]}"""))
        assertEquals(-1, both.indexOf("""{"tcp": [{"type": "b"}, {"type": "a"}]}"""))
    }

    @Test
    fun aCipherSuitesFieldHoldsOneOfTheListWhenItHasItsNamesInItsOrder() {
        assertEquals(0, suites.indexOf("$ecdsa:$rsa"))
        assertEquals(1, suites.indexOf(chacha))
        // Xray splits the names on ':' and trims each, and skips an empty one.
        assertEquals(0, suites.indexOf(" $ecdsa :\n$rsa\n"))
        assertEquals(0, suites.indexOf("$ecdsa::$rsa:"))
        assertEquals(1, suites.indexOf("$chacha\n"))
    }

    @Test
    fun aCipherSuitesFieldWithOtherNamesOrInAnotherOrderHoldsNoneOfThem() {
        assertEquals(-1, suites.indexOf("$rsa:$ecdsa"))
        assertEquals(-1, suites.indexOf("$ecdsa:$rsa:$chacha"))
        assertEquals(-1, suites.indexOf(ecdsa))
        assertEquals(-1, suites.indexOf("${ecdsa}X:$rsa"))
        assertEquals(-1, suites.indexOf(""))
        assertEquals(-1, suites.indexOf(" : \n"))
    }

    @Test
    fun aPickAsksFirstOnlyWhenItWouldTakeThePlaceOfAValueOfTheUsersOwn() {
        fun asks(presets: FieldPresets, text: String) = FieldPresets.asksBeforeReplacing(text, presets.heldOf(text))
        assertFalse(asks(masks, ""))
        assertFalse(asks(masks, "  "))
        assertFalse(asks(masks, noise))
        assertFalse(asks(masks, noise.replace(" ", "")))
        assertTrue(asks(masks, noise.replace("10-20", "30-40")))
        assertTrue(asks(masks, """{"tcp": ["""))
        assertFalse(asks(suites, ""))
        assertFalse(asks(suites, chacha))
        assertFalse(asks(suites, "$ecdsa : $rsa"))
        assertTrue(asks(suites, "$rsa:$ecdsa"))
        assertTrue(asks(suites, " : "))
    }

    @Test
    fun aPickAsksFirstWhileWhatTheFieldHoldsIsNotKnownYetUnlessItIsBlank() {
        // The fields match off the main thread, so a pick can come before the text the field holds now is matched.
        assertTrue(FieldPresets.asksBeforeReplacing(noise, null))
        assertTrue(FieldPresets.asksBeforeReplacing("$rsa:$ecdsa", null))
        assertFalse(FieldPresets.asksBeforeReplacing("", null))
        assertFalse(FieldPresets.asksBeforeReplacing(" \n ", null))
    }

    @Test
    fun theDefaultPickBlanksTheFieldAndEveryOtherPickPutsItsValue() {
        // A blank field leaves the setting at its default: no finalMask, Xray's own cipher suites.
        assertEquals("", masks.textOf(FieldPresets.DEFAULT))
        assertEquals("", suites.textOf(FieldPresets.DEFAULT))
        assertEquals(fragment, masks.textOf(0))
        assertEquals(chacha, suites.textOf(1))
        assertNull(masks.textOf(2))
    }

    @Test
    fun aBlankFieldHoldsTheDefaultPickAPresetItsIndexAndAnyOtherTextNone() {
        // The default is chosen until another pick is, so a new profile's field shows it. Neither it nor NONE can be an
        // index, and they differ, or a blank field and one of the user's own would show alike.
        assertTrue(FieldPresets.DEFAULT < 0 && FieldPresets.NONE < 0)
        assertNotEquals(FieldPresets.NONE, FieldPresets.DEFAULT)
        assertEquals(FieldPresets.DEFAULT, masks.heldOf(""))
        assertEquals(FieldPresets.DEFAULT, masks.heldOf(" \n "))
        assertEquals(FieldPresets.DEFAULT, suites.heldOf(""))
        assertEquals(1, masks.heldOf(noise.replace(" ", "")))
        assertEquals(0, suites.heldOf("$ecdsa : $rsa"))
        assertEquals(FieldPresets.NONE, masks.heldOf(noise.replace("10-20", "30-40")))
        assertEquals(FieldPresets.NONE, suites.heldOf(" : "))
        assertEquals(FieldPresets.NONE, masks.heldOf("""{"tcp": ["""))
        // Nothing of the user's own is lost when the field holds the default or a preset.
        assertFalse(FieldPresets.asksBeforeReplacing("", masks.heldOf("")))
        assertFalse(FieldPresets.asksBeforeReplacing(noise, masks.heldOf(noise)))
        assertTrue(FieldPresets.asksBeforeReplacing("$rsa:$ecdsa", suites.heldOf("$rsa:$ecdsa")))
    }

    @Test
    fun theFinalMaskFieldsOfferTheGivenFinalMasksEachLaidOutAsWrittenUnderItsName() {
        val names = AppResources.stringArray("final_mask_preset_names")
        val values = AppResources.stringArray("final_mask_preset_values").map(::readAsAapt)
        assertEquals(listOf("tlshello-0-len (0-104-1-0-0-114-1-1-11)", "tlshello (6-98-1-0-0-114-1-1-11)", "udp-noise (rnd-24-1200-1230)"), names)
        assertEquals(listOf(TLSHELLO_0_LEN, TLSHELLO, UDP_NOISE), values)
        // Each is one an exit-node takes, as an outbound does, and the list tells each apart from the others.
        val listed = FieldPresets(values, Match.JSON)
        values.forEachIndexed { index, value ->
            assertTrue(AetherExit.takesFinalMask(value), value)
            assertEquals(index, listed.indexOf(value))
        }
    }

    @Test
    fun theCipherSuitesFieldOffersTheGivenListsUnderTheirNames() {
        val names = AppResources.stringArray("cipher_suites_preset_names")
        val values = AppResources.stringArray("cipher_suites_preset_values").map(::readPlain)
        assertEquals(listOf("semi-python-cipherSuites", "real-firefox-cipherSuites"), names)
        assertEquals(listOf(SEMI_PYTHON, REAL_FIREFOX), values)
        val listed = FieldPresets(values, Match.NAMES)
        values.forEachIndexed { index, value -> assertEquals(index, listed.indexOf(value)) }
    }

    /**
     * [raw] as aapt reads a string resource put in quotes so that its spaces stay: the quotes go, and \n, \" and \\ are
     * undone. A quote left unescaped would end the string early, so it fails here.
     */
    private fun readAsAapt(raw: String): String {
        assertTrue(raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"'), raw)
        val text = StringBuilder()
        var escaped = false
        for (c in raw.substring(1, raw.length - 1)) {
            when {
                escaped -> {
                    text.append(if (c == 'n') '\n' else c)
                    escaped = false
                }
                c == '\\' -> escaped = true
                else -> {
                    assertFalse(c == '"', "an unescaped quote in $raw")
                    text.append(c)
                }
            }
        }
        assertFalse(escaped, raw)
        return text.toString()
    }

    /**
     * [raw] as aapt reads a string resource written without quotes: unchanged, as long as it has no white space, quote,
     * apostrophe or backslash, and starts with neither @ nor ?.
     */
    private fun readPlain(raw: String): String {
        assertTrue(raw.isNotEmpty() && raw.none { it.isWhitespace() || it in "\"'\\" } && raw.first() !in "@?", raw)
        return raw
    }

    private companion object {
        const val REAL_FIREFOX = "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256:TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256:TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256:TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256:TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384:TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384:TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA:TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA:TLS_RSA_WITH_AES_128_GCM_SHA256:TLS_RSA_WITH_AES_256_GCM_SHA384:TLS_RSA_WITH_AES_128_CBC_SHA:TLS_RSA_WITH_AES_256_CBC_SHA"

        const val SEMI_PYTHON = "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384:TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384:TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256:TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256:TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256:TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256:TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA:TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA:TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256:TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256"

        val TLSHELLO_0_LEN = """
            {
              "tcp": [
                {"type": "fragment", "settings": {"packets": "tlshello", "lengths": ["0", "104", "1"], "delays": ["0"], "maxSplit": "0"}},
                {"type": "fragment", "settings": {"packets": "1-1", "lengths": ["114", "1"], "delays": ["1"], "maxSplit": "11"}}
              ]
            }
        """.trimIndent()

        val TLSHELLO = """
            {
              "tcp": [
                {"type": "fragment", "settings": {"packets": "tlshello", "lengths": ["6", "98", "1"], "delays": ["0"], "maxSplit": "0"}},
                {"type": "fragment", "settings": {"packets": "1-1", "lengths": ["114", "1"], "delays": ["1"], "maxSplit": "11"}}
              ]
            }
        """.trimIndent()

        val UDP_NOISE = """
            {
              "udp": [
                {
                  "type": "noise",
                  "settings": {
                    "reset": "28",
                    "noise": [
                      {"rand": "1200-1230", "delay": "10"}, {"rand": "1200-1230", "delay": "10"}, {"rand": "1200-1230", "delay": "10"},
                      {"rand": "1200-1230", "delay": "10"}, {"rand": "1200-1230", "delay": "10"}, {"rand": "1200-1230", "delay": "10"},
                      {"rand": "1200-1230", "delay": "10"}, {"rand": "1200-1230", "delay": "10"}, {"rand": "1200-1230", "delay": "10"},
                      {"rand": "1200-1230", "delay": "10"}, {"rand": "1200-1230", "delay": "10"}, {"rand": "1200-1230", "delay": "10"},
                      {"rand": "1200-1230", "delay": "10"}, {"rand": "1200-1230", "delay": "10"}, {"rand": "1200-1230", "delay": "10"},
                      {"rand": "1200-1230", "delay": "10"}, {"rand": "1200-1230", "delay": "10"}, {"rand": "1200-1230", "delay": "10"},
                      {"rand": "1200-1230", "delay": "10"}, {"rand": "1200-1230", "delay": "10"}, {"rand": "1200-1230", "delay": "10"},
                      {"rand": "1200-1230", "delay": "10"}, {"rand": "1200-1230", "delay": "10"}, {"rand": "1200-1230", "delay": "10"}
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
    }
}
