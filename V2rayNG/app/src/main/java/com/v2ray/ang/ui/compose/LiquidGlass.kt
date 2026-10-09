package com.v2ray.ang.ui.compose

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Colors of the "liquid glass" surfaces: a translucent fill, a specular edge and a soft highlight. */
@Immutable
class GlassStyle(
    val fillTop: Color,
    val fillBottom: Color,
    val edgeLight: Color,
    val edgeDim: Color,
    val highlight: Color,
)

val DarkGlassStyle = GlassStyle(
    fillTop = Color(0x24FFFFFF),
    fillBottom = Color(0x0AFFFFFF),
    edgeLight = Color(0x66FFFFFF),
    edgeDim = Color(0x1AA78BFF),
    highlight = Color(0x2EFFFFFF),
)

val LightGlassStyle = GlassStyle(
    fillTop = Color(0xCCFFFFFF),
    fillBottom = Color(0x80FFFFFF),
    edgeLight = Color(0xFFFFFFFF),
    edgeDim = Color(0x335233E6),
    highlight = Color(0xB3FFFFFF),
)

val LocalGlassStyle = staticCompositionLocalOf { DarkGlassStyle }

val GlassCardShape = RoundedCornerShape(24.dp)
val GlassPillShape = RoundedCornerShape(50)

/** Brand gradient of buttons and selected tabs (left-to-right in the design). */
val PrimaryGradient: Brush
    @Composable get() = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary)
    )

/**
 * Liquid-glass surface: translucent gradient fill, specular top-left highlight and a light-to-dim
 * edge line. [tint] colors the glass (e.g. the brand blue for a selected item).
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = GlassCardShape,
    tint: Color = Color.Unspecified,
): Modifier {
    val glass = LocalGlassStyle.current
    val fill = remember(glass) { Brush.verticalGradient(listOf(glass.fillTop, glass.fillBottom)) }
    val edge = remember(glass) {
        Brush.linearGradient(listOf(glass.edgeLight, glass.edgeDim, glass.edgeDim, glass.edgeLight.copy(alpha = glass.edgeLight.alpha * 0.4f)))
    }
    val shine = remember(glass) {
        Brush.radialGradient(
            colors = listOf(glass.highlight, Color.Transparent),
            center = Offset.Zero,
            radius = 600f,
        )
    }
    var result = this.clip(shape).background(fill)
    if (tint != Color.Unspecified) result = result.background(tint)
    return result
        .drawBehind { drawRect(brush = shine) }
        .border(1.dp, edge, shape)
}

/** Soft colored glow behind a shape (the violet halo of the main connect button). */
fun Modifier.softGlow(glowColor: Color, blur: Dp = 18.dp, corner: Dp = 20.dp): Modifier = drawWithCache {
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        this.color = glowColor.copy(alpha = 0.55f).toArgb()
        maskFilter = BlurMaskFilter(blur.toPx(), BlurMaskFilter.Blur.NORMAL)
    }
    val radius = corner.toPx()
    onDrawBehind {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, radius, radius, paint)
        }
    }
}

/**
 * Soft press feedback for buttons: the control shrinks a little while it is held and springs back, so taps feel
 * alive without a ripple. Pass the same [interactionSource] to the `clickable` of the control.
 */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource, pressedScale: Float = 0.96f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** A glass card with arbitrary content. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = GlassCardShape,
    tint: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.liquidGlass(shape, tint), content = content)
}

/**
 * Full-window backdrop: base color with violet glows and a faint dotted "world map" texture, so the
 * translucent glass surfaces have something to refract. Follows day/night through the color scheme.
 * The glow brushes and the dot positions are built once per size, not on every frame.
 */
@Composable
fun AppBackdrop(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val glowA = if (dark) Color(0x665A33E0) else Color(0x66A78BFF)
    val glowB = if (dark) Color(0x4D7B2FBF) else Color(0x4DBBA9FF)
    val dot = if (dark) Color(0x22B7A2FF) else Color(0x225233E6)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .drawWithCache {
                val brushA = Brush.radialGradient(
                    listOf(glowA, Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.05f),
                    radius = size.width * 1.1f,
                )
                val brushB = Brush.radialGradient(
                    listOf(glowB, Color.Transparent),
                    center = Offset(size.width * 0.05f, size.height * 0.95f),
                    radius = size.width * 1.0f,
                )
                val step = 14.dp.toPx()
                val r = 1.3.dp.toPx()
                val rows = (size.height * 0.4f / step).toInt()
                val cols = (size.width / step).toInt() + 1
                // x, y, alpha of every dot kept by the sparse pseudo-random mask (continent-like speckle, fading downwards)
                val dots = ArrayList<Float>()
                for (y in 0 until rows) {
                    for (x in 0 until cols) {
                        val h = (x * 73856093) xor (y * 19349663)
                        if ((h and 7) < 3) {
                            dots.add(x * step)
                            dots.add(y * step)
                            dots.add(dot.alpha * (1f - y / rows.toFloat()))
                        }
                    }
                }
                onDrawBehind {
                    drawRect(brushA)
                    drawRect(brushB)
                    var i = 0
                    while (i < dots.size) {
                        drawCircle(dot.copy(alpha = dots[i + 2]), r, Offset(dots[i], dots[i + 1]))
                        i += 3
                    }
                }
            }
    )
}

/** Rounded-corner convenience for gradient fills inside glass (selected tab, connect button). */
fun Modifier.gradientFill(brush: Brush, shape: Shape): Modifier = this.clip(shape).background(brush)
