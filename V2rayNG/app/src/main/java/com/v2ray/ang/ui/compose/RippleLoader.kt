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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The loading animation: rings that grow from a small dot in the middle and fade as they spread, one after another,
 * without end. `RippleLoader(Modifier.size(56.dp))` is the 56 dp version. The progress is read only while drawing, so a
 * frame costs a few circles and no recomposition.
 */
@Composable
fun RippleLoader(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    rings: Int = 3,
    strokeWidth: Dp = 2.dp,
    durationMillis: Int = 2400,
) {
    val progress = rememberInfiniteTransition(label = "ripple").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
        label = "rippleProgress",
    )
    Spacer(
        modifier.drawBehind {
            val maxRadius = size.minDimension / 2f
            val stroke = strokeWidth.toPx()
            for (i in 0 until rings) {
                val p = (progress.value + i.toFloat() / rings) % 1f
                val radius = maxRadius * (0.16f + 0.84f * p)
                val fade = (1f - p) * (1f - p)
                drawCircle(color.copy(alpha = 0.14f * fade), radius = radius)
                drawCircle(color.copy(alpha = 0.9f * fade), radius = (radius - stroke / 2f).coerceAtLeast(0f), style = Stroke(stroke))
            }
            drawCircle(color, radius = maxRadius * 0.09f)
        }
    )
}
