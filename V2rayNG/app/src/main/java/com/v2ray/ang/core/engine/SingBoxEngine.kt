package com.v2ray.ang.core.engine

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.service.ProcessService
import com.v2ray.ang.util.LogUtil

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
        ensureBinaryInstalled()
        LogUtil.i(AppConfig.TAG, "Sing-box runtime layout initialized at ${runtimeLayout?.rootDir}")
    }

    override fun startLoop(configContent: String, tunFd: Int) {
        val context = appContext ?: throw IllegalStateException("Sing-box engine context is not initialized")
        val layout = runtimeLayout ?: throw IllegalStateException("Sing-box runtime layout is not initialized")

        layout.ensureDirectories()
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
        started = processService.isRunning()
        if (!started) {
            throw IllegalStateException("Sing-box process failed to start")
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
        LogUtil.d(AppConfig.TAG, "Sing-box delay test requested before runtime implementation: $testUrl")
        return -1L
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
}
