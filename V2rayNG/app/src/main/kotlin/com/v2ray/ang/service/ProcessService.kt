package com.v2ray.ang.service

import android.content.Context
import android.util.Log
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProcessService {
    private var process: Process? = null

    fun runProcess(context: Context, cmd: MutableList<String>) {
        Log.d(ANG_PACKAGE, cmd.toString())

        try {
            val proBuilder = ProcessBuilder(cmd)
            proBuilder.redirectErrorStream(true)
            process = proBuilder
                .directory(context.filesDir)
                .start()

            CoroutineScope(Dispatchers.IO).launch {
                Thread.sleep(50L)
                Log.d(ANG_PACKAGE, "runProcess check")
                process?.waitFor()
                Log.d(ANG_PACKAGE, "runProcess exited")
            }
            Log.d(ANG_PACKAGE, process.toString())

        } catch (e: Exception) {
            Log.d(ANG_PACKAGE, e.toString())
        }
    }

    fun stopProcess() {
        try {
            Log.d(ANG_PACKAGE, "runProcess destroy")
            process?.destroy()
        } catch (e: Exception) {
            Log.d(ANG_PACKAGE, e.toString())
        }
    }
}
