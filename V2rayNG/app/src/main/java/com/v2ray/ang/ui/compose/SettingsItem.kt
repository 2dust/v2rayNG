package com.v2ray.ang.ui.compose

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

@Composable
fun PreferenceGroupHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.secondary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun CollapsiblePreferenceGroupHeader(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val groupState = stringResource(
        if (expanded) R.string.acc_group_expanded else R.string.acc_group_collapsed
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { stateDescription = groupState }
            .clickable(role = Role.Button) { onExpandedChange(!expanded) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                .size(24.dp)
                .rotate(if (expanded) 180f else 0f)
        )
    }
}

@Composable
private fun SettingsItemRow(
    icon: Painter?,
    title: String,
    description: String?,
    enabled: Boolean,
    interactionModifier: Modifier,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    val titleColor = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val descriptionColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(interactionModifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = titleColor
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor
            )
            if (!description.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
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
    keyboardNumber: Boolean = false
) {
    var showDialog by remember { mutableStateOf(false) }
    val description = if (isPassword) {
        if (value.isEmpty()) null else "******"
    } else {
        value.ifEmpty { null }
    }

    SettingsItemRow(
        icon = icon,
        title = title,
        description = description,
        enabled = enabled,
        interactionModifier = Modifier.clickable(enabled = enabled) { showDialog = true },
        modifier = modifier
    )

    if (showDialog) {
        var text by remember { mutableStateOf(value) }
        InputDialog(
            title = title,
            fields = listOf(
                InputField(
                    label = title,
                    value = text,
                    visualTransformation = VisualTransformation.None
                )
            ),
            onFieldChange = { _, v -> text = v },
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
    entries: List<String>,
    values: List<String>,
    selectedValue: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var showDialog by remember { mutableStateOf(false) }
    val options = entries.zip(values)
    val selectedOption = options.find { it.second == selectedValue } ?: options.firstOrNull()
    val summary = selectedOption?.first.orEmpty()

    SettingsItemRow(
        icon = icon,
        title = title,
        description = summary.ifEmpty { null },
        enabled = enabled,
        interactionModifier = Modifier.clickable(enabled = enabled) { showDialog = true },
        modifier = modifier
    )

    if (showDialog) {
        SelectListDialog(
            title = title,
            options = options,
            optionText = { it.first },
            selectedOption = selectedOption,
            onSelected = { option ->
                showDialog = false
                onSelected(option.second)
            },
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
    subtitle: String? = null
) {
    SettingsItemRow(
        icon = icon,
        title = title,
        description = subtitle,
        enabled = true,
        interactionModifier = Modifier.clickable(onClick = onClick),
        modifier = modifier,
    )
}

@Composable
fun SettingsSwitchItem(
    icon: Painter? = null,
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    SettingsItemRow(
        icon = icon,
        title = title,
        description = summary,
        enabled = enabled,
        interactionModifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        modifier = modifier,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.scale(0.8f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                    checkedTrackColor = MaterialTheme.colorScheme.secondary
                ),
                enabled = enabled
            )
        }
    )
}
