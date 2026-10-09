package com.v2ray.ang.ui.xfinder

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.XfPurple
import com.v2ray.ang.ui.compose.XfPurpleLight
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ScannerScreen(onBack: () -> Unit, viewModel: ScannerViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val data = state.data

    XfinderPage(
        title = stringResource(R.string.xf_nav_scanner),
        onBack = onBack,
        isLoading = state.isLoading,
        actions = {
            val refreshLabel = stringResource(R.string.xf_refresh)
            IconButton(
                onClick = viewModel::refresh,
                modifier = Modifier.semantics { contentDescription = refreshLabel },
            ) { Text("🔄") }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.xf_scanner_title),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.xf_scanner_source),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item(key = "stats") {
                val stats = data?.stats
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatBox(stringResource(R.string.xf_stat_total), stats?.total, Modifier.weight(1f))
                    StatBox(stringResource(R.string.xf_stat_online), stats?.online, Modifier.weight(1f))
                    StatBox(stringResource(R.string.xf_stat_pinned), stats?.pinned, Modifier.weight(1f))
                }
            }
            item(key = "radar") {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    RadarView(ips = data?.ips.orEmpty().take(RADAR_BLIPS).map { it.ip })
                }
            }
            if (data != null) {
                items(data.ips, key = { it.ip }) { ip ->
                    IpRow(ip = ip, onClick = { viewModel.copyIp(ip.ip) })
                }
            } else if (state.hasError) {
                item(key = "error") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.xf_error_load),
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.xf_retry)) }
                    }
                }
            }
        }
    }
}

private const val RADAR_BLIPS = 6

@Composable
private fun StatBox(label: String, value: Int?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = XfinderCardBorder,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value?.toString() ?: "—",
                color = XfPurpleLight,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Decorative radar: concentric rings, a rotating sweep and a few blinking blips placed from the IPs. */
@Composable
private fun RadarView(ips: List<String>, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "radar")
    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 3500, easing = LinearEasing)),
        label = "sweep",
    )
    val blink by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "blink",
    )
    val ringColor = XfPurple.copy(alpha = 0.35f)

    Canvas(modifier = modifier.size(200.dp).clearAndSetSemantics { }) {
        val radius = size.minDimension / 2f
        val c = center
        for (i in 1..4) {
            drawCircle(color = ringColor, radius = radius * i / 4f, center = c, style = Stroke(1.dp.toPx()))
        }
        drawLine(ringColor, Offset(c.x - radius, c.y), Offset(c.x + radius, c.y), 1.dp.toPx())
        drawLine(ringColor, Offset(c.x, c.y - radius), Offset(c.x, c.y + radius), 1.dp.toPx())

        rotate(degrees = sweepAngle, pivot = c) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(Color.Transparent, XfPurple.copy(alpha = 0.55f)),
                    center = c,
                ),
                radius = radius,
                center = c,
            )
            drawLine(XfPurpleLight, c, Offset(c.x + radius, c.y), 2.dp.toPx())
        }

        ips.forEachIndexed { index, ip ->
            val h = abs(ip.hashCode())
            val angle = Math.toRadians((h % 360).toDouble())
            val frac = 0.25f + ((h / 360) % 70) / 100f
            val p = Offset(c.x + (cos(angle) * radius * frac).toFloat(), c.y + (sin(angle) * radius * frac).toFloat())
            val alpha = (if (index % 2 == 0) blink else 1.15f - blink).coerceIn(0.15f, 1f)
            drawCircle(XfPurpleLight.copy(alpha = alpha), radius = 4.dp.toPx(), center = p)
        }
    }
}

@Composable
private fun IpRow(ip: ScannedIp, onClick: () -> Unit) {
    val level = pingLevelOf(ip.ms)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (ip.pinned) BorderStroke(1.5.dp, XfPurple) else XfinderCardBorder,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = ip.sourceEmoji, fontSize = 22.sp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MonoLtrText(
                    text = ip.ip,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 14,
                )
                if (ip.location.isNotEmpty()) {
                    Text(
                        text = ip.location,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (ip.ms != Int.MAX_VALUE) {
                Text(
                    text = stringResource(R.string.xf_ping_ms, ip.ms),
                    color = pingColor(level, MaterialTheme.colorScheme.onSurfaceVariant),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
