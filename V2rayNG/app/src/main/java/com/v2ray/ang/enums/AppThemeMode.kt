package com.v2ray.ang.enums

enum class AppThemeMode(val value: String) {
    System("0"),
    Light("1"),
    Dark("2");

    companion object {
        fun from(raw: String?): AppThemeMode = entries.firstOrNull { it.value == raw } ?: System
    }
}
