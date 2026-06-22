package com.v2ray.ang.core

/**
 * Helpers for optional fragment settings.
 */
object FragmentSettings {
    /**
     * maxSplit is optional. Empty input means "do not emit this field", so the
     * core keeps its own default behavior.
     */
    fun normalizeMaxSplit(value: String?): String? {
        return value
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}
