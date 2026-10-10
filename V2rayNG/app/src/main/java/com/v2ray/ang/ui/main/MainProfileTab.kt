package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.ui.compose.Btn17
import com.v2ray.ang.ui.compose.GithubStyleButton
import com.v2ray.ang.ui.compose.GlassCardShape
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.MatixIcons
import com.v2ray.ang.ui.compose.MatixSwitch
import com.v2ray.ang.ui.compose.ThemeManager
import com.v2ray.ang.ui.compose.liquidGlass

/**
 * Server info card at the bottom of the Home tab: the selected server's name inside a purple border, with the live
 * download and upload speeds in purple. One semantic node, so a screen reader reads it as one line.
 */
@Composable
fun ServerInfoCard(
    serverName: String?,
    isRunning: Boolean,
    speed: TrafficSpeed,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val idle = stringResource(R.string.mx_stat_none)
    val name = serverName ?: stringResource(R.string.mx_server_none)
    val down = if (isRunning) speed.downBytesPerSec.toSpeedString() else idle
    val up = if (isRunning) speed.upBytesPerSec.toSpeedString() else idle
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .liquidGlass(GlassCardShape)
            .border(1.dp, scheme.primary, GlassCardShape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = rememberVectorPainter(MatixIcons.Location),
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            SpeedLabel(R.drawable.ic_arrow_down_24dp, down)
            SpeedLabel(R.drawable.ic_arrow_up_24dp, up)
        }
    }
}

@Composable
private fun SpeedLabel(iconRes: Int, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

/**
 * Profile tab: day/night switch, the protocol list with Apply, the "Get Pro" card, and the update check.
 * The protocol list and the Pro card are look-only for now: they do not change the connection.
 */
@Composable
fun MainProfileTab(onCheckUpdate: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .androidxVerticalScroll()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Night mode
        Row(
            modifier = Modifier.fillMaxWidth().liquidGlass(GlassCardShape).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(rememberVectorPainter(MatixIcons.Moon), null, tint = scheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.mx_night_mode),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // "2" is night, "1" is day: the same values the settings screen stores.
            MatixSwitch(checked = dark, onCheckedChange = { ThemeManager.setThemeMode(if (it) "2" else "1") })
        }

        // Protocol list
        val protocols = listOf(
            R.string.mx_proto_auto,
            R.string.mx_proto_wireguard,
            R.string.mx_proto_openvpn,
            R.string.mx_proto_ikev2,
        )
        var selectedProtocol by rememberSaveable { mutableIntStateOf(0) }
        Text(
            text = stringResource(R.string.mx_protocol_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        for ((index, res) in protocols.withIndex()) {
            val selected = index == selectedProtocol
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(GlassCardShape)
                    .background(if (selected) scheme.primary else scheme.surfaceContainer)
                    .border(1.dp, if (selected) scheme.primary else scheme.outlineVariant, GlassCardShape)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { selectedProtocol = index })
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(res),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) Color.White else scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Icon(rememberVectorPainter(MatixIcons.Check), null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Btn17(text = stringResource(R.string.mx_apply), onClick = { })
        }

        // Get Pro
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(GlassCardShape)
                .border(1.dp, scheme.primary, GlassCardShape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(rememberVectorPainter(MatixIcons.Crown), null, tint = scheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.mx_pro_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                )
            }
            for (res in listOf(R.string.mx_pro_f1, R.string.mx_pro_f2, R.string.mx_pro_f3, R.string.mx_pro_f4)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(20.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(rememberVectorPainter(MatixIcons.Check), null, tint = scheme.primary, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(text = stringResource(res), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                }
            }
            Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                Btn17(text = stringResource(R.string.mx_pro_upgrade), onClick = { })
            }
        }

        // Update check
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            GithubStyleButton(text = stringResource(R.string.update_check_for_update), onClick = onCheckUpdate)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Modifier.androidxVerticalScroll(): Modifier =
    this.then(Modifier.verticalScroll(rememberScrollState()))
