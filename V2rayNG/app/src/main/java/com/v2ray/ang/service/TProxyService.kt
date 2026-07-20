package com.v2ray.ang.service

import android.content.Context
import android.os.ParcelFileDescriptor
import com.v2ray.ang.AppConfig
import com.v2ray.ang.contracts.Tun2SocksControl
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
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

        /**
         * Starts a second HEV instance from a dedicated process.
         *
         * The Shizuku tethering UserService runs in its own process, so HEV's process-global
         * native state is independent from the instance serving the regular VpnService TUN.
         */
        internal fun startExternalTunnel(configPath: String, fd: Int) {
            TProxyStartService(configPath, fd)
        }

        /** Stops the HEV instance started in the current process. */
        internal fun stopExternalTunnel() {
            TProxyStopService()
        }

        init {
            System.loadLibrary("hev-socks5-tunnel")
        }
    }

    /**
     * Starts the tun2socks process with the appropriate parameters.
     */
    override fun startTun2Socks() {
//        LogUtil.i(AppConfig.TAG, "Starting HevSocks5Tunnel via JNI")

        val configContent = buildConfig()
        val configFile = File(context.filesDir, "hev-socks5-tunnel.yaml").apply {
            writeText(configContent)
        }
//        LogUtil.i(AppConfig.TAG, "Config file created: ${configFile.absolutePath}")
        LogUtil.d(AppConfig.TAG, "HevSocks5Tunnel Config content:\n$configContent")

        try {
//            LogUtil.i(AppConfig.TAG, "TProxyStartService...")
            TProxyStartService(configFile.absolutePath, vpnInterface.fd)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "HevSocks5Tunnel exception: ${e.message}")
        }
    }

    private fun buildConfig(): String {
        val vpnConfig = SettingsManager.getCurrentVpnInterfaceAddressConfig()
        return HevTunnelConfig.build(
            HevTunnelParameters(
                mtu = SettingsManager.getVpnMtu(),
                ipv4 = vpnConfig.ipv4Client,
                ipv6 = vpnConfig.ipv6Client.takeIf {
                    MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED)
                },
                socksAddress = AppConfig.LOOPBACK,
                socksPort = SettingsManager.getSocksPort(),
                socksUsername = SettingsManager.getSocksUsername(),
                socksPassword = SettingsManager.getSocksPassword(),
                settings = HevTunnelSettings.current(),
            ),
        )
    }

    /**
     * Stops the tun2socks process
     */
    override fun stopTun2Socks() {
        try {
            LogUtil.i(AppConfig.TAG, "TProxyStopService...")
            TProxyStopService()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to stop hev-socks5-tunnel", e)
        }
    }
}
