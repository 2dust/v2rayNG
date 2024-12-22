package com.v2ray.ang.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityLogcatBinding
import com.v2ray.ang.extension.toast
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class LogcatActivity : BaseActivity() {
    private val binding by lazy { ActivityLogcatBinding.inflate(layoutInflater) }
    private val throttleManager = ThrottleManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        title = getString(R.string.title_logcat)
        logcat(false)
    }

    class ThrottleManager {
        private val throttleMap = mutableMapOf<String, Long>()

        companion object {
            private const val THROTTLE_DURATION = 1000L
        }

        @Synchronized
        fun shouldProcess(key: String): Boolean {
            val currentTime = System.currentTimeMillis()
            val lastProcessTime = throttleMap[key] ?: 0L

            return if (currentTime - lastProcessTime > THROTTLE_DURATION) {
                throttleMap[key] = currentTime
                true
            } else {
                false
            }
        }

        @Synchronized
        fun reset(key: String) {
            throttleMap.remove(key)
        }
    }

    private fun logcat(shouldFlushLog: Boolean) {
        binding.pbWaiting.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                if (shouldFlushLog) {
                    val lst = linkedSetOf("logcat", "-c")
                    withContext(Dispatchers.IO) {
                        val process = Runtime.getRuntime().exec(lst.toTypedArray())
                        process.waitFor()
                    }
                }

                val lst = linkedSetOf(
                    "logcat", "-d", "-v", "time", "-s",
                    "GoLog,tun2socks,$ANG_PACKAGE,AndroidRuntime,System.err"
                )

                val process = withContext(Dispatchers.IO) {
                    Runtime.getRuntime().exec(lst.toTypedArray())
                }

                val allLogs = process.inputStream.bufferedReader().use { it.readLines() }
                val filteredLogs = processLogs(allLogs)

                withContext(Dispatchers.Main) {
                    updateLogDisplay(filteredLogs)
                }

            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    binding.pbWaiting.visibility = View.GONE
                    toast(R.string.toast_failure)
                }
                e.printStackTrace()
            }
        }
    }

    private fun processLogs(logs: List<String>): List<String> {
        val processedLogs = mutableListOf<String>()
        var isNotMatch = false

        for (line in logs) {
            when {
                line.contains("zxing.NotFoundException", ignoreCase = true) -> {
                    if (!isNotMatch) {
                        if (throttleManager.shouldProcess("NotFoundException")) {
                            processedLogs.add(line)
                            isNotMatch = true
                        }
                    }
                }
                else -> processedLogs.add(line)
            }
        }

        return processedLogs.take(500)
    }

    private fun updateLogDisplay(logs: List<String>) {
        binding.tvLogcat.text = logs.joinToString("\n")
        binding.tvLogcat.movementMethod = ScrollingMovementMethod()
        binding.pbWaiting.visibility = View.GONE

        Handler(Looper.getMainLooper()).post {
            binding.svLogcat.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_logcat, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.copy_all -> {
            Utils.setClipboard(this, binding.tvLogcat.text.toString())
            toast(R.string.toast_success)
            true
        }
        R.id.clear_all -> {
            throttleManager.reset("zxing.NotFoundException")
            logcat(true)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
