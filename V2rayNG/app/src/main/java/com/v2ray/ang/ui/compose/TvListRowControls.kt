package com.v2ray.ang.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun Modifier.tvListRowDecoration(focused: Boolean, moving: Boolean): Modifier {
    val shape = RoundedCornerShape(16.dp)
    return when {
        moving -> this.shadow(12.dp, shape).background(MaterialTheme.colorScheme.surfaceContainerHigh, shape).border(2.dp, colorConfigType, shape)
        focused -> this.background(MaterialTheme.colorScheme.primaryContainer, shape).border(2.dp, MaterialTheme.colorScheme.primary, shape)
        else -> this
    }
}

@Composable
internal fun TvListRowSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    Row(modifier.heightIn(min = 48.dp).dpadFocusOutline(cornerRadius = 24.dp)
        .onFocusChanged { focused = it.isFocused }.semantics(mergeDescendants = true) { contentDescription = label }
        .toggleable(checked, role = Role.Switch, onValueChange = onChange)
        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Switch(checked, onCheckedChange = null, modifier = Modifier.scale(0.65f))
        if (focused) Text(label, Modifier.padding(start = 8.dp).clearAndSetSemantics {}, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}
