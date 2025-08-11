package com.v2ray.ang.service

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.ParcelFileDescriptor
import android.util.Log
import com.v2ray.ang.AppConfig
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
    private val vpnInterface: ParcelFileDescriptor,
    private val isRunningProvider: () -> Boolean,
    private val restartCallback: () -> Unit
) : Tun2SocksControl {
    companion object {
        private const val TUN2SOCKS = "libtun2socks.so"
    }

    private lateinit var process: Process

    /**
     * Starts the tun2socks process with the appropriate parameters.
     */
    override fun startTun2Socks() {
        Log.i(AppConfig.TAG, "Start run $TUN2SOCKS")
        val socksPort = SettingsManager.getSocksPort()
        val vpnConfig = SettingsManager.getCurrentVpnInterfaceAddressConfig()
        val cmd = arrayListOf(
            File(context.applicationInfo.nativeLibraryDir, TUN2SOCKS).absolutePath,
            "--netif-ipaddr", vpnConfig.ipv4Router,
            "--netif-netmask", "255.255.255.252",
            "--socks-server-addr", "${AppConfig.LOOPBACK}:${socksPort}",
            "--tunmtu", SettingsManager.getVpnMtu().toString(),
            "--sock-path", "sock_path",
            "--enable-udprelay",
            "--loglevel", "notice"
        )

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PREFER_IPV6)) {
            cmd.add("--netif-ip6addr")
            cmd.add(vpnConfig.ipv6Router)
        }
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_LOCAL_DNS_ENABLED)) {
            val localDnsPort = Utils.parseInt(
                MmkvManager.decodeSettingsString(AppConfig.PREF_LOCAL_DNS_PORT), 
                AppConfig.PORT_LOCAL_DNS.toInt()
            )
            cmd.add("--dnsgw")
            cmd.add("${AppConfig.LOOPBACK}:${localDnsPort}")
        }
        Log.i(AppConfig.TAG, cmd.toString())

        try {
            val proBuilder = ProcessBuilder(cmd)
            proBuilder.redirectErrorStream(true)
            process = proBuilder
                .directory(context.filesDir)
                .start()
            Thread {
                Log.i(AppConfig.TAG, "$TUN2SOCKS check")
                process.waitFor()
                Log.i(AppConfig.TAG, "$TUN2SOCKS exited")
                if (isRunningProvider()) {
                    Log.i(AppConfig.TAG, "$TUN2SOCKS restart")
                    restartCallback()
                }
            }.start()
            Log.i(AppConfig.TAG, "$TUN2SOCKS process info: $process")

            sendFd()
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to start $TUN2SOCKS process", e)
        }
    }

    /**
     * Sends the file descriptor to the tun2socks process.
     * Attempts to send the file descriptor multiple times if necessary.
     */
    private fun sendFd() {
        val fd = vpnInterface.fileDescriptor
        val path = File(context.filesDir, "sock_path").absolutePath
        Log.i(AppConfig.TAG, "LocalSocket path: $path")

        CoroutineScope(Dispatchers.IO).launch {
            var tries = 0
            while (true) try {
                Thread.sleep(50L shl tries)
                Log.i(AppConfig.TAG, "LocalSocket sendFd tries: $tries")
                LocalSocket().use { localSocket ->
                    localSocket.connect(LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM))
                    localSocket.setFileDescriptorsForSend(arrayOf(fd))
                    localSocket.outputStream.write(42)
                }
                break
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to send file descriptor, try: $tries", e)
                if (tries > 5) break
                tries += 1
            }
        }
    }

    /**
     * Stops the tun2socks process
     */
    override fun stopTun2Socks() {
        try {
            Log.i(AppConfig.TAG, "$TUN2SOCKS destroy")
            if (::process.isInitialized) {
                process.destroy()
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to destroy $TUN2SOCKS process", e)
        }
    }
}
