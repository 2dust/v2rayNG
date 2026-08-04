package com.v2ray.ang.ui.compose

import android.os.Build
import android.view.View
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Радиус размытия фона под стеклом по умолчанию. */
val GlassBlurRadius = 26.dp

/**
 * Снимок экрана, который стекло размывает у себя под низом.
 *
 * Слой пишет тот, кто рисует содержимое ([glassBackdropSource]), а вместе со слоем
 * запоминается и его положение на экране: стекло может жить в другом окне (выпадающее
 * меню, шторка), поэтому координаты нужны общие - экранные, а не оконные.
 */
@Stable
class GlassBackdrop internal constructor(val layer: GraphicsLayer) {
    /** Левый верхний угол записанного содержимого в координатах экрана. */
    var origin by mutableStateOf(Offset.Zero)
        internal set
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/**
 * Пишет содержимое в [backdrop] и тут же рисует его на экране. Вешается на корень экрана,
 * чтобы стеклянные поверхности могли размыть именно то, что под ними.
 */
@Composable
fun Modifier.glassBackdropSource(backdrop: GlassBackdrop): Modifier {
    val view = LocalView.current
    return this
        .onGloballyPositioned { backdrop.origin = it.screenPosition(view) }
        .drawWithContent {
            backdrop.layer.record { this@drawWithContent.drawContent() }
            drawLayer(backdrop.layer)
        }
}

/**
 * Фон «жидкого стекла»: размытая копия того, что под элементом, полупрозрачная тонировка
 * из цветов темы, блик сверху и тонкая светлая грань по контуру.
 *
 * [backdrop] можно передавать только тем элементам, которые сами не попадают в запись слоя:
 * рисовать слой внутри его же записи запрещено. Всё, что лежит внутри экрана-источника
 * (кнопки на карточках и т.п.), стекло получает без физического размытия - с [fallbackColor].
 *
 * @param shape Форма поверхности.
 * @param backdrop Слой с содержимым экрана или null, если размытие невозможно.
 * @param blurRadius Радиус размытия фона.
 * @param opaqueness Плотность тонировки: 1 - как у нижней капсулы, больше - матовее.
 * @param fallbackColor Подложка, когда размытия нет (Android 11 и ниже либо backdrop == null).
 */
@Composable
fun Modifier.glassBackground(
    shape: Shape,
    backdrop: GlassBackdrop? = null,
    blurRadius: Dp = GlassBlurRadius,
    opaqueness: Float = 1f,
    fallbackColor: Color? = null
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val isDark = LocalDarkTheme.current
    val view = LocalView.current

    val canBlur = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val blurLayer = rememberGraphicsLayer()

    // Экранные координаты нужны, чтобы вырезать из слоя ровно тот кусок фона, который под нами
    var position by remember { mutableStateOf(Offset.Zero) }

    // Без размытия стекло должно быть плотнее, иначе сквозь него читается текст
    val solid = fallbackColor ?: scheme.surface.copy(alpha = if (isDark) 0.82f else 0.86f)
    val tint = glassTint(isDark, scheme.surface, opaqueness)
    val edge = glassEdge(isDark)

    return this
        .clip(shape)
        .onGloballyPositioned { position = it.screenPosition(view) }
        .drawBehind {
            val source = backdrop
            var blurred = false
            if (canBlur && source != null) {
                val dx = source.origin.x - position.x
                val dy = source.origin.y - position.y
                val radius = blurRadius.toPx()
                // Размытие живёт на отдельном слое, иначе оно размазало бы и само содержимое.
                // Clamp по краям: иначе кайма набирала бы прозрачность и темнела
                runCatching {
                    blurLayer.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                    blurLayer.record {
                        translate(left = dx, top = dy) {
                            drawLayer(source.layer)
                        }
                    }
                    drawLayer(blurLayer)
                }.onSuccess { blurred = true }
            }
            if (!blurred) drawRect(solid)
            drawRect(tint)
        }
        .border(width = 1.dp, brush = edge, shape = shape)
}

/**
 * Стеклянная поверхность с содержимым. Параметры - как у [glassBackground].
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    backdrop: GlassBackdrop? = null,
    blurRadius: Dp = GlassBlurRadius,
    opaqueness: Float = 1f,
    fallbackColor: Color? = null,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier.glassBackground(
            shape = shape,
            backdrop = backdrop,
            blurRadius = blurRadius,
            opaqueness = opaqueness,
            fallbackColor = fallbackColor
        ),
        content = content
    )
}

/** Тонировка стекла: сверху светлее, снизу уходит в цвет поверхности. */
fun glassTint(isDark: Boolean, surface: Color, opaqueness: Float = 1f): Brush {
    val top = if (isDark) 0.07f else 0.28f
    val bottom = if (isDark) 0.26f else 0.12f
    return Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = (top * opaqueness).coerceIn(0f, 1f)),
            surface.copy(alpha = (bottom * opaqueness).coerceIn(0f, 1f))
        )
    )
}

/** Светлая грань по контуру, гаснущая книзу. */
fun glassEdge(isDark: Boolean): Brush = Brush.verticalGradient(
    listOf(
        Color.White.copy(alpha = if (isDark) 0.22f else 0.7f),
        Color.White.copy(alpha = 0.04f)
    )
)

/**
 * Положение элемента на экране. Внутри окна Compose знает только оконные координаты,
 * а стекло и его фон могут оказаться в разных окнах, поэтому приводим к экранным.
 */
private fun LayoutCoordinates.screenPosition(view: View): Offset {
    val inWindow = positionInWindow()
    val onScreen = IntArray(2).also { view.getLocationOnScreen(it) }
    val viewInWindow = IntArray(2).also { view.getLocationInWindow(it) }
    return Offset(
        inWindow.x + (onScreen[0] - viewInWindow[0]).toFloat(),
        inWindow.y + (onScreen[1] - viewInWindow[1]).toFloat()
    )
}
