package com.v2ray.ang.ui.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

internal enum class LauncherWidgetLayout(val minimumWidthDp: Float) {
    COMPACT(68f),
    MEDIUM(110f),
    WIDE(240f),
    EXTRA_WIDE(320f);

    val size get() = DpSize(minimumWidthDp.dp, 68.dp)

    companion object {
        fun forWidth(widthDp: Float): LauncherWidgetLayout =
            entries.lastOrNull { widthDp >= it.minimumWidthDp } ?: COMPACT
    }
}

internal enum class LauncherWidgetStatusTone { NORMAL, SUCCESS, ERROR }

internal data class LauncherWidgetTextMetrics(
    val profileFontSizeSp: Float,
    val statusFontSizeSp: Float,
    val startPaddingDp: Float,
    val endPaddingDp: Float,
    val verticalPaddingDp: Float,
)

internal fun launcherWidgetTextMetrics(fontScale: Float): LauncherWidgetTextMetrics =
    if (fontScale >= 1.8f) LauncherWidgetTextMetrics(12f, 8f, 4f, 2f, 0f)
    else LauncherWidgetTextMetrics(14f, 12f, 8f, 4f, 5f)

internal data class LauncherWidgetUiState(
    val profileName: String,
    val isRunning: Boolean,
    val canTest: Boolean,
    val serviceStatus: String,
    val connectionStatus: String,
    val connectionStatusTone: LauncherWidgetStatusTone,
    val startActionLabel: String,
    val stopActionLabel: String,
    val testActionLabel: String,
    val restartActionLabel: String,
)

internal fun LauncherWidgetState.present(context: Context): LauncherWidgetUiState {
    val status = context.getString(
        if (isConnected) R.string.widget_status_connected else R.string.widget_status_disconnected
    )
    val result = result
    val details = when {
        isTesting -> context.getString(R.string.connection_test_testing)
        result == null -> ""
        result.delayMillis < 0 -> result.errorMessage.ifBlank {
            context.getString(R.string.connection_test_empty_message)
        }
        else -> listOfNotNull(
            context.getString(R.string.server_test_delay_value, result.delayMillis),
            result.country?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(" · ")
    }
    return LauncherWidgetUiState(
        profileName = profile?.name?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.widget_no_profile),
        isRunning = isRunning,
        canTest = canTest,
        serviceStatus = status,
        connectionStatus = listOf(status, details).filter { it.isNotBlank() }.joinToString(" · "),
        connectionStatusTone = when {
            result == null -> LauncherWidgetStatusTone.NORMAL
            result.delayMillis >= 0 -> LauncherWidgetStatusTone.SUCCESS
            else -> LauncherWidgetStatusTone.ERROR
        },
        startActionLabel = context.getString(R.string.acc_start),
        stopActionLabel = context.getString(R.string.acc_stop),
        testActionLabel = context.getString(R.string.connection_test_pending),
        restartActionLabel = context.getString(R.string.title_service_restart),
    )
}
