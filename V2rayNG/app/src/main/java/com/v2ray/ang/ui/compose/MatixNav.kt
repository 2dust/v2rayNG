package com.v2ray.ang.ui.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

/** The four tabs of the bottom bar. */
enum class MainTab { Home, Location, Settings, Profile }

/**
 * `cir-tabs` bottom navigation (Uiverse.io, uiverse-astronaut): a rounded bar with a border; the active tab is a purple
 * pill with a glow, scaled up through graphicsLayer. Colors follow the day/night theme.
 */
@Composable
fun CirTabs(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val barShape = RoundedCornerShape(28.dp)
    Box(modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(barShape)
                .background(scheme.background)
                .border(1.dp, scheme.outlineVariant, barShape)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (tab in MainTab.entries) {
                CirTab(
                    tab = tab,
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CirTab(tab: MainTab, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    val bg by animateColorAsState(if (selected) scheme.primary else Color.Transparent, label = "cirBg")
    val fg by animateColorAsState(if (selected) Color.White else scheme.onSurfaceVariant, label = "cirFg")
    val scale by animateFloatAsState(if (selected) 1f else 0.94f, label = "cirScale")
    val label = stringResource(
        when (tab) {
            MainTab.Home -> R.string.mx_nav_home
            MainTab.Location -> R.string.mx_nav_location
            MainTab.Settings -> R.string.mx_nav_settings
            MainTab.Profile -> R.string.mx_nav_profile
        }
    )
    val painter: Painter = when (tab) {
        MainTab.Home -> rememberVectorPainter(MatixIcons.Home)
        MainTab.Location -> rememberVectorPainter(MatixIcons.Location)
        MainTab.Settings -> painterResource(R.drawable.ic_settings_24dp)
        MainTab.Profile -> rememberVectorPainter(MatixIcons.Profile)
    }
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (selected) Modifier.shadow(10.dp, shape, ambientColor = scheme.primary, spotColor = scheme.primary)
                else Modifier
            )
            .clip(shape)
            .background(bg)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painter = painter, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
