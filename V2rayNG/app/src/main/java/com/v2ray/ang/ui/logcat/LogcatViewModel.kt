package com.v2ray.ang.ui.logcat

import android.app.Application
import com.v2ray.ang.AppConfig
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

class LogcatViewModel(application: Application) : BaseViewModel(application) {
    private val logsetsAll: MutableList<String> = mutableListOf()
    private var currentFilter: String = ""

    private val _filteredLogs = MutableStateFlow<List<String>>(emptyList())
    val filteredLogs: StateFlow<List<String>> = _filteredLogs.asStateFlow()

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

    fun clearLogcat() {
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

    fun filter(content: String?) {
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