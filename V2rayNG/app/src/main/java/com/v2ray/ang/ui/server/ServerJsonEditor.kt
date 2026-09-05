package com.v2ray.ang.ui.server

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.horizontalScrollbar
import com.v2ray.ang.ui.compose.verticalScrollbar
import kotlinx.coroutines.flow.collectLatest

private object EditorDimens {
    val GutterPadding = 8.dp
    val ScrollbarThickness = 4.dp
    val ScrollbarPadding = 2.dp
    val ScrollPadding = 60.dp
    val EditorEndPadding = 24.dp
    val ScrollTailSpace = 36.dp
}

private const val GutterCacheLimit = 512

@Composable
internal fun ServerJsonEditor(
    remarks: String,
    rawContent: TextFieldState,
    onAction: (ServerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val baseStyle = MaterialTheme.typography.bodyMedium
    val editorColor = MaterialTheme.colorScheme.onSurface
    val gutterColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val editorStyle = remember(baseStyle, editorColor) {
        baseStyle.copy(fontFamily = FontFamily.Monospace, color = editorColor)
    }
    val placeholderStyle = remember(editorStyle) {
        editorStyle.copy(color = editorColor.copy(alpha = 0.38f))
    }
    val gutterStyle = remember(editorStyle, gutterColor) {
        editorStyle.copy(color = gutterColor, textAlign = TextAlign.End)
    }

    val layoutRef = remember { mutableStateOf<TextLayoutResult?>(null) }
    var lineCount by remember { mutableIntStateOf(1) }
    var contentHeightPx by remember { mutableIntStateOf(0) }
    var tailInsetPx by remember { mutableIntStateOf(0) }

    val gutterCache = remember(gutterStyle) { HashMap<Int, TextLayoutResult>() }

    val gutterWidth = remember(lineCount, gutterStyle, density, textMeasurer) {
        val digits = lineCount.toString().length.coerceAtLeast(1)
        val measured = textMeasurer.measure("0".repeat(digits), gutterStyle)
        with(density) { measured.size.width.toDp() + EditorDimens.GutterPadding * 2 }
    }
    val contentHeight: Dp = with(density) { contentHeightPx.coerceAtLeast(1).toDp() }
    val isEmpty by remember(rawContent) { derivedStateOf { rawContent.text.isEmpty() } }

    LaunchedEffect(rawContent, verticalScroll, horizontalScroll) {
        snapshotFlow {
            Triple(
                rawContent.selection,
                verticalScroll.viewportSize,
                horizontalScroll.viewportSize,
            )
        }.collectLatest { (selection, vViewport, hViewport) ->
            val layout = layoutRef.value ?: return@collectLatest
            val cursor = selection.start
            if (cursor < 0 || cursor > layout.layoutInput.text.length) return@collectLatest

            val pad = with(density) { EditorDimens.ScrollPadding.toPx() }

            if (vViewport > 0) {
                val line = layout.getLineForOffset(cursor)
                val lineTop = layout.getLineTop(line)
                val lineBottom = layout.getLineBottom(line)
                val scrollY = verticalScroll.value.toFloat()
                val bottomGuard = pad + tailInsetPx
                val target = when {
                    lineBottom > scrollY + vViewport - bottomGuard ->
                        (lineBottom - vViewport + bottomGuard).toInt()

                    lineTop < scrollY + pad ->
                        (lineTop - pad).toInt()

                    else -> null
                }
                target?.let {
                    verticalScroll.animateScrollTo(it.coerceIn(0, verticalScroll.maxValue))
                }
            }

            if (hViewport > 0) {
                val cursorX = layout.getHorizontalPosition(cursor, true)
                val scrollX = horizontalScroll.value.toFloat()
                val vw = hViewport.toFloat()
                val target = when {
                    cursorX < scrollX + pad -> (cursorX - pad).toInt()
                    cursorX > scrollX + vw - pad -> (cursorX - vw + pad).toInt()
                    else -> null
                }
                target?.let {
                    horizontalScroll.animateScrollTo(it.coerceIn(0, horizontalScroll.maxValue))
                }
            }
        }
    }

    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
    ) {
        FormTextField(
            label = stringResource(R.string.server_lab_remarks),
            value = remarks,
            onValueChange = { onAction(ServerAction.TextChanged(ServerField.REMARKS, it)) },
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScroll)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Canvas(
                        modifier = Modifier
                            .width(gutterWidth)
                            .height(contentHeight)
                    ) {
                        val padPx = EditorDimens.GutterPadding.toPx()
                        fun xFor(measured: TextLayoutResult) =
                            (size.width - padPx - measured.size.width).coerceAtLeast(0f)

                        if (gutterCache.size > GutterCacheLimit) gutterCache.clear()

                        val layout = layoutRef.value
                        if (layout == null || layout.lineCount <= 0) {
                            val measured = gutterCache.getOrPut(1) {
                                textMeasurer.measure("1", gutterStyle)
                            }
                            drawText(measured, topLeft = Offset(xFor(measured), 0f))
                            return@Canvas
                        }

                        val viewTop = verticalScroll.value.toFloat()
                        val viewHeight = verticalScroll.viewportSize
                            .takeIf { it > 0 }?.toFloat() ?: size.height
                        val first = layout.getLineForVerticalPosition(viewTop)
                        val last = layout
                            .getLineForVerticalPosition(viewTop + viewHeight)
                            .coerceAtMost(layout.lineCount - 1)

                        for (i in first..last) {
                            val number = i + 1
                            val measured = gutterCache.getOrPut(number) {
                                textMeasurer.measure(number.toString(), gutterStyle)
                            }
                            val y = layout.getLineBaseline(i) - measured.firstBaseline
                            drawText(measured, topLeft = Offset(xFor(measured), y))
                        }
                    }

                    CompositionLocalProvider(
                        LocalTextSelectionColors provides TextSelectionColors(
                            handleColor = MaterialTheme.colorScheme.secondary,
                            backgroundColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                        )
                    ) {
                        BasicTextField(
                            state = rawContent,
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(horizontalScroll)
                                .padding(end = EditorDimens.EditorEndPadding),
                            textStyle = editorStyle,
                            lineLimits = TextFieldLineLimits.MultiLine(),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
                            onTextLayout = { provider ->
                                provider()?.let { result ->
                                    layoutRef.value = result
                                    if (result.lineCount != lineCount) lineCount = result.lineCount
                                    if (result.size.height != contentHeightPx) {
                                        contentHeightPx = result.size.height
                                    }
                                }
                            },
                            decorator = { innerTextField ->
                                Box {
                                    if (isEmpty) Text(text = "{ }", style = placeholderStyle)
                                    innerTextField()
                                }
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(EditorDimens.ScrollTailSpace))
                Spacer(
                    modifier = Modifier
                        .windowInsetsBottomHeight(WindowInsets.safeDrawing)
                        .onSizeChanged { tailInsetPx = it.height }
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .width(EditorDimens.ScrollbarThickness + EditorDimens.ScrollbarPadding * 2)
                    .verticalScrollbar(scrollState = verticalScroll)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(start = gutterWidth)
                    .height(EditorDimens.ScrollbarThickness + EditorDimens.ScrollbarPadding * 2)
                    .horizontalScrollbar(scrollState = horizontalScroll)
            )
        }
    }
}
