package com.v2ray.ang.service

import android.content.Context
import android.util.Log
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProcessService {
    private val TAG = ANG_PACKAGE
    private lateinit var process: Process

    fun runProcess(context: Context, cmd: MutableList<String>) {
        Log.d(TAG, cmd.toString())

        try {
            val proBuilder = ProcessBuilder(cmd)
            proBuilder.redirectErrorStream(true)
            process = proBuilder
                .directory(context.filesDir)
                .start()

            CoroutineScope(Dispatchers.IO).launch {
                Thread.sleep(50L)
                Log.d(TAG, "runProcess check")
                process.waitFor()
                Log.d(TAG, "runProcess exited")
            }
            Log.d(TAG, process.toString())

        } catch (e: Exception) {
            Log.d(TAG, e.toString())
        }
    }

    fun stopProcess() {
        try {
            Log.d(TAG, "runProcess destroy")
            process?.destroy()
        } catch (e: Exception) {
            Log.d(TAG, e.toString())
        }
    }
}