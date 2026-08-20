package com.v2ray.ang.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.v2ray.ang.extension.delay
import kotlinx.coroutines.flow.collectLatest

data class ScrollbarConfig(
    val thickness: Dp = 4.dp,
    val minThumbSize: Dp = 24.dp,
    val thumbColor: Color = Color.Unspecified,
    val trackColor: Color = Color.Transparent,
    val padding: Dp = 2.dp,
    val cornerRadius: Dp = 2.dp,
    val fadeOutDurationMs: Int = 1500,
    val fadeAnimDurationMs: Int = 300,
)

private data class ScrollbarThumb(val offset: Float, val length: Float, val trackLength: Float)

@Composable
private fun <T> rememberScrollbarAlpha(
    key: Any,
    config: ScrollbarConfig,
    position: () -> T,
): State<Float> {
    val alpha = remember(key) { Animatable(0f) }
    val lifecycleState = LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    LaunchedEffect(key, config.fadeOutDurationMs, config.fadeAnimDurationMs) {
        snapshotFlow(position).collectLatest {
            alpha.snapTo(1f)
            delay(config.fadeOutDurationMs.toLong())
            alpha.animateTo(0f, tween(config.fadeAnimDurationMs))
        }
    }
    return remember(alpha, lifecycleState) {
        derivedStateOf {
            if (lifecycleState.value == Lifecycle.State.RESUMED) alpha.value else 0f
        }
    }
}

@Composable
private fun scrollbarThumbColor(config: ScrollbarConfig): Color =
    if (config.thumbColor == Color.Unspecified) MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
    else config.thumbColor

private fun calculateThumb(
    viewport: Float,
    contentLength: Float,
    scrolled: Float,
    minLength: Float,
): ScrollbarThumb? {
    if (viewport <= 0f || contentLength <= viewport) return null
    val length = (viewport * viewport / contentLength).coerceAtLeast(minLength).coerceAtMost(viewport * 0.5f)
    val offset = (scrolled / (contentLength - viewport)).coerceIn(0f, 1f) * (viewport - length)
    return ScrollbarThumb(offset, length, viewport)
}

private fun DrawScope.drawVerticalScrollbar(
    thumb: ScrollbarThumb,
    alpha: Float,
    config: ScrollbarConfig,
    thumbColor: Color,
) {
    val thickness = config.thickness.toPx()
    val padding = config.padding.toPx()
    val radius = CornerRadius(config.cornerRadius.toPx())
    val x = if (layoutDirection == LayoutDirection.Rtl) padding else size.width - thickness - padding
    if (config.trackColor != Color.Transparent) {
        drawRoundRect(
            config.trackColor.copy(alpha = config.trackColor.alpha * alpha),
            Offset(x, 0f),
            Size(thickness, thumb.trackLength),
            radius
        )
    }
    drawRoundRect(
        thumbColor.copy(alpha = thumbColor.alpha * alpha),
        Offset(x, thumb.offset),
        Size(thickness, thumb.length),
        radius
    )
}

private fun DrawScope.drawHorizontalScrollbar(
    thumb: ScrollbarThumb,
    alpha: Float,
    config: ScrollbarConfig,
    thumbColor: Color,
) {
    val thickness = config.thickness.toPx()
    val y = size.height - thickness - config.padding.toPx()
    val radius = CornerRadius(config.cornerRadius.toPx())
    if (config.trackColor != Color.Transparent) {
        drawRoundRect(
            config.trackColor.copy(alpha = config.trackColor.alpha * alpha),
            Offset(0f, y),
            Size(thumb.trackLength, thickness),
            radius
        )
    }
    drawRoundRect(
        thumbColor.copy(alpha = thumbColor.alpha * alpha),
        Offset(thumb.offset, y),
        Size(thumb.length, thickness),
        radius
    )
}

fun Modifier.verticalScrollbar(scrollState: ScrollState, config: ScrollbarConfig = ScrollbarConfig()): Modifier = composed {
    val alpha = rememberScrollbarAlpha(scrollState, config) { scrollState.value }
    val thumbColor = scrollbarThumbColor(config)
    drawWithContent {
        drawContent()
        if (alpha.value <= 0f) return@drawWithContent
        val viewport = size.height
        val contentLength = viewport + scrollState.maxValue
        val thumb = calculateThumb(viewport, contentLength, scrollState.value.toFloat(), config.minThumbSize.toPx())
            ?: return@drawWithContent
        drawVerticalScrollbar(thumb, alpha.value, config, thumbColor)
    }
}

fun Modifier.horizontalScrollbar(scrollState: ScrollState, config: ScrollbarConfig = ScrollbarConfig()): Modifier = composed {
    val alpha = rememberScrollbarAlpha(scrollState, config) { scrollState.value }
    val thumbColor = scrollbarThumbColor(config)
    drawWithContent {
        drawContent()
        if (alpha.value <= 0f) return@drawWithContent
        val viewport = size.width
        val contentLength = viewport + scrollState.maxValue
        val thumb = calculateThumb(viewport, contentLength, scrollState.value.toFloat(), config.minThumbSize.toPx())
            ?: return@drawWithContent
        drawHorizontalScrollbar(thumb, alpha.value, config, thumbColor)
    }
}

fun Modifier.verticalScrollbar(lazyListState: LazyListState, config: ScrollbarConfig = ScrollbarConfig()): Modifier = composed {
    val alpha = rememberScrollbarAlpha(lazyListState, config) {
        lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset
    }
    val thumbColor = scrollbarThumbColor(config)
    drawWithContent {
        drawContent()
        val layout = lazyListState.layoutInfo
        val visible = layout.visibleItemsInfo
        if (alpha.value <= 0f || visible.isEmpty()) return@drawWithContent
        val viewport = layout.viewportSize.height.toFloat()
        val averageItemHeight = visible.sumOf { it.size } / visible.size.toFloat()
        val scrolled = visible.first().index * averageItemHeight + layout.viewportStartOffset - visible.first().offset
        val contentLength = averageItemHeight * layout.totalItemsCount
        val thumb = calculateThumb(viewport, contentLength, scrolled, config.minThumbSize.toPx())
            ?: return@drawWithContent
        drawVerticalScrollbar(thumb, alpha.value, config, thumbColor)
    }
}

fun Modifier.verticalScrollbar(lazyGridState: LazyGridState, config: ScrollbarConfig = ScrollbarConfig()): Modifier = composed {
    val alpha = rememberScrollbarAlpha(lazyGridState, config) {
        lazyGridState.firstVisibleItemIndex to lazyGridState.firstVisibleItemScrollOffset
    }
    val thumbColor = scrollbarThumbColor(config)
    drawWithContent {
        drawContent()
        val layout = lazyGridState.layoutInfo
        val visible = layout.visibleItemsInfo
        if (alpha.value <= 0f || visible.isEmpty()) return@drawWithContent
        val rows = visible.groupBy { it.row }
        val averageRowHeight = rows.values.map { row -> row.maxOf { it.size.height } }.average().toFloat()
        val columns = rows.values.first().size
        val totalRows = (layout.totalItemsCount + columns - 1) / columns
        val first = visible.first()
        val scrolled = first.row * averageRowHeight + layout.viewportStartOffset - first.offset.y
        val viewport = layout.viewportSize.height.toFloat()
        val contentLength = averageRowHeight * totalRows
        val thumb = calculateThumb(viewport, contentLength, scrolled, config.minThumbSize.toPx())
            ?: return@drawWithContent
        drawVerticalScrollbar(thumb, alpha.value, config, thumbColor)
    }
}
