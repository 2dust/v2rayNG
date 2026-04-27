package com.v2ray.ang.handler

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Process
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object NetworkTriggerHandler {

    private const val READY_DELAY_MS = 3000L
    private const val DEBOUNCE_MS = 1500L

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var debounceJob: Job? = null
    private var ready = false
    private var lastStateKey: String? = null
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        if (!isMainProcess(context)) return
        initialized = true
        registerCallback(context.applicationContext)
    }

    private fun registerCallback(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (ready) scheduleCheck(context)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (ready) scheduleCheck(context)
            }

            override fun onLost(network: Network) {
                if (ready) scheduleCheck(context)
            }
        })
        scope.launch {
            delay(READY_DELAY_MS)
            ready = true
        }
    }

    private fun scheduleCheck(context: Context) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            try {
                delay(DEBOUNCE_MS)
                checkAndApply(context)
            } catch (e: Exception) {
            }
        }
    }

    private fun checkAndApply(context: Context) {
        val triggers = MmkvManager.decodeNetworkTriggers().filter { it.enabled }
        if (triggers.isEmpty()) return

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var isWifi = false
        var isMobile = false
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) isWifi = true
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) isMobile = true
        }
        // Cellular radio usually stays active while Wi-Fi is connected; treat "on mobile data" as Wi-Fi absent.
        if (isWifi) isMobile = false

        val ssid = if (isWifi) currentSsid(context) else ""
        val stateKey = "$isWifi|$isMobile|$ssid"
        if (stateKey == lastStateKey) return
        lastStateKey = stateKey

        for (trigger in triggers) {
            val matches = when (trigger.triggerType) {
                AppConfig.TRIGGER_TYPE_WIFI_ANY -> isWifi
                AppConfig.TRIGGER_TYPE_MOBILE_DATA -> isMobile
                AppConfig.TRIGGER_TYPE_WIFI_SSID -> isWifi && ssid.isNotEmpty() && ssid == trigger.targetSsid
                else -> false
            }
            if (!matches) continue

            if (trigger.action == AppConfig.TRIGGER_ACTION_START) {
                if (!isVpnActive(cm)) {
                    context.toast(R.string.toast_trigger_vpn_start)
                    V2RayServiceManager.startVServiceFromToggle(context)
                }
            } else if (isVpnActive(cm)) {
                context.toast(R.string.toast_trigger_vpn_stop)
                V2RayServiceManager.stopVService(context)
            }
            break
        }
    }

    private fun isVpnActive(cm: ConnectivityManager): Boolean {
        // activeNetwork never exposes the app's own VPN to itself; scan allNetworks instead.
        return cm.allNetworks.any { network ->
            cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }

    @Suppress("DEPRECATION")
    private fun currentSsid(context: Context): String {
        return try {
            val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo
            info.ssid?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun isMainProcess(context: Context): Boolean {
        val pid = Process.myPid()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.runningAppProcesses?.any { it.pid == pid && it.processName == context.packageName } == true
    }
}
