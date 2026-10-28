package com.v2ray.ang.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.colorFabActive
import com.v2ray.ang.ui.compose.colorFabInactiveDark
import com.v2ray.ang.ui.compose.colorFabInactiveLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val KNOB_SIZE = 56.dp
private const val KNOB_SLIDE_MILLIS = 500
private const val TEXT_MAX_SP = 14f
private const val TEXT_MIN_SP = 10f
private const val TEXT_LINE_HEIGHT_FACTOR = 1.3f

/** How long the knob stays where the user sent it while the service has not confirmed the change. */
private const val KNOB_PENDING_TIMEOUT_MILLIS = 6_000L

/**
 * Connection status bar: a rounded track with the status message in the middle and the round
 * connect button as a knob. The knob rests at the start edge while disconnected and slides to the
 * end edge when the connection is turned on (mirrored in right-to-left languages). Tapping the
 * track still tests the current server.
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
    // Where the user just sent the knob, until the service confirms (or the wait times out).
    var pendingRunning by remember { mutableStateOf<Boolean?>(null) }
    val knobAtEnd = pendingRunning ?: isRunning

    LaunchedEffect(isRunning) {
        pendingRunning = null
        if (!isRunning) {
            rotationAnim.snapTo(0f)
        }
    }
    LaunchedEffect(pendingRunning) {
        if (pendingRunning != null) {
            delay(KNOB_PENDING_TIMEOUT_MILLIS)
            pendingRunning = null
        }
    }

    val progress by animateFloatAsState(
        targetValue = if (knobAtEnd) 1f else 0f,
        animationSpec = tween(durationMillis = KNOB_SLIDE_MILLIS, easing = FastOutSlowInEasing),
        label = "connectKnob"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = KNOB_SIZE),
            contentAlignment = Alignment.CenterStart
        ) {
            // The clipped layer is separate from the knob so the button's shadow is never cut off.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .shadow(elevation = 3.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = { onAction(MainAction.TestCurrentServer) })
            )
            // Disconnected the message is centered on the track; connected it moves to the start side
            // (left in LTR, right in RTL) next to the knob, which is then at the end.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = lerp(KNOB_SIZE + 8.dp, 20.dp, progress),
                        end = KNOB_SIZE + 8.dp,
                        top = 8.dp,
                        bottom = 8.dp
                    )
            ) {
                // Whatever the language, the message takes at most two lines of a fixed line height, shrinking the
                // font if needed, so the bar keeps the same height when the status text changes (for example after
                // a ping test, whose result is longer in some languages).
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
                    textAlign = if (progress > 0.5f) TextAlign.Start else TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { layout ->
                        if (layout.hasVisualOverflow && fontSizeSp > TEXT_MIN_SP) {
                            fontSizeSp = (fontSizeSp - 1f).coerceAtLeast(TEXT_MIN_SP)
                        }
                    },
                    // BiasAlignment mirrors in RTL: -1 is the start edge in both directions.
                    modifier = Modifier
                        .align(BiasAlignment(horizontalBias = -progress, verticalBias = 0f))
                        .semantics { contentDescription = displayText }
                )
            }
            val travel = (maxWidth - KNOB_SIZE).coerceAtLeast(0.dp)
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
                    pendingRunning = !knobAtEnd
                    onAction(MainAction.ToggleService)
                },
                // offset() follows the layout direction, so the knob travels start -> end in LTR and RTL.
                modifier = Modifier
                    .offset(x = travel * progress)
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
