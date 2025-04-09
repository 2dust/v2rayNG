package com.v2ray.ang.extension

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.v2ray.ang.AngApplication
import es.dmoral.toasty.Toasty
import org.json.JSONObject
import java.io.Serializable
import java.net.URI
import java.net.URLConnection

val Context.v2RayApplication: AngApplication?
    get() = applicationContext as? AngApplication

/**
 * Shows a toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 */
fun Context.toast(message: Int) {
    Toasty.normal(this, message).show()
}

/**
 * Shows a toast message with the given text.
 *
 * @param message The text of the message to show.
 */
fun Context.toast(message: CharSequence) {
    Toasty.normal(this, message).show()
}

/**
 * Shows a toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 */
fun Context.toastSuccess(message: Int) {
    Toasty.success(this, message, Toast.LENGTH_SHORT, true).show()
}

/**
 * Shows a toast message with the given text.
 *
 * @param message The text of the message to show.
 */
fun Context.toastSuccess(message: CharSequence) {
    Toasty.success(this, message, Toast.LENGTH_SHORT, true).show()
}

/**
 * Shows a toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 */
fun Context.toastError(message: Int) {
    Toasty.error(this, message, Toast.LENGTH_SHORT, true).show()
}

/**
 * Shows a toast message with the given text.
 *
 * @param message The text of the message to show.
 */
fun Context.toastError(message: CharSequence) {
    Toasty.error(this, message, Toast.LENGTH_SHORT, true).show()
}


/**
 * Puts a key-value pair into the JSONObject.
 *
 * @param pair The key-value pair to put.
 */
fun JSONObject.putOpt(pair: Pair<String, Any?>) {
    put(pair.first, pair.second)
}

/**
 * Puts multiple key-value pairs into the JSONObject.
 *
 * @param pairs The map of key-value pairs to put.
 */
fun JSONObject.putOpt(pairs: Map<String, Any?>) {
    pairs.forEach { put(it.key, it.value) }
}

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
    return String.format("%.1f %s", size, units[unitIndex])
}

val URLConnection.responseLength: Long
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        contentLengthLong
    } else {
        contentLength.toLong()
    }

val URI.idnHost: String
    get() = host?.replace("[", "")?.replace("]", "").orEmpty()

/**
 * Removes all whitespace from the string.
 *
 * @return The string without whitespace.
 */
fun String?.removeWhiteSpace(): String? = this?.replace(" ", "")

/**
 * Converts the string to a Long value, or returns 0 if the conversion fails.
 *
 * @return The Long value.
 */
fun String.toLongEx(): Long = toLongOrNull() ?: 0

/**
 * Listens for package changes and executes a callback when a change occurs.
 *
 * @param onetime Whether to unregister the receiver after the first callback.
 * @param callback The callback to execute when a package change occurs.
 * @return The BroadcastReceiver that was registered.
 */
fun Context.listenForPackageChanges(onetime: Boolean = true, callback: () -> Unit) =
    object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            callback()
            if (onetime) context.unregisterReceiver(this)
        }
    }.apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(this, IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(this, IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            })
        }
    }

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
 * Checks if the CharSequence is not null and not empty.
 *
 * @return True if the CharSequence is not null and not empty, false otherwise.
 */
fun CharSequence?.isNotNullEmpty(): Boolean = this != null && this.isNotEmpty()

fun String.concatUrl(vararg paths: String): String {
    val builder = StringBuilder(this.trimEnd('/'))

    paths.forEach { path ->
        val trimmedPath = path.trim('/')
        if (trimmedPath.isNotEmpty()) {
            builder.append('/').append(trimmedPath)
        }
    }

    return builder.toString()
}