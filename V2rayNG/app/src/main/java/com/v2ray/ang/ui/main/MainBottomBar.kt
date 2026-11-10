package com.v2ray.ang.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.colorFabActive
import com.v2ray.ang.ui.compose.colorFabInactiveDark
import com.v2ray.ang.ui.compose.colorFabInactiveLight
import kotlinx.coroutines.launch

private val KNOB_SIZE = 56.dp
private const val TEXT_MAX_SP = 14f
private const val TEXT_MIN_SP = 10f
private const val TEXT_LINE_HEIGHT_FACTOR = 1.3f

/**
 * Connection status bar: a rounded track with the status message and the round connect button.
 *
 * The button always sits on the physical right edge, in left-to-right and right-to-left languages
 * alike, and never moves, so the bar can be used with one hand. The message is start-aligned (left
 * in LTR, right in RTL, next to the button). Tapping the track tests the current server.
 */
@Composable
fun MainBottomBar(
    displayText: String,
    isRunning: Boolean,
    isDarkTheme: Boolean,
    onAction: (MainAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val rotationAnim = remember { Animatable(0f) }
    val textLayoutDirection = LocalLayoutDirection.current

    LaunchedEffect(isRunning) {
        if (!isRunning) {
            rotationAnim.snapTo(0f)
        }
    }

    // The track is tall enough for two lines of text at the largest size (at the current font scale), so
    // its height is the same whether the message takes one line or two, in every language.
    val twoLinesHeight = with(LocalDensity.current) {
        (TEXT_MAX_SP * TEXT_LINE_HEIGHT_FACTOR * 2).sp.toDp()
    }
    val trackHeight = maxOf(KNOB_SIZE + 4.dp, twoLinesHeight + 20.dp)
    val knobInset = (trackHeight - KNOB_SIZE) / 2

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp)
    ) {
        // Forced left-to-right so that the button stays on the physical right edge in every language;
        // the message below gets the real layout direction back.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
            ) {
                // The clipped layer is separate from the button so its shadow is never cut off.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .shadow(elevation = 3.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClick = { onAction(MainAction.TestCurrentServer) })
                )
                CompositionLocalProvider(LocalLayoutDirection provides textLayoutDirection) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth()
                            // Room for the button on the right; absolute so it does not flip with the language.
                            .absolutePadding(left = 20.dp, right = KNOB_SIZE + knobInset + 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // At most two lines of a fixed line height, shrinking the font if needed, so a longer
                        // message (for example a ping result in Persian) never makes the bar taller.
                        var fontSizeSp by remember(displayText) { mutableFloatStateOf(TEXT_MAX_SP) }
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = fontSizeSp.sp,
                                lineHeight = (fontSizeSp * TEXT_LINE_HEIGHT_FACTOR).sp,
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None
                                )
                            ),
                            textAlign = TextAlign.Start,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { layout ->
                                if (layout.hasVisualOverflow && fontSizeSp > TEXT_MIN_SP) {
                                    fontSizeSp = (fontSizeSp - 1f).coerceAtLeast(TEXT_MIN_SP)
                                }
                            },
                            modifier = Modifier.semantics { contentDescription = displayText }
                        )
                    }
                }
                FloatingActionButton(
                    onClick = {
                        if (!isRunning) {
                            scope.launch {
                                rotationAnim.animateTo(
                                    targetValue = 720f,
                                    animationSpec = tween(durationMillis = 3000)
                                )
                            }
                        }
                        onAction(MainAction.ToggleService)
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = knobInset)
                        .size(KNOB_SIZE),
                    shape = CircleShape,
                    containerColor = if (isRunning) colorFabActive
                    else if (isDarkTheme) colorFabInactiveDark
                    else colorFabInactiveLight
                ) {
                    Icon(
                        painter = if (isRunning) painterResource(R.drawable.ic_stop_24dp)
                        else painterResource(R.drawable.ic_play_24dp),
                        contentDescription = stringResource(
                            if (isRunning) R.string.acc_stop else R.string.acc_start
                        ),
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { rotationZ = rotationAnim.value }
                    )
                }
            }
        }
    }
}
