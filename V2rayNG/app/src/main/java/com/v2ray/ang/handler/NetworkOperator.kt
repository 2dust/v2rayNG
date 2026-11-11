package com.v2ray.ang.handler

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager

/**
 * Name of the network the measurements belong to.
 *
 * Suggestions are only meaningful per operator: a config that is fast on one carrier can be dead on
 * another, so every benchmark is stored under the network it was measured on.
 */
object NetworkOperator {

    const val WIFI = "Wi-Fi"
    const val UNKNOWN = "Other"

    fun current(context: Context): String {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity?.activeNetwork
            ?.let { connectivity.getNetworkCapabilities(it) }

        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            return WIFI
        }

        val telephony = context.getSystemService(TelephonyManager::class.java)
        val name = telephony?.networkOperatorName?.trim().orEmpty()
        return name.ifEmpty { UNKNOWN }
    }
}
