package com.v2ray.ang.ui.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

// Button and toggle styles of the Matixray purple theme (Uiverse.io ports). Animations only touch graphicsLayer values
// (translation, scale, alpha) and Canvas fills, so a press never re-measures or re-lays-out anything.

private val ButtonPurple = Color(0xFF9D4EDD)
private val ButtonPurpleDeep = Color(0xFF6A0DAD)

/**
 * `btn-17` (secondary button): a pill with a 2 dp purple border; while pressed a purple fill sweeps across it
 * diagonally and the label moves up a little.
 */
@Composable
fun Btn17(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(50)
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val fill by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "btn17Fill",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(scheme.background)
            .border(2.dp, ButtonPurple, shape)
            .drawBehind {
                if (fill > 0f) {
                    // A parallelogram growing from the left edge: its slanted front edge is the "diagonal fill".
                    val slant = size.height
                    val front = (size.width + slant) * fill
                    val path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(front, 0f)
                        lineTo(front - slant, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(path, ButtonPurple)
                }
            }
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = 48.dp)
            .widthIn(min = 120.dp)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            modifier = Modifier.graphicsLayer { translationY = -2.dp.toPx() * fill },
            color = if (!enabled) scheme.onSurface.copy(alpha = 0.4f) else if (pressed) Color.White else scheme.onBackground,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * `cssbuttons-io-button` (main action): purple gradient pill with a purple glow, white label and a download icon.
 */
@Composable
fun GradientDownloadButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "gradBtnScale",
    )
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation = 16.dp, shape = shape, ambientColor = ButtonPurple, spotColor = ButtonPurple)
            .clip(shape)
            .background(Brush.linearGradient(listOf(ButtonPurpleDeep, ButtonPurple)))
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = 52.dp)
            .padding(horizontal = 28.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_cloud_download_24dp),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * `btn-github` (secondary action): a translucent dark button. While pressed the label turns neon purple, a faint
 * purple ring appears and the button lifts by 4 dp.
 */
@Composable
fun GithubStyleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val scheme = MaterialTheme.colorScheme
    val darkTheme = LocalDarkTheme.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val lift by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "githubLift",
    )
    val textColor by animateColorAsState(
        targetValue = if (pressed) ButtonPurple else scheme.onBackground,
        animationSpec = tween(durationMillis = 200),
        label = "githubText",
    )
    Box(
        modifier = modifier
            .graphicsLayer { translationY = -4.dp.toPx() * lift }
            .clip(shape)
            .background(if (darkTheme) Color(0x66000000) else Color(0x14000000))
            .border(
                width = 3.dp,
                color = ButtonPurple.copy(alpha = 0.3f * lift),
                shape = shape,
            )
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * `switch-button` (toggle, WireGuard iOS style): a flat 51 x 31 dp pill, off `#2C2C3E`, on `#9D4EDD`, and a white
 * knob that slides with a 0.3 s ease. The knob moves through graphicsLayer, not through an offset.
 *
 * With [onCheckedChange] null the switch only shows the state, like the Material switch: a row that already
 * handles the click owns the one semantic node.
 */
@Composable
fun MatixSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackWidth = 51.dp
    val trackHeight = 31.dp
    val knob = 27.dp
    val inset = (trackHeight - knob) / 2
    val travel by animateDpAsState(
        targetValue = if (checked) trackWidth - knob - inset * 2 else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "switchKnob",
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) ButtonPurple else if (LocalDarkTheme.current) Color(0xFF2C2C3E) else Color(0xFFD9CCEA),
        animationSpec = tween(durationMillis = 300),
        label = "switchTrack",
    )
    val sign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val base = modifier
        .size(width = trackWidth, height = trackHeight)
        .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
        .clip(CircleShape)
        .background(trackColor)
    val interactive = if (onCheckedChange != null) {
        base.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        )
    } else {
        base
    }
    Box(modifier = interactive.padding(inset), contentAlignment = Alignment.CenterStart) {
        Box(
            modifier = Modifier
                .size(knob)
                .graphicsLayer { translationX = sign * travel.toPx() }
                .background(Color.White, CircleShape)
        )
    }
}
