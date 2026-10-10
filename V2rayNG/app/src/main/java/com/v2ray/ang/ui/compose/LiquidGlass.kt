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

// Matixray: the surfaces are flat now, as in the Shadow VPN kit: a solid card color and a one-pixel border.
// The old glass fields stay so every caller keeps compiling; only fillTop (the card) and edgeDim (the border) are used.
val DarkGlassStyle = GlassStyle(
    fillTop = Color(0xFF22123A),
    fillBottom = Color(0xFF22123A),
    edgeLight = Color(0xFF4A2C6D),
    edgeDim = Color(0xFF4A2C6D),
    highlight = Color.Transparent,
)

val LightGlassStyle = GlassStyle(
    fillTop = Color(0xFFFFFFFF),
    fillBottom = Color(0xFFFFFFFF),
    edgeLight = Color(0xFFDCCBF2),
    edgeDim = Color(0xFFDCCBF2),
    highlight = Color.Transparent,
)

val LocalGlassStyle = staticCompositionLocalOf { DarkGlassStyle }

val GlassCardShape = RoundedCornerShape(16.dp)
val GlassPillShape = RoundedCornerShape(50)

/** Fill of buttons and selected tabs: the solid accent color. */
val PrimaryGradient: Brush
    @Composable get() = Brush.linearGradient(
        // Flat, like the kit's buttons: a solid accent (the two stops are the same color on purpose)
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)
    )

/**
 * Card surface of the app: a solid fill and a thin border, flat like the Shadow VPN kit. It keeps the name of the old
 * glass modifier so every screen picks the new look up at once. [tint] lays a color over the card (a selected item).
 * It draws no gradient and no highlight, so a long list of cards stays cheap to scroll.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = GlassCardShape,
    tint: Color = Color.Unspecified,
): Modifier {
    val glass = LocalGlassStyle.current
    var result = this.clip(shape).background(glass.fillTop)
    if (tint != Color.Unspecified) result = result.background(tint)
    return result.border(1.dp, glass.edgeDim, shape)
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
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
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
 * Full-window backdrop: the flat canvas color with one soft violet glow at the top, like the lit-from-above look of the
 * Shadow VPN kit. The brush is built once per size, so a frame costs two fills.
 */
@Composable
fun AppBackdrop(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val glow = if (dark) Color(0x339D4EDD) else Color(0x229D4EDD)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .drawWithCache {
                val brush = Brush.radialGradient(
                    listOf(glow, Color.Transparent),
                    center = Offset(size.width * 0.5f, 0f),
                    radius = size.width * 1.05f,
                )
                onDrawBehind { drawRect(brush) }
            }
    )
}

/** Rounded-corner convenience for gradient fills inside glass (selected tab, connect button). */
fun Modifier.gradientFill(brush: Brush, shape: Shape): Modifier = this.clip(shape).background(brush)
