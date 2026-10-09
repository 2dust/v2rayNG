package com.v2ray.ang.ui.main

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.DotGlobe
import com.v2ray.ang.ui.compose.XfPurpleLight
import com.v2ray.ang.ui.compose.pressScale
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.sin

/**
 * Connection hero of the main screen, after the design: a big timer on top, a dotted violet globe with a ring-shaped
 * connect button in its middle, and the status line under it. The ring turns and its halo pulses while the service
 * runs. A tap only reports [onToggle]; the main screen's action handler does the real work.
 */
@Composable
fun MainConnectHero(
    isRunning: Boolean,
    statusText: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    // Seconds the service has been running, counted from the moment this screen saw it start. The start time is
    // saved across rotation; the tick is aligned to the second boundary so the digits never skip.
    var startedAt by rememberSaveable { mutableStateOf(0L) }
    var elapsedSec by remember { mutableStateOf(0L) }
    LaunchedEffect(isRunning) {
        if (isRunning) {
            // 0 = not started; a start time in the future is stale (the device rebooted), so it restarts too
            if (startedAt == 0L || startedAt > SystemClock.elapsedRealtime()) startedAt = SystemClock.elapsedRealtime()
            while (true) {
                val spent = SystemClock.elapsedRealtime() - startedAt
                elapsedSec = spent / 1000L
                delay(1000L - spent % 1000L)
            }
        } else {
            startedAt = 0L
            elapsedSec = 0L
        }
    }

    // One turn of the ring per six seconds while running; only read while drawing, so no recomposition per frame.
    val ringAngle = remember { Animatable(0f) }
    LaunchedEffect(isRunning) {
        if (!isRunning) return@LaunchedEffect
        while (true) {
            ringAngle.snapTo(0f)
            ringAngle.animateTo(360f, tween(6000, easing = LinearEasing))
        }
    }

    val startLabel = stringResource(R.string.acc_start)
    val stopLabel = stringResource(R.string.acc_stop)
    val source = remember { MutableInteractionSource() }
    val ringMain = scheme.primary
    val ringLight = XfPurpleLight

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = formatElapsed(elapsedSec, scheme.onBackground, isRunning),
            style = MaterialTheme.typography.displaySmall,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp),
            contentAlignment = Alignment.Center,
        ) {
            DotGlobe(
                modifier = Modifier.fillMaxWidth().height(176.dp),
                radiusFraction = 0.5f,
                periodMs = 60000,
                dotColor = if (isRunning) ringLight else ringLight.copy(alpha = 0.7f),
                ringColor = ringMain,
            )
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .pressScale(source, 0.94f)
                    .drawBehind {
                        val stroke = 3.dp.toPx()
                        val radius = size.minDimension / 2f - stroke / 2f
                        if (isRunning) {
                            // pulsing halo, driven by the same angle as the turning ring
                            val pulse = 0.8f + 0.2f * sin(Math.toRadians(ringAngle.value * 2.0)).toFloat()
                            val haloR = size.minDimension * 0.9f
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(ringMain.copy(alpha = 0.5f * pulse), Color.Transparent),
                                    center = center,
                                    radius = haloR,
                                ),
                                radius = haloR,
                            )
                        }
                        drawCircle(ringLight.copy(alpha = 0.35f), radius = radius, style = Stroke(stroke))
                        if (isRunning) {
                            rotate(ringAngle.value) {
                                drawCircle(
                                    brush = Brush.sweepGradient(
                                        listOf(Color.Transparent, ringMain, ringLight, Color.Transparent)
                                    ),
                                    radius = radius,
                                    style = Stroke(stroke * 1.4f),
                                )
                            }
                        }
                    }
                    .padding(10.dp)
                    .clip(CircleShape)
                    .background(scheme.background)
                    .clickable(
                        interactionSource = source,
                        indication = null,
                        role = Role.Button,
                        onClick = onToggle,
                    )
                    .semantics { contentDescription = if (isRunning) stopLabel else startLabel },
                contentAlignment = Alignment.Center,
            ) {
                // Full-color logo: tinting the artwork would flatten it into a single-color disc.
                Image(
                    painter = painterResource(R.drawable.ic_matixray_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(88.dp),
                )
            }
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

/** "hh:mm:ss" with the seconds dimmed, like the timer of the design; dimmed as a whole while idle. */
private fun formatElapsed(totalSeconds: Long, color: Color, running: Boolean): AnnotatedString {
    val h = totalSeconds / 3600
    val m = totalSeconds % 3600 / 60
    val s = totalSeconds % 60
    val main = color.copy(alpha = if (running) 1f else 0.5f)
    val dim = color.copy(alpha = if (running) 0.45f else 0.3f)
    return buildAnnotatedString {
        withStyle(SpanStyle(color = main)) { append(String.format(Locale.US, "%02d:%02d:", h, m)) }
        withStyle(SpanStyle(color = dim)) { append(String.format(Locale.US, "%02d", s)) }
    }
}
