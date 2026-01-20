package com.v2ray.ang.viewmodel

import androidx.lifecycle.ViewModel
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import java.io.IOException

class LogcatViewModel : ViewModel() {
    private val logsetsAll: MutableList<String> = mutableListOf()
    private var filteredLogs: List<String> = emptyList()
    private var currentFilter: String = ""

    fun getAll(): List<String> = filteredLogs

    fun loadLogcat() {
        try {
            val lst = LinkedHashSet<String>()
            lst.add("logcat")
            lst.add("-d")
            lst.add("-v")
            lst.add("time")
            lst.add("-s")
            lst.add("GoLog,${ANG_PACKAGE},AndroidRuntime,System.err")
            val process = Runtime.getRuntime().exec(lst.toTypedArray())
            val allText = process.inputStream.bufferedReader().use { it.readLines() }.reversed()

            logsetsAll.clear()
            logsetsAll.addAll(allText)
            applyFilter()
        } catch (e: IOException) {
            android.util.Log.e(AppConfig.TAG, "Failed to get logcat", e)
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
            filteredLogs = emptyList()
        } catch (e: IOException) {
            android.util.Log.e(AppConfig.TAG, "Failed to clear logcat", e)
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
