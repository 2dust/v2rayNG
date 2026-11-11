package com.v2ray.ang.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The RX mark, shared by the launch animation and the first-run screens so both draw the same
 * geometry as `branding/rx-vpn-logo.svg` (512-unit viewport).
 */

/** "R" of the RX monogram, in the 512-unit viewport used by `ic_brand_mark`. */
internal fun rxLetterR(): Path = Path().apply {
    fillType = PathFillType.EvenOdd
    moveTo(100f, 156f)
    lineTo(192f, 156f)
    lineTo(220f, 162f)
    lineTo(238f, 178f)
    lineTo(244f, 202f)
    lineTo(238f, 228f)
    lineTo(216f, 248f)
    lineTo(254f, 356f)
    lineTo(204f, 356f)
    lineTo(168f, 260f)
    lineTo(146f, 260f)
    lineTo(146f, 356f)
    lineTo(100f, 356f)
    close()
    moveTo(146f, 196f)
    lineTo(188f, 196f)
    lineTo(198f, 200f)
    lineTo(202f, 208f)
    lineTo(198f, 218f)
    lineTo(188f, 222f)
    lineTo(146f, 222f)
    close()
}

/** "X" of the RX monogram, in the 512-unit viewport used by `ic_brand_mark`. */
internal fun rxLetterX(): Path = Path().apply {
    moveTo(266f, 156f)
    lineTo(316f, 156f)
    lineTo(342f, 210f)
    lineTo(370f, 156f)
    lineTo(420f, 156f)
    lineTo(368f, 252f)
    lineTo(424f, 356f)
    lineTo(372f, 356f)
    lineTo(342f, 296f)
    lineTo(312f, 356f)
    lineTo(260f, 356f)
    lineTo(316f, 252f)
    close()
}

/**
 * Static RX tile: the monogram on a rounded graphite square. Size the caller up with [modifier]
 * (`Modifier.size(...)`); the mark scales to whatever it is given.
 */
@Composable
fun RxMonogramTile(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
) {
    val colors = MaterialTheme.colorScheme
    val letterR = remember { rxLetterR() }
    val letterX = remember { rxLetterX() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.verticalGradient(listOf(colors.surfaceContainerHigh, colors.background))
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.minDimension / 512f
            withTransform({ scale(unit, unit, Offset.Zero) }) {
                drawPath(letterR, colors.onSurface)
                drawPath(letterX, colors.primary)
            }
        }
    }
}
