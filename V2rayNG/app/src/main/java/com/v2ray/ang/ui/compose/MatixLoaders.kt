package com.v2ray.ang.ui.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// Loaders of the Matixray purple theme, re-drawn on a Canvas. Each one runs a single infinite transition and reads
// its values only inside the draw block, so a frame redraws the canvas and never recomposes anything.
// The transition is tied to the composition, so it stops by itself when the loader leaves the screen.

private val LoaderPurple = Color(0xFF9D4EDD)

@Composable
private fun loaderBarColor(): Color = if (LocalDarkTheme.current) Color(0xFF4A2C6D) else Color(0xFFDCCBF2)

@Composable
private fun loaderTextColor(): Color = if (LocalDarkTheme.current) Color(0xFFE6E6FA) else Color(0xFF4A2C6D)

/**
 * "Speeder" loader (Uiverse.io, anand_4957): a small speeder that shakes while speed lines fly past behind it.
 * Every color of the original black artwork is [color].
 */
@Composable
fun SpeederLoader(modifier: Modifier = Modifier, color: Color = LoaderPurple) {
    val transition = rememberInfiniteTransition(label = "speeder")
    val flow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1000, easing = LinearEasing), RepeatMode.Restart),
        label = "speederFlow",
    )
    Canvas(modifier = modifier.size(width = 120.dp, height = 64.dp)) {
        val w = size.width
        val h = size.height
        // The body shakes up and down a little, twice per cycle.
        val shake = sin(flow * 4f * PI.toFloat()) * 1.5.dp.toPx()

        // Speed lines: three rows, each a short bar sliding to the left and fading out.
        val rows = floatArrayOf(0.30f, 0.52f, 0.74f)
        for (i in rows.indices) {
            val p = (flow + i * 0.33f) % 1f
            val x = w * (0.62f - 0.62f * p)
            val len = w * (0.12f + 0.10f * (1f - p))
            drawLine(
                color = color.copy(alpha = (1f - p) * 0.9f),
                start = Offset(x, h * rows[i]),
                end = Offset(x + len, h * rows[i]),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // Body of the speeder: a wedge nose, a cabin on top and a wheel.
        val bx = w * 0.58f
        val by = h * 0.22f + shake
        val body = Path().apply {
            moveTo(bx, by + h * 0.30f)
            lineTo(bx + w * 0.10f, by)
            lineTo(bx + w * 0.28f, by)
            lineTo(bx + w * 0.40f, by + h * 0.30f)
            lineTo(bx + w * 0.40f, by + h * 0.46f)
            lineTo(bx, by + h * 0.46f)
            close()
        }
        drawPath(body, color, style = Fill)
        drawCircle(color, radius = h * 0.12f, center = Offset(bx + w * 0.10f, by + h * 0.50f))
        drawCircle(color, radius = h * 0.12f, center = Offset(bx + w * 0.32f, by + h * 0.50f))
    }
}

/**
 * "Bar and ball" loader (Uiverse.io, Nawsome): a ball bounces along a row of bars. Ball [ballColor], bars [barColor].
 */
@Composable
fun BallBarLoader(
    modifier: Modifier = Modifier,
    ballColor: Color = LoaderPurple,
    barColor: Color = loaderBarColor(),
) {
    val transition = rememberInfiniteTransition(label = "ballbar")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "ballbarT",
    )
    Canvas(modifier = modifier.size(width = 120.dp, height = 36.dp)) {
        val barCount = 5
        val gap = 6.dp.toPx()
        val barW = (size.width - gap * (barCount - 1)) / barCount
        val barH = 6.dp.toPx()
        val baseY = size.height - barH
        for (i in 0 until barCount) {
            drawRoundRect(
                color = barColor,
                topLeft = Offset(i * (barW + gap), baseY),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barH / 2f, barH / 2f),
            )
        }
        // The ball travels across the bars and hops once over each of them.
        val r = 5.dp.toPx()
        val travel = size.width - 2f * r
        val hop = abs(sin(t * barCount * PI.toFloat()))
        val cx = r + travel * t
        val cy = baseY - r - hop * (size.height - barH - 2f * r)
        drawCircle(ballColor, radius = r, center = Offset(cx, cy))
    }
}

/**
 * Wi-Fi scanner loader (Uiverse.io, mobinkakei): three wifi arcs that fill from the inside out, with the caption
 * below. [frontColor] is the moving part, [backColor] the track, [textColor] the caption.
 */
@Composable
fun WifiLoader(
    text: String,
    modifier: Modifier = Modifier,
    frontColor: Color = LoaderPurple,
    backColor: Color = loaderBarColor(),
    textColor: Color = loaderTextColor(),
) {
    val transition = rememberInfiniteTransition(label = "wifi")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1600, easing = LinearEasing), RepeatMode.Restart),
        label = "wifiT",
    )
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = Modifier.size(64.dp)) {
            val stroke = 5.dp.toPx()
            val cx = size.width / 2f
            val cy = size.height * 0.88f
            val radii = floatArrayOf(size.width * 0.20f, size.width * 0.36f, size.width * 0.52f)
            for (i in radii.indices) {
                val r = radii[i]
                val topLeft = Offset(cx - r, cy - r)
                val arcSize = Size(2f * r, 2f * r)
                drawArc(
                    color = backColor,
                    startAngle = -135f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                // Arc i lights up in the i-th third of the cycle and stays lit until the cycle restarts.
                val local = ((t - i / 3f) * 3f).coerceIn(0f, 1f)
                if (local > 0f) {
                    drawArc(
                        color = frontColor,
                        startAngle = -135f,
                        sweepAngle = 90f * local,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            drawCircle(frontColor, radius = stroke * 0.8f, center = Offset(cx, cy))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}
