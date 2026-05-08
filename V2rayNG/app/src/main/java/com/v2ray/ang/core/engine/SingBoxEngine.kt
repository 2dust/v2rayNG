package com.v2ray.ang.core.engine

import android.content.Context
import android.os.SystemClock
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.service.ProcessService
import com.v2ray.ang.util.LogUtil
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

class SingBoxEngine(
    private val _eventHandler: CoreEventHandler,
) : CoreEngine {
    override val type = CoreType.SING_BOX

    /**
     * The runtime is not wired into production flows yet, so we expose no
     * active capabilities until the bootstrap and validation work lands.
     */
    override val supportedCapabilities: Set<CoreCapability> = emptySet()

    override val isRunning: Boolean
        get() = started || processService.isRunning()

    private val binaryInstaller = SingBoxBinaryInstaller()
    private val processService = ProcessService()
    private var runtimeLayout: SingBoxRuntimeLayout? = null
    private var processFinder: AppProcessFinder? = null
    private var appContext: Context? = null
    private var started = false

    override fun initCoreEnv(context: Context?) {
        if (context == null) return

        appContext = context.applicationContext
        runtimeLayout = SingBoxRuntimeLayout.fromContext(context.applicationContext).also {
            it.ensureDirectories()
        }
        // Binary install can be tens of MB from assets; keep it out of Service.onCreate / main thread.
        // [SingBoxEngine.startLoop] runs on CoreLoopStart thread via CoreServiceManager.startCoreLoopAsync.
        LogUtil.i(AppConfig.TAG, "Sing-box runtime layout initialized at ${runtimeLayout?.rootDir}")
    }

    override fun startLoop(configContent: String, tunFd: Int) {
        val context = appContext ?: throw IllegalStateException("Sing-box engine context is not initialized")
        val layout = runtimeLayout ?: throw IllegalStateException("Sing-box runtime layout is not initialized")

        layout.ensureDirectories()
        layout.logFile.parentFile?.mkdirs()
        if (layout.logFile.exists()) {
            layout.logFile.writeText("")
        }
        layout.configFile.writeText(configContent)

        if (tunFd != 0) {
            LogUtil.i(AppConfig.TAG, "Sing-box skeleton received tunFd=$tunFd; TUN handoff will be wired in a follow-up patch")
        }
        if (processFinder != null) {
            LogUtil.d(AppConfig.TAG, "Sing-box skeleton has a process finder registered for future per-app routing support")
        }
        ensureBinaryInstalled()
        require(layout.binaryFile.exists()) {
            "Sing-box binary is not installed. Expected apk assets in: ${binaryInstaller.expectedAssetLocations().joinToString()}"
        }

        LogUtil.i(AppConfig.TAG, "Sing-box bootstrap command prepared: ${buildStartCommand(layout)}")
        processService.stopProcess()
        processService.runProcess(
            context = context,
            cmd = buildStartCommand(layout),
            workingDirectory = layout.workingDir,
            logFile = layout.logFile,
        )
        started = waitForProcessAlive(timeoutMs = 12_000L)
        if (!started) {
            val logTail = readStartupLogTail(layout)
            throw IllegalStateException(
                buildString {
                    append("Sing-box process failed to start")
                    if (logTail.isNotBlank()) {
                        append(": ")
                        append(logTail)
                    }
                }
            )
        }
    }

    override fun stopLoop() {
        processService.stopProcess()
        started = false
    }

    override fun queryStats(tag: String, link: String): Long {
        LogUtil.d(AppConfig.TAG, "Sing-box stats query requested before runtime implementation: $tag/$link")
        return 0L
    }

    override fun measureDelay(testUrl: String): Long {
        val url = runCatching { URL(testUrl) }.getOrElse { error ->
            LogUtil.e(AppConfig.TAG, "Invalid sing-box delay test url: $testUrl", error)
            return -1L
        }
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", AppConfig.PORT_SOCKS.toInt()))
        val startAt = System.nanoTime()

        return runCatching {
            val connection = (url.openConnection(proxy) as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 5000
                readTimeout = 5000
                useCaches = false
            }
            connection.responseCode
            connection.inputStream.use { it.read() }
            ((System.nanoTime() - startAt) / 1_000_000L).coerceAtLeast(0L)
        }.onFailure { error ->
            LogUtil.e(AppConfig.TAG, "Sing-box delay test failed for $testUrl", error)
        }.getOrElse { error ->
            throw IllegalStateException(
                error.message ?: appContext?.getString(R.string.toast_failure) ?: "Failure",
                error
            )
        }
    }

    override fun registerProcessFinder(processFinder: AppProcessFinder) {
        this.processFinder = processFinder
    }

    internal fun buildStartCommand(layout: SingBoxRuntimeLayout = runtimeLayout ?: error("Sing-box runtime layout is not initialized")): MutableList<String> {
        return mutableListOf(
            layout.binaryFile.absolutePath,
            "run",
            "-c",
            layout.configFile.absolutePath,
        )
    }

    private fun ensureBinaryInstalled() {
        val context = appContext ?: return
        val layout = runtimeLayout ?: return
        if (layout.binaryFile.exists()) {
            return
        }
        if (!binaryInstaller.install(context, layout)) {
            LogUtil.i(AppConfig.TAG, "No bundled sing-box binary found in assets yet")
        }
    }

    /** Busy waits with short sleeps; must run off the main thread (see [CoreServiceManager.startCoreLoopAsync]). */
    private fun waitForProcessAlive(timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (processService.isRunning()) return true
            try {
                Thread.sleep(50L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return processService.isRunning()
            }
        }
        return processService.isRunning()
    }

    private fun readStartupLogTail(layout: SingBoxRuntimeLayout): String {
        return runCatching {
            if (!layout.logFile.exists()) return ""
            layout.logFile.readText().trim().takeLast(1500)
        }.getOrDefault("")
    }
}
