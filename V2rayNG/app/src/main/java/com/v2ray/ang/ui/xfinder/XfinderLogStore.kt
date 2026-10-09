package com.v2ray.ang.ui.xfinder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-memory ring of connection / fetch / error lines for the log screen.
 * Never receives share links or credentials: callers log counts, protocols and host:port only.
 */
object XfinderLogStore {
    const val MAX_LINES = 500

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun info(message: String) = append("INFO", message)

    fun error(message: String) = append("ERROR", message)

    fun clear() {
        _lines.value = emptyList()
    }

    private fun append(level: String, message: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val line = formatLine(stamp, level, message)
        _lines.update { (it + line).takeLast(MAX_LINES) }
    }

    internal fun formatLine(stamp: String, level: String, message: String): String =
        "[$stamp] $level  $message"
}
