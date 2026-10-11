package com.v2ray.ang.ui.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

/**
 * The app's bar button: a full-width pill. While it is pressed (or while [busy]) the label slides a little toward the
 * start and an arrow slides in from the end edge, over 300 ms, the same motion as the "sliding" pattern button of the
 * web design. The slide follows the layout direction, so it is mirrored in right-to-left languages.
 */
@Composable
fun SlidingPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = Color.White,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val progress by animateFloatAsState(
        targetValue = if (pressed || busy) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "pillSlide",
    )
    // Screen direction of "toward the end": right in left-to-right layouts, left in right-to-left ones.
    val dir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    Box(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .fillMaxWidth()
            .pressScale(source, 0.98f)
            .alpha(if (enabled || busy) 1f else 0.6f)
            .clip(GlassPillShape)
            .background(container)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.graphicsLayer { translationX = -dir * 12.dp.toPx() * progress },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = content, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = content,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = content.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_arrow_forward_24dp),
            contentDescription = null,
            tint = content,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(20.dp)
                .graphicsLayer {
                    translationX = dir * 36.dp.toPx() * (1f - progress)
                    alpha = progress
                },
        )
    }
}
