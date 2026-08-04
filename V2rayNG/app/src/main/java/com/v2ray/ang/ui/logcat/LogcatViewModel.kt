package com.v2ray.ang.ui.logcat

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

class LogcatViewModel(application: Application) : BaseViewModel(application) {

    companion object {
        private const val TAGS = "GoLog,${AppConfig.ANG_PACKAGE},AndroidRuntime,System.err"

        /** Lines kept in memory; the oldest ones are dropped past this. */
        private const val MAX_LINES = 3000

        /** New lines are published in batches so a burst cannot flood recomposition. */
        private const val FLUSH_INTERVAL_MS = 300L
        private const val FLUSH_BATCH = 200
    }

    // Oldest first, the way a log reads: fresh lines are appended at the bottom
    private val logsetsAll: MutableList<String> = mutableListOf()
    private var currentFilter: String = ""

    private val _filteredLogs = MutableStateFlow<List<String>>(emptyList())
    val filteredLogs: StateFlow<List<String>> = _filteredLogs.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private var streamJob: Job? = null

    @Volatile
    private var logcatProcess: Process? = null

    /** Guards against a cancelled stream reporting its state over a newer one. */
    @Volatile
    private var streamGeneration = 0

    /** Set when the user pauses the stream, so returning to the screen does not restart it. */
    private var pausedByUser = false

    /**
     * Starts tailing logcat unless the user paused it.
     */
    fun onScreenResumed() {
        if (!pausedByUser) startStreaming()
    }

    /**
     * Stops the logcat process while the screen is not visible.
     */
    fun onScreenPaused() {
        stopStreaming()
    }

    /**
     * Pauses a running stream, or resumes a paused one.
     */
    fun toggleStreaming() {
        if (_isStreaming.value) {
            pausedByUser = true
            stopStreaming()
        } else {
            pausedByUser = false
            startStreaming()
        }
    }

    private fun startStreaming() {
        if (streamJob?.isActive == true) return

        _isStreaming.value = true
        val generation = ++streamGeneration
        streamJob = viewModelScope.launch(Dispatchers.IO) {
            // The stream starts with its own history, so old lines would be duplicated
            synchronized(logsetsAll) { logsetsAll.clear() }
            applyFilter()

            var process: Process? = null
            try {
                // Neither -d nor -T: logcat replays everything the buffer still holds for
                // these tags and then keeps following. With -T it would count raw lines
                // before filtering, so on a chatty device the screen opened empty and only
                // filled once the core logged something new.
                process = Runtime.getRuntime().exec(
                    arrayOf("logcat", "-v", "time", "-s", TAGS)
                )
                logcatProcess = process

                val reader = process.inputStream.bufferedReader()
                val pending = mutableListOf<String>()
                var lastFlush = System.currentTimeMillis()

                while (isActive) {
                    val line = reader.readLine() ?: break
                    pending.add(line)

                    val now = System.currentTimeMillis()
                    if (pending.size >= FLUSH_BATCH || now - lastFlush >= FLUSH_INTERVAL_MS) {
                        publish(pending)
                        pending.clear()
                        lastFlush = now
                    }
                }

                if (pending.isNotEmpty()) publish(pending)
            } catch (e: IOException) {
                LogUtil.e(AppConfig.TAG, "Failed to stream logcat", e)
            } finally {
                process?.destroy()
                if (logcatProcess === process) logcatProcess = null
                if (generation == streamGeneration) _isStreaming.value = false
            }
        }
    }

    private fun stopStreaming() {
        streamGeneration++
        streamJob?.cancel()
        streamJob = null
        // readLine() blocks until the process goes away, so cancelling alone is not enough
        logcatProcess?.destroy()
        logcatProcess = null
        _isStreaming.value = false
    }

    /**
     * Appends a batch of fresh lines, dropping the oldest ones past the cap.
     */
    private fun publish(lines: List<String>) {
        if (lines.isEmpty()) return
        synchronized(logsetsAll) {
            logsetsAll.addAll(lines)
            val overflow = logsetsAll.size - MAX_LINES
            if (overflow > 0) {
                logsetsAll.subList(0, overflow).clear()
            }
        }
        applyFilter()
    }

    fun copyLogcat() {
        val all = filteredLogs.value.joinToString("\n")
        Utils.setClipboard(app, all)
        toast(R.string.toast_success)
    }

    fun clearLogcat() {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-c"))
            process.waitFor()

            synchronized(logsetsAll) { logsetsAll.clear() }
            applyFilter()
        } catch (e: IOException) {
            LogUtil.e(AppConfig.TAG, "Failed to clear logcat", e)
        }
    }

    fun filter(content: String?) {
        currentFilter = content?.trim() ?: ""
        applyFilter()
    }

    private fun applyFilter() {
        val snapshot = synchronized(logsetsAll) { logsetsAll.toList() }
        _filteredLogs.value = if (currentFilter.isEmpty()) {
            snapshot
        } else {
            snapshot.filter { it.contains(currentFilter, ignoreCase = true) }
        }
    }

    override fun onCleared() {
        stopStreaming()
        super.onCleared()
    }
}
