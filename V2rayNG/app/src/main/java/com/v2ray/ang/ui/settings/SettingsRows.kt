package com.v2ray.ang.ui.settings

import androidx.compose.runtime.Composable
import com.v2ray.ang.ui.compose.CollapsiblePreferenceGroupHeader as PhoneGroupHeader
import com.v2ray.ang.ui.compose.SettingsEditItem as PhoneEditItem
import com.v2ray.ang.ui.compose.SettingsListItem as PhoneListItem
import com.v2ray.ang.ui.compose.SettingsMenuItem as PhoneMenuItem
import com.v2ray.ang.ui.compose.SettingsSwitchItem as PhoneSwitchItem

/** The shared form supplies the setting and its handler; each screen supplies the controls. */
internal interface SettingsRows {
    @Composable
    fun CollapsiblePreferenceGroupHeader(title: String, expanded: Boolean, onExpandedChange: (Boolean) -> Unit)

    @Composable
    fun SettingsSwitchItem(title: String, summary: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true)

    @Composable
    fun SettingsEditItem(
        title: String, value: String, onValueChanged: (String) -> Unit, enabled: Boolean = true,
        isPassword: Boolean = false, keyboardNumber: Boolean = false
    )

    @Composable
    fun SettingsListItem(
        title: String, entries: List<String>, values: List<String>, selectedValue: String,
        onSelected: (String) -> Unit, enabled: Boolean = true, focusKey: String? = null
    )

    @Composable
    fun SettingsMenuItem(title: String, onClick: () -> Unit, subtitle: String? = null)
}

internal object PhoneSettingsRows : SettingsRows {
    @Composable
    override fun CollapsiblePreferenceGroupHeader(title: String, expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
        PhoneGroupHeader(title, expanded, onExpandedChange)
    }

    @Composable
    override fun SettingsSwitchItem(title: String, summary: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean) {
        PhoneSwitchItem(title = title, summary = summary, checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }

    @Composable
    override fun SettingsEditItem(title: String, value: String, onValueChanged: (String) -> Unit, enabled: Boolean, isPassword: Boolean, keyboardNumber: Boolean) {
        PhoneEditItem(title = title, value = value, onValueChanged = onValueChanged, enabled = enabled,
            isPassword = isPassword, keyboardNumber = keyboardNumber)
    }

    @Composable
    override fun SettingsListItem(
        title: String, entries: List<String>, values: List<String>, selectedValue: String,
        onSelected: (String) -> Unit, enabled: Boolean, focusKey: String?
    ) {
        PhoneListItem(title = title, entries = entries, values = values, selectedValue = selectedValue,
            onSelected = onSelected, enabled = enabled)
    }

    @Composable
    override fun SettingsMenuItem(title: String, onClick: () -> Unit, subtitle: String?) {
        PhoneMenuItem(title = title, onClick = onClick, subtitle = subtitle)
    }
}
