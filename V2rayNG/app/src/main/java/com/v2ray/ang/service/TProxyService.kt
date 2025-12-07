package com.v2ray.ang.service

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import java.io.File

/**
 * Manages the tun2socks process that handles VPN traffic
 */
class TProxyService(
    private val context: Context,
    private val vpnInterface: ParcelFileDescriptor,
    private val isRunningProvider: () -> Boolean,
    private val restartCallback: () -> Unit
) : Tun2SocksControl {
    companion object {
        @JvmStatic
        @Suppress("FunctionName")
        private external fun TProxyStartService(configPath: String, fd: Int)
        @JvmStatic
        @Suppress("FunctionName")
        private external fun TProxyStopService()
        @JvmStatic
        @Suppress("FunctionName")
        private external fun TProxyGetStats(): LongArray?

        init {
            System.loadLibrary("hev-socks5-tunnel")
        }
    }

    /**
     * Starts the tun2socks process with the appropriate parameters.
     */
    override fun startTun2Socks() {
        Log.i(AppConfig.TAG, "Starting HevSocks5Tunnel via JNI")

        val configContent = buildConfig()
        val configFile = File(context.filesDir, "hev-socks5-tunnel.yaml").apply {
            writeText(configContent)
        }
        Log.i(AppConfig.TAG, "Config file created: ${configFile.absolutePath}")
        Log.d(AppConfig.TAG, "Config content:\n$configContent")

        try {
            Log.i(AppConfig.TAG, "TProxyStartService...")
            TProxyStartService(configFile.absolutePath, vpnInterface.fd)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "HevSocks5Tunnel exception: ${e.message}")
        }
    }

    private fun buildConfig(): String {
        val socksPort = SettingsManager.getSocksPort()
        val vpnConfig = SettingsManager.getCurrentVpnInterfaceAddressConfig()
        return buildString {
            appendLine("tunnel:")
            appendLine("  mtu: ${SettingsManager.getVpnMtu()}")
            appendLine("  ipv4: ${vpnConfig.ipv4Client}")

            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PREFER_IPV6) == true) {
                appendLine("  ipv6: '${vpnConfig.ipv6Client}'")
            }

            appendLine("socks5:")
            appendLine("  port: ${socksPort}")
            appendLine("  address: ${AppConfig.LOOPBACK}")
            appendLine("  udp: 'udp'")

            appendLine("misc:")
            appendLine("  tcp-read-write-timeout: ${MmkvManager.decodeSettingsString(AppConfig.PREF_HEV_TUNNEL_RW_TIMEOUT) ?: AppConfig.HEVTUN_RW_TIMEOUT}")
            appendLine("  udp-read-write-timeout: ${MmkvManager.decodeSettingsString(AppConfig.PREF_HEV_TUNNEL_RW_TIMEOUT) ?: AppConfig.HEVTUN_RW_TIMEOUT}")
            appendLine("  log-level: ${MmkvManager.decodeSettingsString(AppConfig.PREF_HEV_TUNNEL_LOGLEVEL) ?: "warn"}")
        }
    }

    /**
     * Stops the tun2socks process
     */
    override fun stopTun2Socks() {
        try {
            Log.i(AppConfig.TAG, "TProxyStopService...")
            TProxyStopService()
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to stop hev-socks5-tunnel", e)
        }
    }
}
