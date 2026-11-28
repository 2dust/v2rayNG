package com.v2ray.ang.ui.tv.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.settings.SettingsRows
import com.v2ray.ang.ui.tv.compose.dpadFocusOutline
import com.v2ray.ang.ui.tv.compose.requestFocusWhenReady

internal class TvSettingsRows(private val back: FocusRequester, private val onRowFocused: (String?) -> Unit) : SettingsRows {
    private val restorableRows = mutableMapOf<String, FocusRequester>()

    fun focusTarget(key: String?): FocusRequester? = restorableRows[key]

    @Composable
    private fun rowFocus(origin: FocusRequester? = null, key: String? = null): Modifier =
        Modifier.onFocusChanged { if (it.isFocused) onRowFocused(key) }
            .focusProperties { start = back }.dpadFocusOutline(focusRequester = origin)

    @Composable
    override fun CollapsiblePreferenceGroupHeader(title: String, expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
        SettingsRow(title, null, true, rowFocus().clickable(role = Role.Button) { onExpandedChange(!expanded) }) {
            Icon(
                painterResource(R.drawable.ic_expand_more_24dp), contentDescription = null,
                modifier = Modifier.size(24.dp).rotate(if (expanded) 180f else 0f),
                tint = MaterialTheme.colorScheme.secondary
            )
        }
    }

    @Composable
    override fun SettingsSwitchItem(title: String, summary: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean) {
        SettingsRow(title, summary, enabled, rowFocus().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)) {
            Switch(checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.scale(0.8f).clearAndSetSemantics {})
        }
    }

    @Composable
    override fun SettingsEditItem(title: String, value: String, onValueChanged: (String) -> Unit, enabled: Boolean, isPassword: Boolean, keyboardNumber: Boolean) {
        val summary = if (isPassword && value.isNotEmpty()) "••••••" else value
        DialogRow(title, summary, enabled) { close ->
            TvSettingsEditDialog(title, value, isPassword, keyboardNumber, close) {
                close()
                onValueChanged(it)
            }
        }
    }

    @Composable
    override fun SettingsListItem(
        title: String, entries: List<String>, values: List<String>, selectedValue: String,
        onSelected: (String) -> Unit, enabled: Boolean, focusKey: String?
    ) {
        val options = remember(entries, values) { entries.zip(values) }
        val selected = options.find { it.second == selectedValue } ?: options.firstOrNull()
        DialogRow(title, selected?.first, enabled, focusKey) { close ->
            TvSettingsListDialog(title, options, selected?.second, close) {
                close()
                onSelected(it)
            }
        }
    }

    @Composable
    override fun SettingsMenuItem(title: String, onClick: () -> Unit, subtitle: String?) {
        SettingsRow(title, subtitle, true, rowFocus().clickable(role = Role.Button, onClick = onClick))
    }

    @Composable
    private fun DialogRow(
        title: String, summary: String?, enabled: Boolean, focusKey: String? = null,
        dialog: @Composable (close: () -> Unit) -> Unit
    ) {
        var open by remember { mutableStateOf(false) }
        var returning by remember { mutableStateOf(false) }
        val origin = remember { FocusRequester() }
        if (focusKey != null && enabled) {
            DisposableEffect(focusKey, origin) {
                restorableRows[focusKey] = origin
                onDispose { restorableRows.remove(focusKey) }
            }
        }
        SettingsRow(title, summary, enabled, rowFocus(origin, focusKey).clickable(enabled = enabled, role = Role.Button) { open = true })
        if (open) dialog { open = false; returning = true }
        if (returning) {
            LaunchedEffect(origin) {
                requestFocusWhenReady(origin, back)
                returning = false
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, summary: String?, enabled: Boolean, modifier: Modifier, trailing: @Composable (() -> Unit)? = null) {
    val alpha = if (enabled) 1f else 0.38f
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha), style = MaterialTheme.typography.bodyLarge)
            if (!summary.isNullOrEmpty()) Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                style = MaterialTheme.typography.bodyMedium)
        }
        trailing?.invoke()
    }
}
