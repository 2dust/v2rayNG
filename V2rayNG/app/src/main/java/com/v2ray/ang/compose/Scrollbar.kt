package com.v2ray.ang.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
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

fun Modifier.verticalScrollbar(
    scrollState: ScrollState,
    config: ScrollbarConfig = ScrollbarConfig(),
): Modifier = composed {
    val actualThumbColor = if (config.thumbColor == Color.Unspecified) {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
    } else {
        config.thumbColor
    }

    val alpha = remember { Animatable(0f) }
    val scrollChanged = remember {
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }
            .collect { scrollChanged.tryEmit(Unit) }
    }

    LaunchedEffect(Unit) {
        scrollChanged.collectLatest {
            alpha.snapTo(1f)
            kotlinx.coroutines.delay(config.fadeOutDurationMs.toLong())
            alpha.animateTo(0f, animationSpec = tween(config.fadeAnimDurationMs))
        }
    }

    drawWithContent {
        drawContent()

        val maxScroll = scrollState.maxValue.toFloat()
        if (maxScroll <= 0f) return@drawWithContent
        if (alpha.value <= 0f) return@drawWithContent

        val viewportHeight = size.height
        val contentHeight = viewportHeight + maxScroll
        val maxThumbHeight = viewportHeight * 0.5f
        val thumbHeight = (viewportHeight / contentHeight * viewportHeight)
            .coerceAtLeast(config.minThumbSize.toPx())
            .coerceAtMost(maxThumbHeight)
        val scrollFraction = scrollState.value / maxScroll
        val maxThumbOffset = viewportHeight - thumbHeight
        val thumbOffset = scrollFraction * maxThumbOffset

        val thickness = config.thickness.toPx()
        val padding = config.padding.toPx()
        val cornerRadius = config.cornerRadius.toPx()
        val currentAlpha = alpha.value

        if (config.trackColor != Color.Transparent) {
            drawRoundRect(
                color = config.trackColor.copy(alpha = config.trackColor.alpha * currentAlpha),
                topLeft = Offset(size.width - thickness - padding, 0f),
                size = Size(thickness, viewportHeight),
                cornerRadius = CornerRadius(cornerRadius)
            )
        }

        drawRoundRect(
            color = actualThumbColor.copy(alpha = actualThumbColor.alpha * currentAlpha),
            topLeft = Offset(size.width - thickness - padding, thumbOffset),
            size = Size(thickness, thumbHeight),
            cornerRadius = CornerRadius(cornerRadius)
        )
    }
}

fun Modifier.horizontalScrollbar(
    scrollState: ScrollState,
    config: ScrollbarConfig = ScrollbarConfig(),
): Modifier = composed {
    val actualThumbColor = if (config.thumbColor == Color.Unspecified) {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
    } else {
        config.thumbColor
    }

    val alpha = remember { Animatable(0f) }
    val scrollChanged = remember {
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }
            .collect { scrollChanged.tryEmit(Unit) }
    }

    LaunchedEffect(Unit) {
        scrollChanged.collectLatest {
            alpha.snapTo(1f)
            kotlinx.coroutines.delay(config.fadeOutDurationMs.toLong())
            alpha.animateTo(0f, animationSpec = tween(config.fadeAnimDurationMs))
        }
    }

    drawWithContent {
        drawContent()

        val maxScroll = scrollState.maxValue.toFloat()
        if (maxScroll <= 0f) return@drawWithContent
        if (alpha.value <= 0f) return@drawWithContent

        val viewportWidth = size.width
        val contentWidth = viewportWidth + maxScroll
        val maxThumbWidth = viewportWidth * 0.5f
        val thumbWidth = (viewportWidth / contentWidth * viewportWidth)
            .coerceAtLeast(config.minThumbSize.toPx())
            .coerceAtMost(maxThumbWidth)
        val scrollFraction = scrollState.value / maxScroll
        val maxThumbOffset = viewportWidth - thumbWidth
        val thumbOffset = scrollFraction * maxThumbOffset

        val thickness = config.thickness.toPx()
        val padding = config.padding.toPx()
        val cornerRadius = config.cornerRadius.toPx()
        val currentAlpha = alpha.value

        if (config.trackColor != Color.Transparent) {
            drawRoundRect(
                color = config.trackColor.copy(alpha = config.trackColor.alpha * currentAlpha),
                topLeft = Offset(0f, size.height - thickness - padding),
                size = Size(viewportWidth, thickness),
                cornerRadius = CornerRadius(cornerRadius)
            )
        }

        drawRoundRect(
            color = actualThumbColor.copy(alpha = actualThumbColor.alpha * currentAlpha),
            topLeft = Offset(thumbOffset, size.height - thickness - padding),
            size = Size(thumbWidth, thickness),
            cornerRadius = CornerRadius(cornerRadius)
        )
    }
}

fun Modifier.verticalScrollbar(
    lazyListState: LazyListState,
    config: ScrollbarConfig = ScrollbarConfig(),
): Modifier = composed {
    val actualThumbColor = if (config.thumbColor == Color.Unspecified) {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
    } else {
        config.thumbColor
    }

    val alpha = remember { Animatable(0f) }
    val scrollChanged = remember {
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset
        }.collect { scrollChanged.tryEmit(Unit) }
    }

    LaunchedEffect(Unit) {
        scrollChanged.collectLatest {
            alpha.snapTo(1f)
            kotlinx.coroutines.delay(config.fadeOutDurationMs.toLong())
            alpha.animateTo(0f, animationSpec = tween(config.fadeAnimDurationMs))
        }
    }

    drawWithContent {
        drawContent()

        val layoutInfo = lazyListState.layoutInfo
        val totalItems = layoutInfo.totalItemsCount
        if (totalItems == 0) return@drawWithContent

        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return@drawWithContent
        if (alpha.value <= 0f) return@drawWithContent

        val viewportHeight = layoutInfo.viewportSize.height.toFloat()
        val viewportStartOffset = layoutInfo.viewportStartOffset.toFloat()

        val averageItemHeight = visibleItems.sumOf { it.size } / visibleItems.size.toFloat()
        val estimatedTotalHeight = averageItemHeight * totalItems

        if (estimatedTotalHeight <= viewportHeight) return@drawWithContent

        val maxThumbHeight = viewportHeight * 0.5f
        val thumbHeight = (viewportHeight / estimatedTotalHeight * viewportHeight)
            .coerceAtLeast(config.minThumbSize.toPx())
            .coerceAtMost(maxThumbHeight)

        val firstItem = visibleItems.first()
        val scrolledPx = firstItem.index * averageItemHeight + (viewportStartOffset - firstItem.offset)
        val maxScroll = estimatedTotalHeight - viewportHeight
        val scrollFraction = (scrolledPx / maxScroll).coerceIn(0f, 1f)

        val maxThumbOffset = viewportHeight - thumbHeight
        val thumbOffset = scrollFraction * maxThumbOffset

        val thickness = config.thickness.toPx()
        val padding = config.padding.toPx()
        val cornerRadius = config.cornerRadius.toPx()
        val currentAlpha = alpha.value

        if (config.trackColor != Color.Transparent) {
            drawRoundRect(
                color = config.trackColor.copy(alpha = config.trackColor.alpha * currentAlpha),
                topLeft = Offset(size.width - thickness - padding, 0f),
                size = Size(thickness, viewportHeight),
                cornerRadius = CornerRadius(cornerRadius)
            )
        }

        drawRoundRect(
            color = actualThumbColor.copy(alpha = actualThumbColor.alpha * currentAlpha),
            topLeft = Offset(size.width - thickness - padding, thumbOffset),
            size = Size(thickness, thumbHeight),
            cornerRadius = CornerRadius(cornerRadius)
        )
    }
}

fun Modifier.verticalScrollbar(
    lazyGridState: LazyGridState,
    config: ScrollbarConfig = ScrollbarConfig(),
): Modifier = composed {
    val actualThumbColor = if (config.thumbColor == Color.Unspecified) {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
    } else {
        config.thumbColor
    }

    val alpha = remember { Animatable(0f) }
    val scrollChanged = remember {
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    }

    LaunchedEffect(lazyGridState) {
        snapshotFlow {
            lazyGridState.firstVisibleItemIndex to lazyGridState.firstVisibleItemScrollOffset
        }.collect { scrollChanged.tryEmit(Unit) }
    }

    LaunchedEffect(Unit) {
        scrollChanged.collectLatest {
            alpha.snapTo(1f)
            kotlinx.coroutines.delay(config.fadeOutDurationMs.toLong())
            alpha.animateTo(0f, animationSpec = tween(config.fadeAnimDurationMs))
        }
    }

    drawWithContent {
        drawContent()

        val layoutInfo = lazyGridState.layoutInfo
        val totalItems = layoutInfo.totalItemsCount
        if (totalItems == 0) return@drawWithContent

        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return@drawWithContent
        if (alpha.value <= 0f) return@drawWithContent

        val viewportHeight = layoutInfo.viewportSize.height.toFloat()
        val viewportStartOffset = layoutInfo.viewportStartOffset.toFloat()

        val rows = visibleItems.groupBy { it.row }
        val avgRowHeight = rows.values.map { row -> row.maxOf { it.size.height } }.average().toFloat()
        val columnsPerRow = rows.values.firstOrNull()?.size ?: 1
        val totalRows = (totalItems + columnsPerRow - 1) / columnsPerRow
        val estimatedTotalHeight = avgRowHeight * totalRows

        if (estimatedTotalHeight <= viewportHeight) return@drawWithContent

        val maxThumbHeight = viewportHeight * 0.5f
        val thumbHeight = (viewportHeight / estimatedTotalHeight * viewportHeight)
            .coerceAtLeast(config.minThumbSize.toPx())
            .coerceAtMost(maxThumbHeight)

        val firstItem = visibleItems.first()
        val firstRow = firstItem.row
        val scrolledPx = firstRow * avgRowHeight + (viewportStartOffset - firstItem.offset.y)
        val maxScroll = estimatedTotalHeight - viewportHeight
        val scrollFraction = (scrolledPx / maxScroll).coerceIn(0f, 1f)

        val maxThumbOffset = viewportHeight - thumbHeight
        val thumbOffset = scrollFraction * maxThumbOffset

        val thickness = config.thickness.toPx()
        val padding = config.padding.toPx()
        val cornerRadius = config.cornerRadius.toPx()
        val currentAlpha = alpha.value

        if (config.trackColor != Color.Transparent) {
            drawRoundRect(
                color = config.trackColor.copy(alpha = config.trackColor.alpha * currentAlpha),
                topLeft = Offset(size.width - thickness - padding, 0f),
                size = Size(thickness, viewportHeight),
                cornerRadius = CornerRadius(cornerRadius)
            )
        }

        drawRoundRect(
            color = actualThumbColor.copy(alpha = actualThumbColor.alpha * currentAlpha),
            topLeft = Offset(size.width - thickness - padding, thumbOffset),
            size = Size(thickness, thumbHeight),
            cornerRadius = CornerRadius(cornerRadius)
        )
    }
}
