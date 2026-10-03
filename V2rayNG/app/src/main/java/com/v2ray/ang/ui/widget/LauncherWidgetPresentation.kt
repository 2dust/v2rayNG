package com.v2ray.ang.ui.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

// Available dp, not cell spans: keep the compact layout small enough for dense phone grids.
internal enum class LauncherWidgetLayout(val minimumWidthDp: Float) {
    COMPACT(48f),
    HORIZONTAL(110f);

    val size get() = DpSize(minimumWidthDp.dp, 68.dp)

    companion object {
        fun forWidth(widthDp: Float): LauncherWidgetLayout =
            entries.lastOrNull { widthDp >= it.minimumWidthDp } ?: COMPACT
    }
}

internal data class LauncherWidgetUiState(
    val profileName: String,
    val isRunning: Boolean,
    val startActionLabel: String,
    val stopActionLabel: String,
)

internal fun LauncherWidgetState.present(context: Context) = LauncherWidgetUiState(
    profileName = profile?.name?.takeIf { it.isNotBlank() }
        ?: context.getString(R.string.widget_no_profile),
    isRunning = isRunning,
    startActionLabel = context.getString(R.string.acc_start),
    stopActionLabel = context.getString(R.string.acc_stop),
)
