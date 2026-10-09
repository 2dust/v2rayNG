package com.v2ray.ang.dto

data class AetherRange(val min: Int, val max: Int) {

    override fun toString(): String = if (min == max) "$min" else "$min-$max"

    companion object {

        val FRAGMENT_SIZE = 1..4096
        val FRAGMENT_DELAY = 0..1000

        fun parse(text: String?, bounds: IntRange): AetherRange? {
            val parts = text?.trim()?.split('-') ?: return null
            if (parts.size !in 1..2) return null
            val values = parts.map { part -> part.trim().toIntOrNull()?.takeIf { it in bounds } ?: return null }
            return AetherRange(values.min(), values.max())
        }
    }
}
