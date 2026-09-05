package com.v2ray.ang.ui.logcat

import androidx.compose.runtime.Immutable
import com.v2ray.ang.ui.base.BaseAction
import com.v2ray.ang.ui.base.BaseEvent
import com.v2ray.ang.ui.base.BaseUiState

/** Separator of the `TAG(pid): message` shape produced by `logcat -v time`. */
private const val TAG_SEPARATOR = "):"

@Immutable
data class LogLine(
    val id: Long,
    val tag: String,
    val content: String,
    val raw: String
) : java.io.Serializable

@Immutable
data class LogcatUiState(
    val lines: List<LogLine> = emptyList(),
    val query: String = "",
    val searchActive: Boolean = false
) : BaseUiState

sealed interface LogcatAction : BaseAction {
    data object Back : LogcatAction
    data object Refresh : LogcatAction
    data object CopyAll : LogcatAction
    data object Clear : LogcatAction
    data object Share : LogcatAction

    data object SearchOpened : LogcatAction
    data object SearchClosed : LogcatAction
    data class QueryChanged(val value: String) : LogcatAction

    data class LineLongPressed(val text: String) : LogcatAction
    data class ShareFinished(val ok: Boolean) : LogcatAction
}

/** Platform capability; translated by [LogcatActivity.handlePlatformEvent]. */
sealed interface LogcatEvent : BaseEvent.Platform {
    data class ShareFile(val path: String) : LogcatEvent
}

/**
 * Splits `TAG(pid): message` once.
 */
internal fun parseLogLines(raw: List<String>): List<LogLine> {
    val result = ArrayList<LogLine>(raw.size)
    raw.forEachIndexed { index, line ->
        val marker = line.indexOf(TAG_SEPARATOR)
        val head = if (marker >= 0) line.substring(0, marker) else line
        val paren = head.indexOf('(')
        val tag = if (paren >= 0) head.substring(0, paren) else head
        val content = if (marker >= 0) {
            line.substring(marker + TAG_SEPARATOR.length).trim()
        } else {
            ""
        }
        result += LogLine(
            id = index.toLong(),
            tag = tag.trim(),
            content = content,
            raw = line
        )
    }
    return result
}
