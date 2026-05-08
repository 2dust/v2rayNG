package com.v2ray.ang.service

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

class ProcessService {
    private var process: Process? = null

    /**
     * Runs a process with the given command.
     * @param context The context.
     * @param cmd The command to run.
     */
    fun runProcess(
        context: Context,
        cmd: MutableList<String>,
        workingDirectory: File = context.filesDir,
        logFile: File? = null,
    ) {
        LogUtil.i(AppConfig.TAG, cmd.toString())

        try {
            val proBuilder = ProcessBuilder(cmd)
            proBuilder.redirectErrorStream(true)
            if (logFile != null) {
                logFile.parentFile?.mkdirs()
                proBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
            }
            process = proBuilder
                .directory(workingDirectory)
                .start()

            CoroutineScope(Dispatchers.IO).launch {
                Thread.sleep(50L)
                LogUtil.i(AppConfig.TAG, "runProcess check")
                process?.waitFor()
                LogUtil.i(AppConfig.TAG, "runProcess exited")
            }
            LogUtil.i(AppConfig.TAG, process.toString())

        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, e.toString(), e)
        }
    }

    fun isRunning(): Boolean {
        return process?.isAlive == true
    }

    /**
     * Stops the running process.
     */
    fun stopProcess() {
        val active = process ?: return
        process = null
        try {
            LogUtil.i(AppConfig.TAG, "runProcess destroy")
            active.destroy()
            val exited = active.waitFor(2L, TimeUnit.SECONDS)
            if (!exited && active.isAlive) {
                LogUtil.w(AppConfig.TAG, "runProcess destroy timed out; destroyForcibly")
                active.destroyForcibly()
                active.waitFor(500L, TimeUnit.MILLISECONDS)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to destroy process", e)
            runCatching {
                if (active.isAlive) active.destroyForcibly()
            }
        }
    }
}
