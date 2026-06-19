package com.v2ray.ang.enums

import com.v2ray.ang.AppConfig

/**
 * The way the core routes system traffic.
 *
 * [VPN] and [PROXY_ONLY] keep the historical behavior and need no root.
 * [TUN2SOCKS] is the root-only system-wide mode ("Root mode"): it routes all traffic
 * through a tun device into the in-process core via a root tun2socks helper, without
 * using Android [android.net.VpnService].
 *
 * The string [prefValue] is what gets persisted in [AppConfig.PREF_MODE]; the legacy
 * values "VPN" and "Proxy only" are preserved so existing installs keep working.
 */
enum class ERunMode(val prefValue: String, val needsRoot: Boolean) {
    VPN(AppConfig.MODE_VPN, false),
    PROXY_ONLY(AppConfig.MODE_PROXY_ONLY, false),
    TUN2SOCKS(AppConfig.MODE_TUN2SOCKS, true);

    companion object {
        fun fromPref(value: String?): ERunMode {
            if (value.isNullOrEmpty()) return VPN
            return entries.firstOrNull { it.prefValue == value } ?: VPN
        }
    }
}
