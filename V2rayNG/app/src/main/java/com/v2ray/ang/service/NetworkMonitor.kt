package com.v2ray.ang.service

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Watches the network that carries the tunnel and reports stable handovers.
 *
 * Android may announce a replacement network before losing the old one, and may deliver stale
 * capability or loss callbacks for that old network afterwards. Only the newest network is allowed
 * to update the VPN underlay or complete the debounced handover.
 *
 * Only used from Android P and above, see CoreServiceManager.startNetworkMonitor().
 * [onHandover] is invoked on a background thread after the debounce window and may block.
 */
class NetworkMonitor(
    private val connectivity: ConnectivityManager,
    private val onUnderlyingNetworksChanged: (Array<Network>?) -> Unit,
    private val onHandover: () -> Unit,
) {
    private companion object {
        const val HANDOVER_DEBOUNCE_MS = 1000L
    }

    private val state = UnderlyingNetworkStateTracker<Network>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var handoverJob: Job? = null

    @Volatile
    private var registered = false

    /**
     * Unfortunately registerDefaultNetworkCallback is going to return our VPN interface:
     * https://android.googlesource.com/platform/frameworks/base/+/dda156ab0c5d66ad82bdcf76cda07cbc0a9c8a2e
     *
     * This makes doing a requestNetwork with REQUEST necessary so that we don't get ALL possible networks that
     * satisfies default network capabilities but only THE default network. Unfortunately we need to have
     * android.permission.CHANGE_NETWORK_STATE to be able to call requestNetwork.
     *
     * Source: https://android.googlesource.com/platform/frameworks/base/+/2df4c7d/services/core/java/com/android/server/ConnectivityService.java#887
     */
    private val request by lazy {
        NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = handleAvailable(network)

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) =
            handleCapabilitiesChanged(network)

        override fun onLost(network: Network) = handleLost(network)
    }

    /** Starts watching. Safe to call more than once, only the first call registers. */
    fun register() {
        if (registered) return
        registered = true
        try {
            connectivity.requestNetwork(request, callback)
        } catch (e: Exception) {
            registered = false
            LogUtil.e(AppConfig.TAG, "NetworkMonitor: Failed to request network", e)
        }
    }

    /** Stops watching and drops the tracked state. Safe to call more than once. */
    fun unregister() {
        val wasRegistered = registered
        registered = false
        handoverJob?.cancel()
        handoverJob = null
        state.reset()
        if (!wasRegistered) return
        try {
            connectivity.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "NetworkMonitor: Failed to unregister callback", e)
        }
    }

    private fun handleAvailable(network: Network) {
        if (!registered) return
        val isHandover = state.onAvailable(network)
        notifyUnderlyingNetworksChanged(arrayOf(network))
        if (isHandover) {
            scheduleHandover(network)
        }
    }

    private fun handleCapabilitiesChanged(network: Network) {
        if (!registered || !state.isCurrent(network)) return
        notifyUnderlyingNetworksChanged(arrayOf(network))
    }

    private fun handleLost(network: Network) {
        if (!registered || !state.onLost(network)) return
        handoverJob?.cancel()
        handoverJob = null
        notifyUnderlyingNetworksChanged(null)
    }

    private fun scheduleHandover(network: Network) {
        LogUtil.i(AppConfig.TAG, "NetworkMonitor: Upstream is now $network")
        handoverJob?.cancel()
        handoverJob = scope.launch {
            try {
                delay(HANDOVER_DEBOUNCE_MS)
                if (state.isCurrent(network)) {
                    onHandover()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "NetworkMonitor: Failed to handle upstream change", e)
            }
        }
    }

    private fun notifyUnderlyingNetworksChanged(networks: Array<Network>?) {
        try {
            onUnderlyingNetworksChanged(networks)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "NetworkMonitor: Failed to publish underlying network", e)
        }
    }
}
