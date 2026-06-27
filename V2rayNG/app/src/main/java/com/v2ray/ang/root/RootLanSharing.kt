package com.v2ray.ang.root

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


object RootLanSharing {

    private var lanSharingStarted = false
    private var lanShareJob: Job? = null

    /**
     * Optional root feature: share the proxy with tethered LAN/USB clients while the
     * device itself stays on the VpnService. Runs a dedicated client hev-socks5-tunnel
     * off the main thread so the su calls don't block service startup.
     * The cheap, usually-false preference is checked first so the common path
     * short-circuits before touching root state.
     */
    fun startClientSharing(context: Context): Boolean {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_ROOT_LAN_SHARING) && RootManager.cachedRoot()) {
            if (lanShareJob != null) return false

            lanSharingStarted = true
            lanShareJob = CoroutineScope(Dispatchers.IO).launch { RootProxyManager.startClientSharing(context) }
        }

        return true
    }

    /**
     * Remove LAN/tethering sharing rules + helper before stopping the core. Wait for the
     * async setup to finish first, otherwise a stop during setup tears down before the
     * rules are installed and they leak (orphan FORWARD/policy-routing rules + client tun).
     */
    fun stopClientSharing(context: Context) {
        if (!lanSharingStarted) return

        lanSharingStarted = false
        runBlocking { lanShareJob?.cancelAndJoin() }
        lanShareJob = null
        RootProxyManager.stop(context)
    }
}
