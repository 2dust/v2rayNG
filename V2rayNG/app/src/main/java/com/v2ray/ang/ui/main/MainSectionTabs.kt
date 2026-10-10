package com.v2ray.ang.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.GlassPillShape
import com.v2ray.ang.ui.compose.XfOrange
import com.v2ray.ang.ui.compose.XfOrangeDark
import com.v2ray.ang.ui.compose.liquidGlass

/** The two sections of the main screen: the normal profiles, and Psiphon on its own page. */
enum class MainSection { Main, Psiphon }

/**
 * Two tabs at the top of the main screen. The selected tab is filled: violet for the main section, orange for Psiphon,
 * so the two sections read apart at a glance. A tap on a tab only reports [onSelect].
 */
@Composable
fun MainSectionTabs(
    selected: MainSection,
    onSelect: (MainSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .liquidGlass(GlassPillShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SectionTab(
            text = stringResource(R.string.mx_tab_main),
            selected = selected == MainSection.Main,
            fillStart = MaterialTheme.colorScheme.primary,
            fillEnd = MaterialTheme.colorScheme.primary,
            onClick = { onSelect(MainSection.Main) },
            modifier = Modifier.weight(1f),
        )
        SectionTab(
            text = stringResource(R.string.mx_tab_psiphon),
            selected = selected == MainSection.Psiphon,
            fillStart = XfOrange,
            fillEnd = XfOrange,
            onClick = { onSelect(MainSection.Psiphon) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SectionTab(
    text: String,
    selected: Boolean,
    fillStart: Color,
    fillEnd: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    val start by animateColorAsState(if (selected) fillStart else Color.Transparent, label = "tabStart")
    val end by animateColorAsState(if (selected) fillEnd else Color.Transparent, label = "tabEnd")
    val textColor by animateColorAsState(
        if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tabText",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(start, end)))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
