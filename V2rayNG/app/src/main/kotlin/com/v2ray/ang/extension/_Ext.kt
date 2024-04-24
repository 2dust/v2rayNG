package com.v2ray.ang.extension

import android.content.Context
import android.os.Build
import android.widget.Toast
import com.v2ray.ang.AngApplication
import me.drakeet.support.toast.ToastCompat
import org.json.JSONObject
import java.net.URI
import java.net.URLConnection

val Context.v2RayApplication: AngApplication
    get() = applicationContext as AngApplication

fun Context.toast(message: Int) {
    ToastCompat.makeText(this, message, Toast.LENGTH_SHORT).apply { show() }
}

fun Context.toast(message: CharSequence) {
    ToastCompat.makeText(this, message, Toast.LENGTH_SHORT).apply { show() }
}

fun JSONObject.putOpt(pair: Pair<String, Any?>) {
    put(pair.first, pair.second)
}

fun JSONObject.putOpt(pairs: Map<String, Any?>) {
    pairs.forEach { put(it.key, it.value) }
}

const val THRESHOLD = 1000L
const val DIVISOR = 1024.0

fun Long.toSpeedString(): String = this.toTrafficString() + "/s"

fun Long.toTrafficString(): String {
    if (this < THRESHOLD) {
        return "$this B"
    }
    val kb = this / DIVISOR
    if (kb < THRESHOLD) {
        return "${String.format("%.1f KB", kb)}"
    }
    val mb = kb / DIVISOR
    if (mb < THRESHOLD) {
        return "${String.format("%.1f MB", mb)}"
    }
    val gb = mb / DIVISOR
    if (gb < THRESHOLD) {
        return "${String.format("%.1f GB", gb)}"
    }
    val tb = gb / DIVISOR
    if (tb < THRESHOLD) {
        return "${String.format("%.1f TB", tb)}"
    }
    return String.format("%.1f PB", tb / DIVISOR)
}

val URLConnection.responseLength: Long
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        contentLengthLong
    } else {
        contentLength.toLong()
    }

val URI.idnHost: String
    get() = host?.replace("[", "")?.replace("]", "") ?: ""

fun String.removeWhiteSpace(): String = replace("\\s+".toRegex(), "")
