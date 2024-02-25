package com.v2ray.ang.extension

import android.content.Context
import android.widget.Toast
import com.v2ray.ang.AngApplication
import me.drakeet.support.toast.ToastCompat
import org.json.JSONObject
import java.net.URI
import java.net.URLConnection

val Context.v2RayApplication: AngApplication
    get() = applicationContext as? AngApplication ?: throw IllegalStateException("Not an AngApplication")

fun Context.toast(message: Int) = showToast(message)
fun Context.toast(message: CharSequence) = showToast(message)

private fun Context.showToast(message: Any) {
    ToastCompat.makeText(this, message.toString(), Toast.LENGTH_SHORT).apply { show() }
}

fun JSONObject.putOpt(pair: Pair<String, Any>) {
    pair.second?.let {
        put(pair.first, it)
    }
}

fun JSONObject.putOpt(pairs: Map<String, Any>) {
    pairs.forEach { (key, value) ->
        putOpt(key to value)
    }
}

private const val KILOBYTE = 1024.0
private const val THRESHOLD = 1024

fun Long.toTrafficString(): String {
    var value = this.toDouble()
    val units = listOf("B", "KB", "MB", "GB", "TB", "PB")
    var unitIndex = 0

    while (value >= THRESHOLD && unitIndex < units.lastIndex) {
        value /= KILOBYTE
        unitIndex++
    }

    return String.format("%.2f %s", value, units[unitIndex])
}

val URLConnection.responseLength: Long
    get() = if (Build.VERSION.SDK_INT >= 23) contentLengthLong else contentLength.toLong()

val URI.idnHost: String
    get() = host?.replace("[", "")?.replace("]", "") ?: ""

fun String.removeWhiteSpace() = replace("\\s+".toRegex(), "")
