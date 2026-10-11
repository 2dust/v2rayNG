package com.v2ray.ang.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.ui.compose.GlassCardShape
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.XfGreen
import com.v2ray.ang.ui.compose.XfPurpleLight
import com.v2ray.ang.ui.compose.XfYellow
import com.v2ray.ang.ui.compose.liquidGlass
import com.v2ray.ang.ui.compose.pressScale
import kotlinx.coroutines.delay
import java.util.Locale

/** How long a tap shows the new state before the real state is trusted again, in case the start never happens (for example a refused VPN permission). */
private const val PENDING_TIMEOUT_MS = 5000L

/**
 * Connection hero of the main screen, after the Shadow VPN kit: the timer, the status line, one plain round power
 * button, flat as in the kit (no glow and nothing turning around it), and under it the live download, upload and ping. The time is passed in, so it does not restart when the
 * section changes, and it is read through a lambda so only the timer is redrawn each second. A tap flips the button at once and only then waits for the service; [onToggle] does the real work.
 * [accent] colors the button: violet for the main section, orange for the Psiphon section.
 */
@Composable
fun MainConnectHero(
    isRunning: Boolean,
    elapsedSec: () -> Long,
    statusText: String,
    onToggle: () -> Unit,
    speed: TrafficSpeed,
    pingMillis: Long?,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    accentLight: Color = XfPurpleLight,
    onStatusClick: (() -> Unit)? = null,
    bottomExtra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme

    // The button answers the tap in the same frame; the service state arrives a moment later and takes over.
    var pending by remember { mutableStateOf(false) }
    LaunchedEffect(isRunning) { pending = false }
    LaunchedEffect(pending) {
        if (pending) {
            delay(PENDING_TIMEOUT_MS)
            pending = false
        }
    }
    val shownRunning = if (pending) !isRunning else isRunning

    val startLabel = stringResource(R.string.acc_start)
    val stopLabel = stringResource(R.string.acc_stop)
    val source = remember { MutableInteractionSource() }

    // The page is one box: the timer and status at the top, the button in the exact middle of the page, the live
    // numbers (and whatever the caller adds) at the bottom, and a faint internet globe behind it all.
    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
        val buttonSize = (maxHeight * 0.30f).coerceIn(150.dp, 220.dp)

        // A picture drawn once and tinted, so it costs one bitmap draw per frame and never animates.
        Image(
            painter = painterResource(R.drawable.bg_globe),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alpha = 0.14f,
            colorFilter = ColorFilter.tint(accent),
            modifier = Modifier
                .align(Alignment.Center)
                .size(maxWidth * 1.15f),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = formatElapsed(if (isRunning) elapsedSec() else 0L, scheme.onBackground, isRunning),
                style = MaterialTheme.typography.displaySmall,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                // Status dot: green while connected, yellow while not.
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isRunning) XfGreen else XfYellow)
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
                            // A tap on the status line of a running tunnel tests the connection again.
                            if (onStatusClick != null && isRunning) Modifier.clickable(role = Role.Button, onClick = onStatusClick)
                            else Modifier
                        ),
                )
            }
        }

        // The button is the two pictures of the design, off and on; a tap is taken on press so it answers at once.
        Image(
            painter = painterResource(if (shownRunning) R.drawable.ic_btn_on else R.drawable.ic_btn_off),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.Center)
                .size(buttonSize)
                .pressScale(source, 0.96f)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            val press = PressInteraction.Press(offset)
                            source.tryEmit(press)
                            val released = tryAwaitRelease()
                            source.tryEmit(if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                        },
                        onTap = {
                            pending = true
                            onToggle()
                        },
                    )
                }
                .semantics(mergeDescendants = true) {
                    contentDescription = if (shownRunning) stopLabel else startLabel
                    role = Role.Button
                    onClick { pending = true; onToggle(); true }
                },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val idle = stringResource(R.string.mx_stat_none)
                StatCard(
                    iconRes = R.drawable.ic_arrow_down_24dp,
                    label = stringResource(R.string.mx_stat_download),
                    value = if (isRunning) speed.downBytesPerSec.toSpeedString() else idle,
                    tint = accentLight,
                )
                StatCard(
                    iconRes = R.drawable.ic_arrow_up_24dp,
                    label = stringResource(R.string.mx_stat_upload),
                    value = if (isRunning) speed.upBytesPerSec.toSpeedString() else idle,
                    tint = accentLight,
                )
                StatCard(
                    iconRes = R.drawable.ic_speed_24dp,
                    label = stringResource(R.string.mx_stat_ping),
                    value = if (isRunning && pingMillis != null) stringResource(R.string.mx_ping_value, pingMillis) else idle,
                    tint = accentLight,
                )
            }
            if (bottomExtra != null) {
                Spacer(Modifier.height(10.dp))
                bottomExtra()
            }
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
