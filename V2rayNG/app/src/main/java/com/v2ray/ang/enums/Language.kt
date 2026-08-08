package com.v2ray.ang.enums

import androidx.core.os.LocaleListCompat
import java.util.Locale

enum class Language(val code: String, val languageTag: String) {
    AUTO("auto", ""),
    ENGLISH("en", "en"),
    CHINA("zh-rCN", "zh-CN"),
    TRADITIONAL_CHINESE("zh-rTW", "zh-TW"),
    VIETNAMESE("vi", "vi"),
    RUSSIAN("ru", "ru"),
    PERSIAN("fa", "fa"),
    ARABIC("ar", "ar"),
    BANGLA("bn", "bn"),
    BAKHTIARI("bqi-rIR", "bqi-IR");

    val locale: Locale?
        get() = languageTag.takeIf { it.isNotEmpty() }?.let(Locale::forLanguageTag)

    fun toLocaleList(): LocaleListCompat =
        if (languageTag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }

    companion object {
        fun fromCode(code: String): Language {
            return entries.find { it.code == code } ?: AUTO
        }

        fun fromLanguageTag(languageTag: String?): Language {
            if (languageTag.isNullOrBlank()) return AUTO

            val locale = Locale.forLanguageTag(languageTag)
            return when (locale.language) {
                "en" -> ENGLISH
                "zh" -> if (
                    locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")
                ) {
                    TRADITIONAL_CHINESE
                } else {
                    CHINA
                }
                "vi" -> VIETNAMESE
                "ru" -> RUSSIAN
                "fa" -> PERSIAN
                "ar" -> ARABIC
                "bn" -> BANGLA
                "bqi" -> BAKHTIARI
                else -> AUTO
            }
        }
    }
}
