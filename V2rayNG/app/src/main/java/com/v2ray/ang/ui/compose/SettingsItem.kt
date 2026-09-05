package com.v2ray.ang.ui.compose

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

private val ItemPad = 16.dp
private val GroupHeaderTopPad = 16.dp
private val GroupHeaderBottomPad = 8.dp
private val HeaderVerticalPad = 12.dp
private val IconSize = 24.dp
private val TitleGap = 4.dp
private const val DisabledAlpha = 0.38f
private const val SwitchScale = 0.8f
private const val MaskedValue = "******"

@Composable
fun PreferenceGroupHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.secondary,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = ItemPad,
                end = ItemPad,
                top = GroupHeaderTopPad,
                bottom = GroupHeaderBottomPad
            )
    )
}

@Composable
fun CollapsiblePreferenceGroupHeader(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "GroupHeaderChevron"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onExpandedChange(!expanded) }
            .padding(horizontal = ItemPad, vertical = HeaderVerticalPad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(R.drawable.ic_expand_more_24dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .size(IconSize)
                .graphicsLayer { rotationZ = rotation }
        )
    }
}

@Composable
private fun SettingsItemRow(
    icon: Painter?,
    title: String,
    description: String?,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    val titleColor = MaterialTheme.colorScheme.onSurface
        .let { if (enabled) it else it.copy(alpha = DisabledAlpha) }
    val descriptionColor = MaterialTheme.colorScheme.onSurfaceVariant
        .let { if (enabled) it else it.copy(alpha = DisabledAlpha) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick)
                else Modifier
            )
            .padding(ItemPad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(IconSize),
                tint = titleColor
            )
            Spacer(modifier = Modifier.width(ItemPad))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (!description.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(TitleGap))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = descriptionColor
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun SettingsEditItem(
    icon: Painter? = null,
    title: String,
    value: String,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    keyboardNumber: Boolean = false,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var text by remember(value) { mutableStateOf(value) }
    val description = value.ifEmpty { null }?.let { if (isPassword) MaskedValue else it }

    SettingsItemRow(
        icon = icon,
        title = title,
        description = description,
        enabled = enabled,
        onClick = if (enabled) {
            { text = value; showDialog = true }
        } else {
            null
        },
        modifier = modifier,
    )
    if (showDialog) {
        val fields = remember(text, title, isPassword, keyboardNumber) {
            listOf(
                InputField(
                    label = title,
                    value = text,
                    keyboardType = if (keyboardNumber) KeyboardType.Number else KeyboardType.Text,
                    visualTransformation = if (isPassword) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    onValueChange = { text = it }
                )
            )
        }
        InputDialog(
            title = title,
            fields = fields,
            confirmText = stringResource(R.string.action_ok),
            dismissText = stringResource(R.string.action_cancel),
            onConfirm = { showDialog = false; onValueChanged(text) },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun SettingsListItem(
    icon: Painter? = null,
    title: String,
    entries: StringOptions,
    values: StringOptions,
    selectedValue: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val options = remember(entries, values) { entries.values.zip(values.values) }
    val selectedOption = remember(options, selectedValue) {
        options.find { it.second == selectedValue } ?: options.firstOrNull()
    }
    SettingsItemRow(
        icon = icon,
        title = title,
        description = selectedOption?.first?.ifEmpty { null },
        enabled = enabled,
        onClick = if (enabled) {
            { showDialog = true }
        } else {
            null
        },
        modifier = modifier,
    )
    if (showDialog) {
        SelectListDialog(
            title = title,
            options = options,
            optionText = { it.first },
            optionKey = { it.second },
            selectedOption = selectedOption,
            onSelected = { option -> showDialog = false; onSelected(option.second) },
            onDismiss = { showDialog = false },
            showRadio = true
        )
    }
}

@Composable
fun SettingsMenuItem(
    icon: Painter? = null,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) = SettingsItemRow(
    icon = icon,
    title = title,
    description = subtitle,
    enabled = true,
    onClick = onClick,
    modifier = modifier,
)

@Composable
fun SettingsSwitchItem(
    icon: Painter? = null,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    SettingsItemRow(
        icon = icon,
        title = title,
        description = summary,
        enabled = enabled,
        onClick = null,
        modifier = modifier
            .then(
                Modifier.toggleable(
                    value = checked,
                    onValueChange = onCheckedChange,
                    enabled = enabled,
                    role = Role.Switch
                )
            ),
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.scale(SwitchScale),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                    checkedTrackColor = MaterialTheme.colorScheme.secondary
                ),
                enabled = enabled
            )
        }
    )
}

// ===== previews =====

@Preview(showBackground = true)
@Composable
private fun PreferenceGroupHeaderPreview() = AppTheme {
    PreferenceGroupHeader(title = "VPN")
}

@Preview(showBackground = true)
@Composable
private fun CollapsiblePreferenceGroupHeaderPreview() = AppTheme {
    Column {
        CollapsiblePreferenceGroupHeader(title = "Core", expanded = true, onExpandedChange = {})
        CollapsiblePreferenceGroupHeader(title = "Mux", expanded = false, onExpandedChange = {})
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsSwitchItemPreview() = AppTheme {
    Column {
        SettingsSwitchItem(
            title = "Enable local proxy",
            summary = "A very long summary that has to stay readable and get truncated politely",
            checked = true,
            onCheckedChange = {}
        )
        SettingsSwitchItem(
            title = "Disabled",
            checked = false,
            enabled = false,
            onCheckedChange = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsEditItemPreview() = AppTheme {
    Column {
        SettingsEditItem(
            title = "Socks port",
            value = "10808",
            keyboardNumber = true,
            onValueChanged = {}
        )
        SettingsEditItem(
            title = "Socks password",
            value = "secret",
            isPassword = true,
            onValueChanged = {}
        )
        SettingsMenuItem(title = "Mode help", onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsListItemPreview() = AppTheme {
    SettingsListItem(
        title = "Log level",
        entries = listOf("warning", "debug").toStringOptions(),
        values = listOf("warning", "debug").toStringOptions(),
        selectedValue = "warning",
        onSelected = {}
    )
}
