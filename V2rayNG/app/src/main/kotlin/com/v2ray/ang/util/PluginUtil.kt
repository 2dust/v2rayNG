package com.v2ray.ang.util

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.gson.Gson
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ServerConfig
import com.v2ray.ang.util.MmkvManager.settingsStorage
import com.v2ray.ang.util.fmt.Hysteria2Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

object PluginUtil {
    //private const val HYSTERIA2 = "hysteria2-plugin"
    private const val HYSTERIA2 = "libhysteria2.so"
    private const val packageName = "PluginUtil"
    private lateinit var process: Process

//    fun initPlugin(name: String): PluginManager.InitResult {
//        return PluginManager.init(name)!!
//    }

    fun runPlugin(context: Context, config: ServerConfig?) {
        Log.d(packageName, "runPlugin")

        val outbound = config?.getProxyOutbound() ?: return
        if (outbound.protocol.equals(EConfigType.HYSTERIA2.name, true)) {
            Log.d(packageName, "runPlugin $HYSTERIA2")

            val socksPort = 100 + Utils.parseInt(settingsStorage?.decodeString(AppConfig.PREF_SOCKS_PORT), AppConfig.PORT_SOCKS.toInt())
            val hy2Config = Hysteria2Fmt.toNativeConfig(config, socksPort) ?: return

            val configFile = File(context.noBackupFilesDir, "hy2_${SystemClock.elapsedRealtime()}.json")
            Log.d(packageName, "runPlugin ${configFile.absolutePath}")

            configFile.parentFile?.mkdirs()
            configFile.writeText(Gson().toJson(hy2Config))
            Log.d(packageName, Gson().toJson(hy2Config))

            runHy2(context, configFile)
        }
    }

    fun stopPlugin() {
        stopHy2()
    }

    private fun runHy2(context: Context, configFile: File) {
        val cmd = mutableListOf(
            File(context.applicationInfo.nativeLibraryDir, HYSTERIA2).absolutePath,
            //initPlugin(HYSTERIA2).path,
            "--disable-update-check",
            "--config",
            configFile.absolutePath,
            "--log-level",
            "warn",
            "client"
        )
        Log.d(packageName, cmd.toString())

        try {
            val proBuilder = ProcessBuilder(cmd)
            proBuilder.redirectErrorStream(true)
            process = proBuilder
                .directory(context.filesDir)
                .start()

            CoroutineScope(Dispatchers.IO).launch {
                Thread.sleep(500L)
                Log.d(packageName, "$HYSTERIA2 check")
                process.waitFor()
                Log.d(packageName, "$HYSTERIA2 exited")
            }
            Log.d(packageName, process.toString())

        } catch (e: Exception) {
            Log.d(packageName, e.toString())
        }
    }

    private fun stopHy2() {
        try {
            Log.d(packageName, "$HYSTERIA2 destroy")
            process?.destroy()
        } catch (e: Exception) {
            Log.d(packageName, e.toString())
        }
    }
}