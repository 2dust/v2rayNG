package com.v2ray.ang.ui.compose

/** Prefer the following row after deletion, or the preceding row when deleting the last one. */
internal fun focusAfterRemoval(keys: List<String>, removed: String): String? {
    val index = keys.indexOf(removed)
    if (index < 0) return null
    return keys.getOrNull(index + 1) ?: keys.getOrNull(index - 1)
}
