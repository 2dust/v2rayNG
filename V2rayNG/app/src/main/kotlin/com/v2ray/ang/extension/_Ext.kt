package com.v2ray.ang.extension

import android.content.Context
import android.os.Build
import android.widget.Toast
import com.v2ray.ang.AngApplication
import me.drakeet.support.toast.ToastCompat
import org.json.JSONObject
import java.net.URI
import java.net.URLConnection

/**
 * Some extensions for the AngApplication
 */

// Extension property to retrieve AngApplication instance from Context
val Context.v2RayApplication: AngApplication
    get() = applicationContext as AngApplication

// Extension functions to show a toast message for Int and CharSequence types
fun Context.toast(message: Int, duration: Int = Toast.LENGTH_SHORT) = 
    ToastCompat.makeText(this, message, duration).show()

fun Context.toast(message: CharSequence, duration: Int = Toast.LENGTH_SHORT) = 
    ToastCompat.makeText(this, message, duration).show()

// JSONObject Extensions to put a Pair or Map of data
fun JSONObject.putOpt(pair: Pair<String, Any>) = putOpt(pair.first, pair.second)

fun JSONObject.putOpt(pairs: Map<String, Any>) = pairs.forEach { (key, value) ->
    putOpt(key to value)
}

// Constants for traffic and speed conversions
const val threshold = 1000
const val divisor = 1024F

// Extension function to convert Long byte value to a string with speed units
fun Long.toSpeedString() = "${toTrafficString()}/s"

// Extension function to convert Long byte value to a string with traffic units
fun Long.toTrafficString(): String = when {
    this == 0L -> "0 B"
    this < threshold -> "${this.formatBytes()} B"
    else -> {
        val (value, unit) = listOf(
            divisor to "KB",
            divisor * divisor to "MB",
            divisor * divisor * divisor to "GB",
            divisor * divisor * divisor * divisor to "TB",
            divisor * divisor * divisor * divisor * divisor to "PB"
        ).fold(this to "B") { (value, _), (div, unit) ->
            if (value < threshold * divisor) return@fold value to unit
            (value / divisor) to unit
        }
        "${value.formatBytes()} $unit"
    }
}

private fun Long.formatBytes() = "%.2f".format(this / divisor).trimEnd('0').trimEnd('.')

// URLConnection extension property to return the response length with backward compatibility
val URLConnection.responseLength: Long
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) contentLengthLong else contentLength.toLong()

// URI extension property to return an IDN Hostname
val URI.idnHost: String
    get() = host?.replace("[", "")?.replace("]", "") ?: ""

// String extension function to remove all white spaces
fun String.removeWhiteSpace() = replace(" ", "")
