package com.v2ray.ang.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager

/** Resolves reconnect-stable underlay identities without phone-state access. */
object NetworkIdentityResolver {
    fun resolve(context: Context, capabilities: NetworkCapabilities): String = when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
            val rememberPerNetwork = canReadWifiIdentity(context)
            wifiKey(
                ssid = if (rememberPerNetwork) readSsid(context, capabilities) else null,
                rememberPerNetwork = rememberPerNetwork,
            )
        }

        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
            val (networkOperator, simOperator) = readCellularOperators(context)
            cellularKey(networkOperator, simOperator)
        }

        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
        else -> "other"
    }

    fun canReadWifiIdentity(context: Context): Boolean {
        val enabled = MmkvManager.decodeSettingsBool(
            AppConfig.PREF_REMEMBER_ROUTES_PER_WIFI_NETWORK,
            false,
        )
        if (!enabled) return false

        val permissionGranted =
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!permissionGranted) {
            MmkvManager.encodeSettings(AppConfig.PREF_REMEMBER_ROUTES_PER_WIFI_NETWORK, false)
        }
        return permissionGranted
    }

    internal fun wifiKey(ssid: String?, rememberPerNetwork: Boolean = true): String {
        if (!rememberPerNetwork) return "wifi"
        val normalized = ssid
            ?.trim()
            ?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID }
        return normalized?.let { "wifi:$it" } ?: "wifi"
    }

    internal fun cellularKey(networkOperator: String?, simOperator: String?): String {
        val mccMnc = sequenceOf(networkOperator, simOperator)
            .mapNotNull { it?.trim()?.takeIf(::isMccMnc) }
            .firstOrNull()
        return mccMnc?.let { "cellular:$it" } ?: "cellular"
    }

    private fun isMccMnc(value: String): Boolean =
        value.length in 5..6 && value.all(Char::isDigit)

    @Suppress("DEPRECATION")
    private fun readSsid(context: Context, capabilities: NetworkCapabilities): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val capabilitiesSsid = (capabilities.transportInfo as? WifiInfo)?.ssid
            if (wifiKey(capabilitiesSsid) != "wifi") return capabilitiesSsid
        }
        return runCatching {
            context.applicationContext
                .getSystemService(WifiManager::class.java)
                ?.connectionInfo
                ?.ssid
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun readCellularOperators(context: Context): Pair<String?, String?> {
        val base = context.getSystemService(TelephonyManager::class.java)
            ?: return null to null
        val subscriptionId = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> SubscriptionManager.getActiveDataSubscriptionId()
            else -> SubscriptionManager.getDefaultDataSubscriptionId()
        }
        val telephony = if (SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
            base.createForSubscriptionId(subscriptionId)
        } else {
            base
        }
        return telephony.networkOperator to telephony.simOperator
    }
}
