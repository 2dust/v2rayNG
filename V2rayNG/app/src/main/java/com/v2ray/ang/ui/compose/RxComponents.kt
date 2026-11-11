package com.v2ray.ang.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Layered translucency and a soft edge highlight, without a backdrop blur.
 * The same treatment works on every supported Android version and keeps text
 * contrast independent of the wallpaper. It also follows the active color scheme.
 */
@Composable
fun rxGlassBrush(): Brush {
    val colors = MaterialTheme.colorScheme
    return Brush.verticalGradient(
        listOf(
            colors.surfaceContainerHigh.copy(alpha = 0.88f),
            colors.surfaceContainer.copy(alpha = 0.72f),
        )
    )
}

@Composable
fun rxGlassBorder(): Brush {
    val colors = MaterialTheme.colorScheme
    return Brush.verticalGradient(
        listOf(colors.onSurface.copy(alpha = 0.20f), colors.outlineVariant.copy(alpha = 0.38f))
    )
}

@Composable
fun RxGlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(rxGlassBrush())
            .border(BorderStroke(1.dp, rxGlassBorder()), shape)
    ) {
        content()
    }
}

@Composable
fun RxGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    val background = if (accent && enabled) {
        Brush.verticalGradient(
            listOf(colors.primary.copy(alpha = 0.96f), colors.primary.copy(alpha = 0.82f))
        )
    } else rxGlassBrush()
    val contentColor = when {
        !enabled -> colors.onSurfaceVariant
        accent -> colors.onPrimary
        else -> colors.onSurface
    }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            modifier = modifier
                .defaultMinSize(minHeight = 56.dp)
                .clip(shape)
                .background(background)
                .border(
                    1.dp,
                    if (accent && enabled) colors.primary.copy(alpha = 0.55f)
                    else colors.outlineVariant.copy(alpha = 0.65f),
                    shape,
                )
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * Small rounded label: protocol name, latency, subscription badge. One shape and one padding for
 * all of them, so a row of meta information reads as a grid instead of a pile of loose text.
 */
@Composable
fun RxPill(
    text: String,
    contentColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = contentColor,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
