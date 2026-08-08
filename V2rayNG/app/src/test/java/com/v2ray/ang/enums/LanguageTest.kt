package com.v2ray.ang.enums

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageTest {

    @Test
    fun `maps BCP 47 locale tags to persisted language values`() {
        val expected = mapOf(
            null to Language.AUTO,
            "" to Language.AUTO,
            "en-US" to Language.ENGLISH,
            "zh-CN" to Language.CHINA,
            "zh-Hans" to Language.CHINA,
            "zh-TW" to Language.TRADITIONAL_CHINESE,
            "zh-Hant" to Language.TRADITIONAL_CHINESE,
            "zh-HK" to Language.TRADITIONAL_CHINESE,
            "zh-MO" to Language.TRADITIONAL_CHINESE,
            "vi" to Language.VIETNAMESE,
            "ru-RU" to Language.RUSSIAN,
            "fa-IR" to Language.PERSIAN,
            "ar" to Language.ARABIC,
            "bn-BD" to Language.BANGLA,
            "bqi-IR" to Language.BAKHTIARI
        )

        expected.forEach { (tag, language) ->
            assertEquals(tag, language, Language.fromLanguageTag(tag))
        }
    }
}
