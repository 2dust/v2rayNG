package com.v2ray.ang.ui.main

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.extension.isComplexType

/**
 * ExpressVPN-inspired hero connection dashboard for behradNG.
 */
@Composable
fun ExpressConnectDashboard(
    isRunning: Boolean,
    statusText: String,
    selectedGuid: String?,
    selectedProfile: ProfileItem?,
    selectedPing: Long,
    onToggleService: () -> Unit,
    onTestPing: () -> Unit,
    onOpenLocations: () -> Unit,
    onAddConfig: () -> Unit,
    onRoutingClick: () -> Unit,
    onPerAppClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    // Pulse animation for connected state
    val infiniteTransition = rememberInfiniteTransition(label = "ExpressPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val activeEmerald = Color(0xFF05A870)
    val activeEmeraldDark = Color(0xFF007E54)
    val inactiveRed = Color(0xFFD9383A)
    val darkSurfaceBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Status Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (isRunning) activeEmerald.copy(alpha = 0.15f) else inactiveRed.copy(alpha = 0.12f),
                border = BorderStroke(
                    1.dp,
                    if (isRunning) activeEmerald.copy(alpha = 0.4f) else inactiveRed.copy(alpha = 0.35f)
                ),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) activeEmerald else inactiveRed)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRunning) "VPN IS ON" else "VPN IS OFF",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        color = if (isRunning) activeEmerald else inactiveRed
                    )
                }
            }

            Text(
                text = if (isRunning) "CONNECTED" else "NOT CONNECTED",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                ),
                color = if (isRunning) activeEmerald else MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isRunning) {
                    "Protected by behradNG • Safe & Encrypted"
                } else {
                    "Tap button below to secure your connection"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ExpressVPN Giant Power Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(190.dp)
                .semantics {
                    contentDescription = if (isRunning) "Disconnect behradNG VPN" else "Connect behradNG VPN"
                }
        ) {
            // Pulsing Aura Ring (when connected)
            if (isRunning) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(activeEmerald.copy(alpha = pulseAlpha))
                )
            }

            // Outer Frame Ring
            Box(
                modifier = Modifier
                    .size(166.dp)
                    .clip(CircleShape)
                    .border(
                        width = 4.dp,
                        color = if (isRunning) activeEmerald else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
            )

            // Inner Button Circle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(136.dp)
                    .shadow(
                        elevation = if (isRunning) 14.dp else 4.dp,
                        shape = CircleShape,
                        spotColor = if (isRunning) activeEmerald else Color.Black
                    )
                    .clip(CircleShape)
                    .background(
                        brush = if (isRunning) {
                            Brush.verticalGradient(listOf(Color(0xFF07C182), activeEmeraldDark))
                        } else {
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                    MaterialTheme.colorScheme.surfaceContainer
                                )
                            )
                        }
                    )
                    .clickable {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onToggleService()
                    }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_power_24dp),
                    contentDescription = "Power",
                    tint = if (isRunning) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // ExpressVPN Selected Location / Server Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            border = BorderStroke(
                1.dp,
                if (isRunning) activeEmerald.copy(alpha = 0.45f) else darkSurfaceBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(22.dp))
                .clickable(onClick = onOpenLocations)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Globe / Location Icon
                Surface(
                    shape = CircleShape,
                    color = if (isRunning) activeEmerald.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_globe_24dp),
                            contentDescription = null,
                            tint = if (isRunning) activeEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Location / Server Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SELECTED CONFIGURATION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    val serverTitle = selectedProfile?.remarks?.ifBlank { "Default Server" }
                        ?: if (selectedGuid.isNullOrEmpty()) "No server selected" else "Config: ${selectedGuid.take(8)}"

                    Text(
                        text = serverTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Protocol badge
                        val proto = when {
                            selectedProfile == null -> "v2ray"
                            selectedProfile.configType.isComplexType() -> selectedProfile.configType.name
                            else -> selectedProfile.configType.name
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = proto,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // Ping badge
                        val pingText = when {
                            selectedPing > 0L -> "$selectedPing ms"
                            selectedPing < 0L -> "Timeout"
                            else -> "Untested"
                        }
                        val pingColor = when {
                            selectedPing > 0L -> activeEmerald
                            selectedPing < 0L -> inactiveRed
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = "● $pingText",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = pingColor
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Ping action button
                IconButton(
                    onClick = onTestPing,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_flash_on_24dp),
                        contentDescription = "Test Ping",
                        tint = activeEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Arrow pointing right
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back_24dp),
                    contentDescription = "Change Location",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(180f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Express Quick Action Shortcuts Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ExpressActionChip(
                iconRes = R.drawable.ic_add_24dp,
                label = "+ Add Config",
                onClick = onAddConfig,
                modifier = Modifier.weight(1f)
            )
            ExpressActionChip(
                iconRes = R.drawable.ic_flash_on_24dp,
                label = "Speed Test",
                onClick = onTestPing,
                modifier = Modifier.weight(1f)
            )
            ExpressActionChip(
                iconRes = R.drawable.ic_routing_24dp,
                label = "Routing",
                onClick = onRoutingClick,
                modifier = Modifier.weight(1f)
            )
            ExpressActionChip(
                iconRes = R.drawable.ic_per_apps_24dp,
                label = "Split Tunnel",
                onClick = onPerAppClick,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Security & Status Telemetry Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            border = BorderStroke(1.dp, darkSurfaceBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lock_24dp),
                            contentDescription = null,
                            tint = if (isRunning) activeEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "behradNG Security Shield",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = if (isRunning) "ACTIVE" else "STANDBY",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                        color = if (isRunning) activeEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (statusText.isNotBlank()) statusText else "Client ready. Add or select a configuration to start.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ExpressActionChip(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
