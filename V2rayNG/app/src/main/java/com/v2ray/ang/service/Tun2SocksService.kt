package com.v2ray.ang.service

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.ParcelFileDescriptor
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.VPN_MTU
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Manages the tun2socks process that handles VPN traffic
 */
class Tun2SocksService(
    private val context: Context,
    private val vpnInterface: ParcelFileDescriptor
) {
    companion object {
        init {
            System.loadLibrary("hev-socks5-tunnel")
        }
    }

    private external fun TProxyStartService(configPath: String, fd: Int)
    private external fun TProxyStopService()
    private external fun TProxyGetStats(): LongArray

    /**
     * Start the HevSocks5Tunnel
     */
    fun startHevSocks5Tunnel() {
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
        return buildString {
            appendLine("tunnel:")
            appendLine("  mtu: $VPN_MTU")
            appendLine("  ipv4: ${SettingsManager.getCurrentVpnInterfaceAddressConfig().ipv4Client}")

            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PREFER_IPV6) == true) {
                appendLine("  ipv6: \'${SettingsManager.getCurrentVpnInterfaceAddressConfig().ipv6Client}\'")
            }

            appendLine("socks5:")
            appendLine("  port: ${SettingsManager.getSocksPort()}")
            appendLine("  address: ${AppConfig.LOOPBACK}")
            appendLine("  udp: 'udp'")

            MmkvManager.decodeSettingsString(AppConfig.PREF_LOGLEVEL)?.let { logPref ->
                if (logPref != "none") {
                    val logLevel = if (logPref == "warning") "warn" else logPref
                    appendLine("misc:")
                    appendLine("  log-level: $logLevel")
                }
            }
        }
    }

    /**
     * Stops the HevSocks5Tunnel
     */
    fun stopHevSocks5Tunnel() {
        try {
            Log.i(AppConfig.TAG, "TProxyStopService...")
            TProxyStopService()
         } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to stop hev-socks5-tunnel", e)
         }
    }
}
