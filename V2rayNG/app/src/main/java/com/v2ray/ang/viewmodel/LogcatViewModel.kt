package com.v2ray.ang.viewmodel

import androidx.lifecycle.ViewModel
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.util.LogUtil
import java.io.IOException

class LogcatViewModel : ViewModel() {
    private val logsetsAll: MutableList<String> = mutableListOf()
    private var filteredLogs: List<String> = emptyList()
    private var currentFilter: String = ""

    fun getAll(): List<String> = filteredLogs

    fun loadLogcat() {
        try {
            val allowedTags = listOf("GoLog", ANG_PACKAGE, "AndroidRuntime", "System.err")
            val tagFilter = allowedTags.joinToString(",") { tag ->
                require(tag.matches(Regex("[A-Za-z0-9_.:]+"))) { "Invalid logcat tag: $tag" }
                tag
            }
            val cmd = listOf("logcat", "-d", "-v", "time", "-s", tagFilter)
            val process = ProcessBuilder(cmd)
                .redirectErrorStream(false)
                .start()
            val allText = process.inputStream.bufferedReader().use { it.readLines() }.reversed()

            logsetsAll.clear()
            logsetsAll.addAll(allText)
            applyFilter()
        } catch (e: IOException) {
            LogUtil.e(AppConfig.TAG, "Failed to get logcat", e)
        }
    }

    fun clearLogcat() {
        try {
            val cmd = listOf("logcat", "-c")
            val process = ProcessBuilder(cmd)
                .redirectErrorStream(false)
                .start()
            process.waitFor()

            logsetsAll.clear()
            filteredLogs = emptyList()
        } catch (e: IOException) {
            LogUtil.e(AppConfig.TAG, "Failed to clear logcat", e)
        }
    }

    fun filter(content: String?) {
        currentFilter = content?.trim() ?: ""
        applyFilter()
    }

    private fun applyFilter() {
        filteredLogs = if (currentFilter.isEmpty()) {
            logsetsAll.toList()
        } else {
            logsetsAll.filter { it.contains(currentFilter) }
        }
    }
}
