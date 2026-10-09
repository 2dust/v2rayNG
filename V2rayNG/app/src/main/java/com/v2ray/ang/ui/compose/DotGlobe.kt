package com.v2ray.ang.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val GLOBE_DOTS = 900

// Fibonacci-sphere dots, kept only where a cheap pseudo-continent mask is on, so the globe reads as land masses.
// Built once for the whole process and shared by the splash screen and the main screen.
private val globePoints: Pair<FloatArray, FloatArray> = run {
    val lat = ArrayList<Float>()
    val lon = ArrayList<Float>()
    val golden = (PI * (3.0 - sqrt(5.0))).toFloat()
    for (i in 0 until GLOBE_DOTS * 2) {
        val y = 1f - (i / (GLOBE_DOTS * 2 - 1f)) * 2f
        val theta = golden * i
        val la = Math.asin(y.toDouble()).toFloat()
        val lo = ((theta + PI) % (2 * PI) - PI).toFloat()
        val land = sin(la * 3.1f + 1.3f) * cos(lo * 2.2f - 0.4f) + 0.55f * sin(lo * 4.7f + la * 2.1f)
        if (land > 0.15f) { lat.add(la); lon.add(lo) }
    }
    lat.toFloatArray() to lon.toFloatArray()
}

/**
 * Turning dotted globe in the violet brand colors, drawn in one Canvas pass. The angle is only read while drawing,
 * so a frame redraws the canvas without recomposing anything.
 *
 * @param radiusFraction globe radius as a fraction of the smaller side of the canvas
 * @param periodMs time of one full turn
 * @param spinning false keeps the globe still
 */
@Composable
fun DotGlobe(
    modifier: Modifier = Modifier,
    radiusFraction: Float = 0.46f,
    periodMs: Int = 24000,
    spinning: Boolean = true,
    dotColor: Color = Color(0xFFB39BFF),
    ringColor: Color = Color(0xFF6B4DFF),
) {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(periodMs, spinning) {
        if (!spinning) return@LaunchedEffect
        while (true) {
            angle.snapTo(0f)
            angle.animateTo((2 * PI).toFloat(), tween(periodMs, easing = LinearEasing))
        }
    }
    Canvas(modifier = modifier) {
        val r = size.minDimension * radiusFraction
        val c = Offset(size.width / 2f, size.height * 0.5f)
        drawCircle(
            Brush.radialGradient(listOf(ringColor.copy(alpha = 0.35f), Color.Transparent), center = c, radius = r * 1.5f),
            radius = r * 1.5f, center = c,
        )
        drawCircle(ringColor.copy(alpha = 0.55f), radius = r, center = c, style = Stroke(width = 2f))
        val (lats, lons) = globePoints
        val a = angle.value
        val ca = cos(a)
        val sa = sin(a)
        for (i in lats.indices) {
            val la = lats[i]
            val x0 = cos(la) * sin(lons[i])
            val z0 = cos(la) * cos(lons[i])
            val x = x0 * ca + z0 * sa
            val z = -x0 * sa + z0 * ca
            if (z <= 0f) continue
            val y = sin(la)
            drawCircle(
                dotColor.copy(alpha = 0.15f + 0.75f * z),
                radius = 1.2f + 1.8f * z,
                center = Offset(c.x + x * r, c.y - y * r),
            )
        }
    }
}
