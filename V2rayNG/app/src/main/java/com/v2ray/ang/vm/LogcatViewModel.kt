package com.v2ray.ang.vm

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogcatViewModel(application: Application) : BaseViewModel(application) {

    sealed interface LogcatViewModelEvent : ViewModelEvent {
        data class ShareLogcat(val uri: Uri, val title: String) : LogcatViewModelEvent
    }

    private val logsetsAll: MutableList<String> = mutableListOf()
    private var currentFilter: String = ""

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filteredLogs = MutableStateFlow<List<String>>(emptyList())
    val filteredLogs: StateFlow<List<String>> = _filteredLogs.asStateFlow()
    val logEntries: StateFlow<List<LogcatEntry>> = filteredLogs
        .map(::createLogcatEntries)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loadLogcat() {
        launchLoading {
            try {
                val lst = LinkedHashSet<String>()
                lst.add("logcat")
                lst.add("-d")
                lst.add("-v")
                lst.add("time")
                lst.add("-s")
                lst.add("GoLog,${AppConfig.ANG_PACKAGE},AndroidRuntime,System.err")
                val process = Runtime.getRuntime().exec(lst.toTypedArray())
                val allText = process.inputStream.bufferedReader().use { it.readLines() }.reversed()

                logsetsAll.clear()
                logsetsAll.addAll(allText)
                applyFilter()
            } catch (e: IOException) {
                LogUtil.e(AppConfig.TAG, "Failed to get logcat", e)
            }
        }
    }

    fun copyLogcat() {
        val all = filteredLogs.value.joinToString("\n")
        Utils.setClipboard(app, all)
        toast(R.string.toast_success)
    }

    fun clearLogcat() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lst = LinkedHashSet<String>()
                lst.add("logcat")
                lst.add("-c")
                val process = Runtime.getRuntime().exec(lst.toTypedArray())
                process.waitFor()

                logsetsAll.clear()
                _filteredLogs.value = emptyList()
            } catch (e: IOException) {
                LogUtil.e(AppConfig.TAG, "Failed to clear logcat", e)
            }
        }
    }

    fun shareLogcat(cacheDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val logText = filteredLogs.value.joinToString("\n")

            try {
                val shareDir = File(cacheDir, "shared_logs").apply {
                    mkdirs()
                }

                shareDir.listFiles()?.forEach { it.delete() }

                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val logFile = File(shareDir, "v2rayNG_logcat_$timestamp.txt")
                logFile.writeText(logText, Charsets.UTF_8)

                val uri = FileProvider.getUriForFile(
                    app,
                    "${app.packageName}.cache",
                    logFile
                )

                _viewModelEvent.send(LogcatViewModelEvent.ShareLogcat(uri, logFile.name))
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to share Logcat", e)
                toastError(R.string.toast_failure)
            }
        }
    }

    fun filter(content: String?) {
        _searchQuery.value = content ?: ""
        currentFilter = content?.trim() ?: ""
        applyFilter()
    }

    private fun applyFilter() {
        _filteredLogs.value = if (currentFilter.isEmpty()) {
            logsetsAll.toList()
        } else {
            logsetsAll.filter { it.contains(currentFilter) }
        }
    }
}

data class LogcatEntry(val key: String, val text: String)

internal fun createLogcatEntries(logs: List<String>): List<LogcatEntry> {
    val occurrences = HashMap<String, Int>()
    // Identical lines are valid. Count oldest-first so filtering other lines or
    // prepending newer entries does not change the keys of existing occurrences.
    return logs.asReversed().map { line ->
        val occurrence = occurrences.getOrDefault(line, 0)
        occurrences[line] = occurrence + 1
        LogcatEntry("$occurrence:$line", line)
    }.asReversed()
}
