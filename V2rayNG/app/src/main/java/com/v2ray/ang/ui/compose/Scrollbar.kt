package com.v2ray.ang.ui.compose

import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.nonInteractiveScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val ThumbAlpha = 0.7f
private const val ThumbMaxLengthFraction = 0.5f

@Immutable
data class ScrollbarConfig(
    val thickness: Dp = 4.dp,
    val minThumbSize: Dp = 24.dp,
    val thumbColor: Color = Color.Unspecified,
    val trackColor: Color = Color.Transparent,
    val padding: Dp = 2.dp,
    val cornerRadius: Dp = 2.dp,
    val fadeOutDurationMs: Int = 1500,
    val fadeAnimDurationMs: Int = 300
) {
    companion object {
        val Default = ScrollbarConfig()
    }
}

@Composable
private fun Modifier.m3Scrollbar(
    state: ScrollIndicatorState?,
    orientation: Orientation,
    config: ScrollbarConfig
): Modifier {
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val isResumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    if (!isResumed) return this

    val resolvedState = state ?: return this
    val thumbColor = if (config.thumbColor == Color.Unspecified) {
        MaterialTheme.colorScheme.secondary.copy(alpha = ThumbAlpha)
    } else {
        config.thumbColor
    }

    return this.then(
        Modifier.nonInteractiveScrollbar(
            state = resolvedState,
            orientation = orientation,
            thumbColor = thumbColor,
            trackColor = config.trackColor,
            thickness = config.thickness,
            thumbMinLength = config.minThumbSize,
            thumbMaxLengthFraction = ThumbMaxLengthFraction,
            isFadeEnabled = true,
            fadeDelayMillis = config.fadeOutDurationMs,
            fadeDurationMillis = config.fadeAnimDurationMs,
            mainAxisTrackInset = config.padding,
            crossAxisTrackInset = config.padding
        )
    )
}

@Composable
fun Modifier.verticalScrollbar(
    scrollState: ScrollState,
    config: ScrollbarConfig = ScrollbarConfig.Default
): Modifier = m3Scrollbar(scrollState.scrollIndicatorState, Orientation.Vertical, config)

@Composable
fun Modifier.horizontalScrollbar(
    scrollState: ScrollState,
    config: ScrollbarConfig = ScrollbarConfig.Default
): Modifier = m3Scrollbar(scrollState.scrollIndicatorState, Orientation.Horizontal, config)

@Composable
fun Modifier.verticalScrollbar(
    lazyListState: LazyListState,
    config: ScrollbarConfig = ScrollbarConfig.Default
): Modifier = m3Scrollbar(lazyListState.scrollIndicatorState, Orientation.Vertical, config)

@Composable
fun Modifier.verticalScrollbar(
    lazyGridState: LazyGridState,
    config: ScrollbarConfig = ScrollbarConfig.Default
): Modifier = m3Scrollbar(lazyGridState.scrollIndicatorState, Orientation.Vertical, config)
