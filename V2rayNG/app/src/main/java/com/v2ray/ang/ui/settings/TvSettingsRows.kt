package com.v2ray.ang.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.v2ray.ang.ui.compose.CollapsiblePreferenceGroupHeader as PreferenceGroupHeader
import com.v2ray.ang.ui.compose.SettingsItemRow
import com.v2ray.ang.ui.compose.SettingsMenuItem as PreferenceMenuItem
import com.v2ray.ang.ui.compose.SettingsSwitchItem as PreferenceSwitchItem

internal class TvSettingsRows(private val back: FocusRequester, private val onRowFocused: (String?) -> Unit) : SettingsRows {
    private val restorableRows = mutableMapOf<String, FocusRequester>()

    fun restoreFocus(key: String?): Boolean = restorableRows[key]?.requestFocus() == true

    @Composable
    private fun rowFocus(key: String? = null): Modifier = Modifier
        .onFocusChanged { if (it.isFocused) onRowFocused(key) }
        .tvSettingsFocusOutline()
        .focusProperties { start = back }

    @Composable
    override fun CollapsiblePreferenceGroupHeader(title: String, expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
        PreferenceGroupHeader(title, expanded, onExpandedChange, modifier = rowFocus())
    }

    @Composable
    override fun SettingsSwitchItem(title: String, summary: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean) {
        PreferenceSwitchItem(
            title = title,
            summary = summary,
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = rowFocus(),
            enabled = enabled
        )
    }

    @Composable
    override fun SettingsEditItem(title: String, value: String, onValueChanged: (String) -> Unit, enabled: Boolean, isPassword: Boolean, keyboardNumber: Boolean) {
        val summary = if (isPassword && value.isNotEmpty()) "******" else value
        DialogRow(title, summary.ifEmpty { null }, enabled) { close ->
            var text by remember { mutableStateOf(value) }
            TvSettingsEditDialog(
                title = title,
                value = text,
                onValueChange = { text = it },
                onConfirm = { close(); onValueChanged(text) },
                onDismiss = close,
                isPassword = isPassword,
                keyboardNumber = keyboardNumber
            )
        }
    }

    @Composable
    override fun SettingsListItem(
        title: String, entries: List<String>, values: List<String>, selectedValue: String,
        onSelected: (String) -> Unit, enabled: Boolean, focusKey: String?
    ) {
        val options = remember(entries, values) { entries.zip(values) }
        val selected = options.find { it.second == selectedValue } ?: options.firstOrNull()
        DialogRow(title, selected?.first?.ifEmpty { null }, enabled, focusKey) { close ->
            TvSettingsListDialog(
                title = title,
                options = options,
                selectedValue = selected?.second,
                onSelected = { close(); onSelected(it) },
                onDismiss = close
            )
        }
    }

    @Composable
    override fun SettingsMenuItem(title: String, onClick: () -> Unit, subtitle: String?) {
        PreferenceMenuItem(title = title, onClick = onClick, modifier = rowFocus(), subtitle = subtitle)
    }

    @Composable
    private fun DialogRow(
        title: String, summary: String?, enabled: Boolean, focusKey: String? = null,
        dialog: @Composable (close: () -> Unit) -> Unit
    ) {
        var open by remember { mutableStateOf(false) }
        val origin = remember { FocusRequester() }
        SettingsItemRow(
            icon = null,
            title = title,
            description = summary,
            enabled = enabled,
            interactionModifier = Modifier.clickable(enabled = enabled) { open = true },
            modifier = Modifier.focusRequester(origin).then(rowFocus(focusKey))
        )
        if (focusKey != null && enabled) {
            DisposableEffect(focusKey, origin) {
                restorableRows[focusKey] = origin
                onDispose { restorableRows.remove(focusKey) }
            }
        }
        if (open) dialog { open = false }
        var wasOpen by remember { mutableStateOf(false) }
        LaunchedEffect(open) {
            if (!open && wasOpen) origin.requestFocus()
            wasOpen = open
        }
    }
}
