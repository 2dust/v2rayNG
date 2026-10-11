package com.v2ray.ang.ui.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The infinity loop (∞) used as the loading animation: a faint track in the shape of a lemniscate and a bright arc
 * that runs around it without end. The shape is drawn to fill whatever size the caller gives, so
 * `InfinityLoop(Modifier.size(width = 56.dp, height = 24.dp))` works at any size. The path is built once per size and
 * the animated progress is read only while drawing, so a frame costs one path segment and no recomposition.
 */
@Composable
fun InfinityLoop(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 4.dp,
    trackAlpha: Float = 0.18f,
    arcFraction: Float = 0.3f,
    durationMillis: Int = 1200,
) {
    val progress = rememberInfiniteTransition(label = "infinity").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
        label = "infinityProgress",
    )
    Spacer(
        modifier.drawWithCache {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val pad = strokeWidth.toPx()
            val cx = size.width / 2f
            val cy = size.height / 2f
            val ax = (cx - pad).coerceAtLeast(1f)
            val ay = (cy - pad).coerceAtLeast(1f)
            // Lemniscate of Bernoulli; its height is about 0.3536 of its half width, scaled up to fill the box.
            val path = Path()
            val steps = 160
            for (i in 0..steps) {
                val t = i.toFloat() / steps * (2f * PI.toFloat())
                val s = sin(t)
                val c = cos(t)
                val d = 1f + s * s
                val x = cx + ax * c / d
                val y = cy + ay * (s * c / d) / 0.35355339f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            val measure = PathMeasure().apply { setPath(path, false) }
            val total = measure.length
            val arc = Path()
            val trackColor = color.copy(alpha = trackAlpha)
            onDrawBehind {
                drawPath(path, trackColor, style = stroke)
                val start = progress.value * total
                val end = start + total * arcFraction
                arc.reset()
                if (end <= total) {
                    measure.getSegment(start, end, arc, true)
                } else {
                    measure.getSegment(start, total, arc, true)
                    measure.getSegment(0f, end - total, arc, true)
                }
                drawPath(arc, color, style = stroke)
            }
        }
    )
}
