package com.v2ray.ang.core

import android.content.Context
import androidx.annotation.RequiresApi
import com.v2ray.ang.dto.entities.AetherSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/** One daemon-owned native process. Identity files belong to Aether, not to app settings. */
@RequiresApi(26)
class AetherSession {
    private var process: Process? = null

    suspend fun start(context: Context, guid: String, settings: AetherSettings) = withContext(Dispatchers.IO) {
        check(process == null)
        require(settings.isValid())
        require(guid.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        val core = File(context.applicationInfo.nativeLibraryDir, "libaether-core.so")
        val launcher = File(context.applicationInfo.nativeLibraryDir, "libaether-launcher.so")
        check(core.isFile && launcher.isFile) { "Aether core is unavailable" }
        val port = settings.socksPort.toInt()
        ServerSocket(port, 1, InetAddress.getByName("127.0.0.1")).use { }
        val identityDirectory = File(context.noBackupFilesDir, "aether/$guid")
        check(identityDirectory.isDirectory || identityDirectory.mkdirs())
        val command = listOf(launcher.absolutePath, core.absolutePath) +
            settings.arguments(File(identityDirectory, "identity.toml").absolutePath)
        val builder = ProcessBuilder(command).directory(identityDirectory).redirectErrorStream(true)
            .redirectOutput(File("/dev/null"))
        builder.environment().keys.removeAll { it.startsWith("AETHER_") }
        builder.environment()["AETHER_ECH"] = "0"
        val child = builder.start()
        process = child
        child.outputStream.close()
        try {
            withTimeout(180_000) {
                while (true) {
                    check(child.isAlive) { "Aether stopped during startup" }
                    if (proxyReady(port)) break
                    delay(200)
                }
            }
        } catch (e: Exception) {
            child.destroyForcibly()
            throw e
        }
    }

    fun isAlive(): Boolean = process?.isAlive == true

    suspend fun stop() = withContext(Dispatchers.IO) {
        val child = process ?: return@withContext
        child.destroyForcibly()
        withTimeout(3_000) {
            while (child.isAlive) delay(20)
        }
        child.inputStream.close()
        child.errorStream.close()
        child.outputStream.close()
        process = null
    }

    private fun proxyReady(port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.soTimeout = 500
            socket.connect(InetSocketAddress("127.0.0.1", port), 500)
            socket.getOutputStream().write(byteArrayOf(5, 1, 0))
            socket.getOutputStream().flush()
            val input = socket.getInputStream()
            input.read() == 5 && input.read() == 0
        }
    }.getOrDefault(false)
}
