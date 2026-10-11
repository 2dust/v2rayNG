package com.v2ray.ang.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.XfOrange

/** The two sections of the home screen, two pages side by side: Matixray (the normal profiles) and Psiphon on its own page. */
enum class MainSection { Main, Psiphon }

/**
 * The titles above the two home pages: "Matixray" over the main page and "Psiphon" over the Psiphon page, the page in
 * view shown in full color with a short bar under it (violet, orange for Psiphon). A tap on a title only reports
 * [onSelect]; swiping the page moves the same marker.
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
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        SectionTitle(
            text = stringResource(R.string.mx_tab_main),
            selected = selected == MainSection.Main,
            accent = MaterialTheme.colorScheme.primary,
            onClick = { onSelect(MainSection.Main) },
            modifier = Modifier.weight(1f),
        )
        SectionTitle(
            text = stringResource(R.string.mx_tab_psiphon),
            selected = selected == MainSection.Psiphon,
            accent = XfOrange,
            onClick = { onSelect(MainSection.Psiphon) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SectionTitle(
    text: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor by animateColorAsState(
        if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "sectionTitle",
    )
    val bar by animateColorAsState(if (selected) accent else Color.Transparent, label = "sectionBar")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .selectable(
                selected = selected,
                interactionSource = MutableInteractionSource(),
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .heightIn(min = 44.dp)
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .width(28.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(50))
                .background(bar),
        )
    }
}
