package com.v2ray.ang.extension

/**
 * Moves an item to another index while preserving the relative order of the remaining items.
 */
internal fun <T> MutableList<T>.moveItem(fromIndex: Int, toIndex: Int): Boolean {
    if (fromIndex !in indices || toIndex !in indices || fromIndex == toIndex) return false
    add(toIndex, removeAt(fromIndex))
    return true
}
