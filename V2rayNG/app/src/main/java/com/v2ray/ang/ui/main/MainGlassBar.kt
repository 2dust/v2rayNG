package com.v2ray.ang.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.GlassBackdrop
import com.v2ray.ang.ui.compose.GlassSurface
import com.v2ray.ang.ui.compose.LocalDarkTheme

/** Пункты нижней капсулы. */
enum class GlassBarItem { HOME, SETTINGS, ADD }

/** Форма стеклянных таблеток: и капсулы снизу, и кнопок на карточках. */
val GlassCapsuleShape = RoundedCornerShape(50)

private val ItemSize = 68.dp
private val BarHeight = 72.dp
private val BarPadding = 14.dp

/**
 * Нижняя капсула в духе жидкого стекла: под ней размывается то, что нарисовано на экране,
 * сверху ложится полупрозрачный слой темы, блик и тонкая светлая грань.
 *
 * @param backdrop Слой с содержимым экрана, записанный тем, кто рисует контент.
 * @param selected Активный пункт.
 * @param onSelect Нажатие по пункту.
 */
@Composable
fun LiquidGlassBar(
    backdrop: GlassBackdrop,
    selected: GlassBarItem,
    onSelect: (GlassBarItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = LocalDarkTheme.current

    val items = listOf(GlassBarItem.HOME, GlassBarItem.SETTINGS, GlassBarItem.ADD)
    val selectedIndex = items.indexOf(selected).coerceAtLeast(0)

    // Подсветка активного пункта переезжает пружиной - это и даёт «жидкость»
    val highlightOffset by animateDpAsState(
        targetValue = ItemSize * selectedIndex,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "glassHighlight"
    )

    GlassSurface(
        modifier = modifier
            .height(BarHeight)
            .width(ItemSize * items.size + BarPadding * 2),
        shape = GlassCapsuleShape,
        backdrop = backdrop,
        fallbackColor = scheme.surfaceContainerHigh.copy(alpha = 0.96f)
    ) {
        // Подсветка активного пункта, отцентрованная по иконке
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = BarPadding)
                .offset(x = highlightOffset)
                .size(ItemSize)
                .padding(7.dp)
                .clip(GlassCapsuleShape)
                .background(scheme.primary.copy(alpha = if (isDark) 0.30f else 0.18f))
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = BarPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                GlassBarButton(
                    item = item,
                    active = item == selected,
                    onClick = { onSelect(item) }
                )
            }
        }
    }
}

@Composable
private fun GlassBarButton(
    item: GlassBarItem,
    active: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "glassButtonScale"
    )
    val tint by animateColorAsState(
        targetValue = if (active) scheme.primary else scheme.onSurfaceVariant,
        animationSpec = tween(250),
        label = "glassButtonTint"
    )

    Box(
        modifier = Modifier
            .size(ItemSize)
            .scale(scale)
            .clip(GlassCapsuleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        when (item) {
            GlassBarItem.HOME -> HomeIcon(color = tint, modifier = Modifier.size(26.dp))
            GlassBarItem.SETTINGS -> Icon(
                painter = painterResource(R.drawable.ic_settings_24dp),
                contentDescription = "Настройки",
                tint = tint,
                modifier = Modifier.size(26.dp)
            )

            GlassBarItem.ADD -> Icon(
                painter = painterResource(R.drawable.ic_add_24dp),
                contentDescription = "Добавить",
                tint = tint,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/** Домик в том же проволочном стиле, что и остальные рисованные иконки. */
@Composable
private fun HomeIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 4f, cap = StrokeCap.Round)

        // Крыша
        drawLine(color, Offset(w * 0.1f, h * 0.45f), Offset(w * 0.5f, h * 0.12f), strokeWidth = 4f, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, h * 0.12f), Offset(w * 0.9f, h * 0.45f), strokeWidth = 4f, cap = StrokeCap.Round)
        // Стены
        drawLine(color, Offset(w * 0.22f, h * 0.42f), Offset(w * 0.22f, h * 0.85f), strokeWidth = 4f, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.78f, h * 0.42f), Offset(w * 0.78f, h * 0.85f), strokeWidth = 4f, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.22f, h * 0.85f), Offset(w * 0.78f, h * 0.85f), strokeWidth = 4f, cap = StrokeCap.Round)
        // Дверь
        drawRect(
            color = color,
            topLeft = Offset(w * 0.42f, h * 0.58f),
            size = androidx.compose.ui.geometry.Size(w * 0.16f, h * 0.27f),
            style = stroke
        )
    }
}
