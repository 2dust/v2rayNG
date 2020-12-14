package com.v2ray.ang.extension

import android.content.Context
import android.os.Build
import android.widget.Toast
import com.v2ray.ang.AngApplication
import me.dozen.dpreference.DPreference
import me.drakeet.support.toast.ToastCompat
import org.json.JSONObject
import java.net.URLConnection

/**
 * Some extensions
 */

val Context.v2RayApplication: AngApplication
    get() = applicationContext as AngApplication

// Usage note: DPreference use Android ContentProvider to redirect multi process access to main process.
// Currently, RunSoLibV2RayDaemon process will run proxy core, keep minimum configuration and long running
// in the background, support toggle on/off. That means it should NOT use DPreference after the initial
// creation and setup of the service
val Context.defaultDPreference: DPreference
    get() = v2RayApplication.defaultDPreference

inline fun Context.toast(message: Int): Toast = ToastCompat
        .makeText(this, message, Toast.LENGTH_SHORT)
        .apply {
            show()
        }

inline fun Context.toast(message: CharSequence): Toast = ToastCompat
        .makeText(this, message, Toast.LENGTH_SHORT)
        .apply {
            show()
        }

fun JSONObject.putOpt(pair: Pair<String, Any>) = putOpt(pair.first, pair.second)!!
fun JSONObject.putOpt(pairs: Map<String, Any>) = pairs.forEach { putOpt(it.key to it.value) }

const val threshold = 1000
const val divisor = 1024F

fun Long.toSpeedString() = toTrafficString() + "/s"

fun Long.toTrafficString(): String {
    if (this == 0L)
        return "\t\t\t0\t  B"

    if (this < threshold)
        return "${this.toFloat().toShortString()}\t  B"

    val kib = this / divisor
    if (kib < threshold)
        return "${kib.toShortString()}\t KB"

    val mib = kib / divisor
    if (mib < threshold)
        return "${mib.toShortString()}\t MB"

    val gib = mib / divisor
    if (gib < threshold)
        return "${gib.toShortString()}\t GB"

    val tib = gib / divisor
    if (tib < threshold)
        return "${tib.toShortString()}\t TB"

    val pib = tib / divisor
    if (pib < threshold)
        return "${pib.toShortString()}\t PB"

    return "∞"
}

private fun Float.toShortString(): String {
    val s = "%.2f".format(this)
    if (s.length <= 4)
        return s
    return s.substring(0, 4).removeSuffix(".")
}

val URLConnection.responseLength: Long
    get() = if (Build.VERSION.SDK_INT >= 24) contentLengthLong else contentLength.toLong()
