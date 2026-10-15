package com.v2ray.ang.extension

import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.v2ray.ang.enums.EConfigType
import java.io.Serializable
import java.net.URI
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

const val THRESHOLD = 1000L
const val DIVISOR = 1024.0

/**
 * Converts a Long value to a speed string.
 *
 * @return The speed string.
 */
fun Long.toSpeedString(): String = this.toTrafficString() + "/s"

/**
 * Converts a Long value to a traffic string.
 *
 * @return The traffic string.
 */
fun Long.toTrafficString(): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
    var size = this.toDouble()
    var unitIndex = 0
    while (size >= THRESHOLD && unitIndex < units.size - 1) {
        size /= DIVISOR
        unitIndex++
    }
    return String.format(Locale.getDefault(), "%.1f %s", size, units[unitIndex])
}

val URI.idnHost: String
    get() = host?.replace("[", "")?.replace("]", "").orEmpty()

/**
 * Retrieves a serializable object from the Bundle.
 *
 * @param key The key of the serializable object.
 * @return The serializable object, or null if not found.
 */
inline fun <reified T : Serializable> Bundle.serializable(key: String): T? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getSerializable(key, T::class.java)
    else -> @Suppress("DEPRECATION") getSerializable(key) as? T
}

/**
 * Retrieves a serializable object from the Intent.
 *
 * @param key The key of the serializable object.
 * @return The serializable object, or null if not found.
 */
inline fun <reified T : Serializable> Intent.serializable(key: String): T? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getSerializableExtra(key, T::class.java)
    else -> @Suppress("DEPRECATION") getSerializableExtra(key) as? T
}

/**
 * Checks if the config type is a group type (PolicyGroup or ProxyChain).
 *
 * @return True if the config type is PolicyGroup or ProxyChain, false otherwise.
 */
fun EConfigType.isGroupType(): Boolean {
    return this == EConfigType.POLICYGROUP || this == EConfigType.PROXYCHAIN
}

/**
 * Checks if the config type is a complex type (Custom, PolicyGroup, or ProxyChain).
 *
 * @return True if the config type is Custom, Custom Outbound, PolicyGroup, or ProxyChain, false otherwise.
 */
fun EConfigType.isComplexType(): Boolean {
    return this == EConfigType.CUSTOM || this == EConfigType.CUSTOM_OUTBOUND
        || this == EConfigType.POLICYGROUP || this == EConfigType.PROXYCHAIN
}

/**
 * Checks whether a profile of this config type can be exported as a single share link.
 *
 * Custom outbounds qualify even though they are complex, because their outbound object
 * encodes into a v2rayn:// link. Other complex types have no single-link form, so the
 * link and QR code share actions are never offered for them.
 */
fun EConfigType.isShareableAsLink(): Boolean {
    return !isComplexType() || this == EConfigType.CUSTOM_OUTBOUND
}

/**
 * Shorthand for delay with Int milliseconds using Duration to avoid legacy Long overload warning.
 */
suspend fun delay(millis: Int) {
    kotlinx.coroutines.delay(millis.toLong().milliseconds)
}

/**
 * Shorthand for delay with Long milliseconds using Duration to avoid legacy Long overload warning.
 */
suspend fun delay(millis: Long) {
    kotlinx.coroutines.delay(millis.milliseconds)
}


