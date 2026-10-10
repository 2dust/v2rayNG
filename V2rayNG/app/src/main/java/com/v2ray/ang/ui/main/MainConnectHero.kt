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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.DotGlobe
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.ui.compose.GlassCardShape
import com.v2ray.ang.ui.compose.XfGreen
import com.v2ray.ang.ui.compose.XfPurpleLight
import com.v2ray.ang.ui.compose.liquidGlass
import com.v2ray.ang.ui.compose.pressScale
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.sin

/**
 * Connection hero of the main screen, after the design: a big timer on top, a network globe with a large round
 * connect button in its middle, the status line, and under it the live download, upload and ping. The ring turns and
 * its halo pulses while the service runs. A tap only reports [onToggle]; the main screen's action handler does the
 * real work. [accent] and [accentLight] color the ring, the halo and the globe: violet for the main section, orange
 * for the Psiphon section.
 */
@Composable
fun MainConnectHero(
    isRunning: Boolean,
    statusText: String,
    onToggle: () -> Unit,
    speed: TrafficSpeed,
    pingMillis: Long?,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    accentLight: Color = XfPurpleLight,
    globeHeight: Dp = 200.dp,
    buttonSize: Dp = 140.dp,
    onStatusClick: (() -> Unit)? = null,
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
    val ringMain = accent
    val ringLight = accentLight

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
                .height(globeHeight),
            contentAlignment = Alignment.Center,
        ) {
            DotGlobe(
                modifier = Modifier.fillMaxWidth().height(globeHeight).alpha(if (isRunning) 0.75f else 0.45f),
                radiusFraction = 0.5f,
                periodMs = 42000,
                dotColor = if (isRunning) ringLight else ringLight.copy(alpha = 0.7f),
                ringColor = ringMain,
            )
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .pressScale(source, 0.95f)
                    .drawBehind {
                        val stroke = 3.dp.toPx()
                        val radius = size.minDimension / 2f - stroke / 2f
                        if (isRunning) {
                            // soft pulsing halo, driven by the same angle as the turning arc
                            val pulse = 0.75f + 0.25f * sin(Math.toRadians(ringAngle.value * 2.0)).toFloat()
                            val haloR = size.minDimension * 0.85f
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(ringMain.copy(alpha = 0.32f * pulse), Color.Transparent),
                                    center = center,
                                    radius = haloR,
                                ),
                                radius = haloR,
                            )
                            // the idle outline, and over it a short bright arc that goes round
                            drawCircle(Color.White.copy(alpha = 0.10f), radius = radius, style = Stroke(stroke))
                            rotate(ringAngle.value) {
                                drawArc(
                                    color = ringLight,
                                    startAngle = -20f,
                                    sweepAngle = 110f,
                                    useCenter = false,
                                    topLeft = Offset(stroke / 2f, stroke / 2f),
                                    size = Size(size.width - stroke, size.height - stroke),
                                    style = Stroke(stroke, cap = StrokeCap.Round),
                                )
                            }
                        } else {
                            // idle: a faint outline and a soft glow of the accent under the filled button
                            val haloR = size.minDimension * 0.8f
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(ringMain.copy(alpha = 0.22f), Color.Transparent),
                                    center = center,
                                    radius = haloR,
                                ),
                                radius = haloR,
                            )
                        }
                    }
                    .padding(10.dp)
                    .clip(CircleShape)
                    // The kit's button: solid accent while disconnected, a dark disc while connected.
                    .background(if (isRunning) scheme.surfaceContainerHigh else ringMain)
                    .clickable(
                        interactionSource = source,
                        indication = null,
                        role = Role.Button,
                        onClick = onToggle,
                    )
                    .semantics { contentDescription = if (isRunning) stopLabel else startLabel },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_power_24dp),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(buttonSize * 0.4f),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            // Status dot as in the kit: green while connected, muted while not.
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isRunning) XfGreen else scheme.outline)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .then(
                        // A tap on the status line of a running tunnel tests the connection again; this took the place
                        // of the old status bar at the bottom of the screen.
                        if (onStatusClick != null && isRunning) Modifier.clickable(role = Role.Button, onClick = onStatusClick)
                        else Modifier
                    ),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val idle = stringResource(R.string.mx_stat_none)
            StatCard(
                iconRes = R.drawable.ic_arrow_down_24dp,
                label = stringResource(R.string.mx_stat_download),
                value = if (isRunning) speed.downBytesPerSec.toSpeedString() else idle,
                tint = ringLight,
            )
            StatCard(
                iconRes = R.drawable.ic_arrow_up_24dp,
                label = stringResource(R.string.mx_stat_upload),
                value = if (isRunning) speed.upBytesPerSec.toSpeedString() else idle,
                tint = ringLight,
            )
            StatCard(
                iconRes = R.drawable.ic_speed_24dp,
                label = stringResource(R.string.mx_stat_ping),
                value = if (isRunning && pingMillis != null) stringResource(R.string.mx_ping_value, pingMillis) else idle,
                tint = ringLight,
            )
        }
    }
}

/** One live number under the connect button, as in the kit: the name with a small colored mark on the right, the value below; one focusable node. */
@Composable
private fun RowScope.StatCard(iconRes: Int, label: String, value: String, tint: Color) {
    Column(
        modifier = Modifier
            .weight(1f)
            .liquidGlass(GlassCardShape)
            .semantics(mergeDescendants = true) { contentDescription = "$label: $value" }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
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
