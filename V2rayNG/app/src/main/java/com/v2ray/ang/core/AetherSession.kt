package com.v2ray.ang.core

import android.content.Context
import androidx.annotation.RequiresApi
import com.v2ray.ang.dto.entities.AetherSettings
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    private var outputJob: Job? = null
    @Volatile
    private var stage = AetherStartupStage.CORE
    @Volatile
    private var listenerReady = false

    suspend fun start(context: Context, guid: String, settings: AetherSettings,
        reservedPorts: Set<Int>, ownerScope: CoroutineScope, mode: String): Int = withContext(Dispatchers.IO) {
        check(process == null)
        require(settings.isValid())
        require(guid.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        val core = File(context.applicationInfo.nativeLibraryDir, "libaether-core.so")
        val launcher = File(context.applicationInfo.nativeLibraryDir, "libaether-launcher.so")
        check(core.isFile && launcher.isFile) { "Aether core is unavailable" }
        val port = (1..16).asSequence().map {
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        }.firstOrNull { it in 1024..65535 && it !in reservedPorts }
            ?: error("No internal Aether port available")
        val identityDirectory = File(context.noBackupFilesDir, "aether/$guid")
        check(identityDirectory.isDirectory || identityDirectory.mkdirs())
        val command = listOf(launcher.absolutePath, core.absolutePath) +
            settings.arguments(File(identityDirectory, "identity.toml").absolutePath, port)
        val builder = ProcessBuilder(command).directory(identityDirectory).redirectErrorStream(true)
        builder.environment().keys.removeAll { it.startsWith("AETHER_") }
        builder.environment()["AETHER_ECH"] = "0"
        builder.environment()["AETHER_LOG_LEVEL"] = "info"
        builder.environment()["RUST_LOG"] = "warn,aether=info"
        val child = builder.start()
        process = child
        stage = AetherStartupStage.CORE
        listenerReady = false
        val listenerPattern = Regex("socks5 server listening on 127\\.0\\.0\\.1:$port(?:\\s|$)")
        outputJob = ownerScope.launch(Dispatchers.IO + CoroutineName("AetherStartupOutput")) {
            // Bound the line buffer, discard all text after classification, and never persist it.
            try {
                child.inputStream.bufferedReader().use { reader ->
                    val buffer = CharArray(512)
                    val line = StringBuilder()
                    while (isActive) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        for (index in 0 until count) {
                            val char = buffer[index]
                            if (char == '\n') {
                                stage = stage.observe(line.toString())
                                if (listenerPattern.containsMatchIn(line)) listenerReady = true
                                line.setLength(0)
                            } else if (line.length < 1024) line.append(char)
                        }
                    }
                    if (line.isNotEmpty()) stage = stage.observe(line.toString())
                }
            } catch (e: Exception) {
                if (isActive && child.isAlive) {
                    LogUtil.e(AppConfig.TAG, "Aether: mode=$mode phase=startup guid=$guid output read failed", e)
                }
            }
        }
        child.outputStream.close()
        try {
            withTimeout(180_000) {
                while (true) {
                    if (!child.isAlive) {
                        withTimeout(1_000) { outputJob?.join() }
                        throw AetherStartupException(stage, child.exitValue())
                    }
                    if (listenerReady && proxyReady(port)) break
                    delay(200)
                }
            }
        } catch (e: TimeoutCancellationException) {
            child.destroyForcibly()
            throw AetherStartupException(stage)
        } catch (e: Exception) {
            child.destroyForcibly()
            throw e
        }
        port
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
        outputJob?.cancel()
        withTimeout(1_000) { outputJob?.join() }
        outputJob = null
        process = null
        listenerReady = false
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
