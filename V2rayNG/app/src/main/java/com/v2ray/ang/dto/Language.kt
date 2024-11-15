package com.v2ray.ang.dto

enum class Language(val code: String) {
    AUTO("auto"),
    ENGLISH("en"),
    CHINA("zh-rCN"),
    TRADITIONAL_CHINESE("zh-rTW"),
    VIETNAMESE("vi"),
    RUSSIAN("ru"),
    PERSIAN("fa"),
    BANGLA("bn"),
    BAKHTIARI("bqi-rIR");

    companion object {
        fun fromCode(code: String): Language {
            return entries.find { it.code == code } ?: AUTO
        }
    }
}
