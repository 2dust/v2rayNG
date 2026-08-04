package com.v2ray.ang.ui.main

import android.os.Build

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.LocalDarkTheme

/** Пункты нижней капсулы. */
enum class GlassBarItem { HOME, SETTINGS, ADD }

private val CapsuleShape = RoundedCornerShape(50)
private val ItemSize = 56.dp
private val BarHeight = 68.dp
private const val BLUR_RADIUS_DP = 22

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
    backdrop: GraphicsLayer,
    selected: GlassBarItem,
    onSelect: (GlassBarItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = LocalDarkTheme.current

    // Куда капсула попала на экране: по этому смещению из общего слоя
    // вырезается ровно тот кусок фона, который под ней
    var barOffset by remember { mutableStateOf(Offset.Zero) }

    val items = listOf(GlassBarItem.HOME, GlassBarItem.SETTINGS, GlassBarItem.ADD)
    val selectedIndex = items.indexOf(selected).coerceAtLeast(0)

    // Подсветка активного пункта переезжает пружиной - это и даёт «жидкость»
    val highlightOffset by animateDpAsState(
        targetValue = ItemSize * selectedIndex,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "glassHighlight"
    )

    Box(
        modifier = modifier
            .height(BarHeight)
            .width(ItemSize * items.size + 16.dp)
            .onGloballyPositioned { barOffset = it.positionInRoot() }
            .clip(CapsuleShape)
    ) {
        // 1. Размытая копия того, что под капсулой.
        // Настоящее размытие умеет только Android 12+, ниже вместо него плотная подложка,
        // иначе под капсулой была бы видна чёткая копия экрана
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(BLUR_RADIUS_DP.dp, edgeTreatment = BlurredEdgeTreatment(CapsuleShape))
                    .drawBehind {
                        translate(left = -barOffset.x, top = -barOffset.y) {
                            drawLayer(backdrop)
                        }
                    }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scheme.surfaceContainerHigh.copy(alpha = 0.96f))
            )
        }

        // 2. Тонировка стекла + блик сверху
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (isDark) {
                            listOf(
                                Color.White.copy(alpha = 0.10f),
                                scheme.surface.copy(alpha = 0.55f)
                            )
                        } else {
                            listOf(
                                Color.White.copy(alpha = 0.55f),
                                scheme.surface.copy(alpha = 0.35f)
                            )
                        }
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (isDark) 0.25f else 0.85f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    shape = CapsuleShape
                )
        )

        // 3. Подсветка активного пункта
        Box(
            modifier = Modifier
                .padding(8.dp)
                .offset(x = highlightOffset)
                .size(width = ItemSize - 16.dp, height = BarHeight - 16.dp)
                .clip(CapsuleShape)
                .background(scheme.primary.copy(alpha = if (isDark) 0.28f else 0.16f))
        )

        // 4. Сами кнопки
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
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
            .clip(CapsuleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        when (item) {
            GlassBarItem.HOME -> HomeIcon(color = tint, modifier = Modifier.size(24.dp))
            GlassBarItem.SETTINGS -> Icon(
                painter = painterResource(R.drawable.ic_settings_24dp),
                contentDescription = "Настройки",
                tint = tint,
                modifier = Modifier.size(24.dp)
            )

            GlassBarItem.ADD -> Icon(
                painter = painterResource(R.drawable.ic_add_24dp),
                contentDescription = "Добавить",
                tint = tint,
                modifier = Modifier.size(26.dp)
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
