package com.v2ray.ang.ui.xfinder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.v2ray.ang.R
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.XfBorder
import com.v2ray.ang.ui.compose.XfGreen
import com.v2ray.ang.ui.compose.XfPurple
import com.v2ray.ang.ui.compose.XfRed
import com.v2ray.ang.ui.compose.XfYellow

fun pingColor(level: PingLevel, fallback: Color): Color = when (level) {
    PingLevel.GOOD -> XfGreen
    PingLevel.MEDIUM -> XfYellow
    PingLevel.BAD -> XfRed
    PingLevel.NONE -> fallback
}

/** Shared frame for the three Xfinder pages: app top bar + themed background. */
@Composable
fun XfinderPage(
    title: String,
    onBack: () -> Unit,
    isLoading: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = { AppTopBar(title = title, onBackClick = onBack, isLoading = isLoading, actions = actions) },
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

@Composable
fun XfinderBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = XfPurple.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, XfPurple.copy(alpha = 0.55f)),
    ) {
        Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

val XfinderCardBorder = BorderStroke(1.dp, XfBorder)

/** Monospace text that always runs left-to-right, for IPs and host:port inside the RTL layout. */
@Composable
fun MonoLtrText(text: String, color: Color, modifier: Modifier = Modifier, fontSize: Int = 13) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize.sp,
        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
        maxLines = 1,
    )
}

/** The refresh icon of the Xfinder and scanner pages, drawn from a vector instead of a phone emoji. */
@Composable
fun RefreshIcon(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_refresh_24dp),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/**
 * Round badge for the network an IP belongs to: a colored disc with the network's first letter. It stands in for the
 * emoji the scanner data brings, which look different on every phone.
 */
@Composable
fun SourceBadge(source: String, modifier: Modifier = Modifier) {
    val color = when (source.lowercase()) {
        "cloudflare" -> Color(0xFFFF8A1F)
        "fastly" -> XfRed
        "cloudfront" -> Color(0xFF3B82F6)
        else -> XfPurple
    }
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = source.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
            color = color,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
