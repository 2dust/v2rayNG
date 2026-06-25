package com.v2ray.ang.compose

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.v2ray.ang.R
import sh.calvin.reorderable.ReorderableCollectionItemScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBackClick: () -> Unit,
    isLoading: Boolean = false,
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onSearchClose: () -> Unit = {},
    searchPlaceholder: String? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column {
        TopAppBar(
            title = {
                if (isSearchActive) {
                    SearchInputField(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        placeholder = searchPlaceholder
                    )
                } else {
                    Text(text = title)
                }
            },
            navigationIcon = {
                if (navigationIcon != null) {
                    navigationIcon()
                } else {
                    IconButton(onClick = if (isSearchActive) onSearchClose else onBackClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back_24dp),
                            contentDescription = "Back"
                        )
                    }
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
        AnimatedVisibility(visible = isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = colorFabActive)
        }
    }
}

@Composable
private fun SearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String?
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), Alignment.CenterStart) {
                    if (query.isEmpty() && !placeholder.isNullOrEmpty()) {
                        Text(placeholder, style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp))
                    }
                    inner()
                }
            }
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(painterResource(android.R.drawable.ic_menu_close_clear_cancel), "Clear")
            }
        }
    }
}

// region 设置相关组件
@Composable
fun PreferenceGroupHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = colorFabActive,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    )
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
    val contentColor = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
            if (!description.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
        onClick = if (enabled) { { showDialog = true } } else null,
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
            confirmText = stringResource(android.R.string.ok),
            dismissText = stringResource(android.R.string.cancel),
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
    val selectedIndex = values.indexOf(selectedValue).let { if (it >= 0) it else 0 }
    val summary = entries.getOrNull(selectedIndex).orEmpty()

    SettingsItemRow(
        icon = icon,
        title = title,
        description = summary.ifEmpty { null },
        enabled = enabled,
        onClick = if (enabled) { { showDialog = true } } else null,
        modifier = modifier
    )

    if (showDialog) {
        SelectListDialog(
            title = title,
            options = entries,
            selectedOption = summary,
            onSelected = { index, _ ->
                showDialog = false
                values.getOrNull(index)?.let(onSelected)
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
        onClick = onClick,
        modifier = modifier
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
        onClick = if (enabled) { { onCheckedChange(!checked) } } else null,
        modifier = modifier,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                modifier = Modifier.scale(0.8f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                    checkedTrackColor = colorFabActive
                ),
                enabled = enabled
            )
        }
    )
}

// region App 列表项
@Composable
fun AppListItem(
    appName: String,
    packageName: String,
    icon: Any?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                },
                update = { imageView ->
                    when (icon) {
                        is Drawable -> imageView.setImageDrawable(icon)
                        is Bitmap -> imageView.setImageBitmap(icon)
                        is Int -> imageView.setImageResource(icon)
                    }
                },
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = appName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = colorFabActive)
        )
    }
}

// region 表单输入组件
@Composable
fun FormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormDropdownField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (editable) onValueChange(it) },
            readOnly = !editable,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(
                    type = if (editable) ExposedDropdownMenuAnchorType.PrimaryEditable
                    else ExposedDropdownMenuAnchorType.PrimaryNotEditable
                )
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

// region 分隔线与版本信息
@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    val color = if (isSystemInDarkTheme()) dividerColorDark else dividerColorLight
    HorizontalDivider(modifier = modifier.fillMaxWidth(), thickness = 1.dp, color = color)
}

@Composable
fun VersionInfoBlock(
    versionText: String,
    appIdText: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = versionText, style = MaterialTheme.typography.bodySmall)
        if (appIdText != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = appIdText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// region 对话框组件
@Composable
fun ConfirmDialog(
    title: String? = null,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissText) }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

data class InputField(
    val label: String,
    val value: String,
    val singleLine: Boolean = true,
    val visualTransformation: VisualTransformation = VisualTransformation.None
)

@Composable
fun InputDialog(
    title: String,
    fields: List<InputField>,
    onFieldChange: (Int, String) -> Unit,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                fields.forEachIndexed { index, field ->
                    OutlinedTextField(
                        value = field.value,
                        onValueChange = { onFieldChange(index, it) },
                        label = { Text(field.label) },
                        singleLine = field.singleLine,
                        visualTransformation = field.visualTransformation,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissText) }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun QRCodeDialog(
    bitmap: Bitmap?,
    onDismiss: () -> Unit
) {
    if (bitmap == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "QR Code",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

/**
 * 通用选择对话框。showRadio 为 true 时显示 RadioButton（单选模式），
 * 否则为普通点击列表模式。selectedOption 仅在 showRadio=true 时用于高亮选中项。
 */
@Composable
fun SelectListDialog(
    title: String? = null,
    options: List<String>,
    selectedOption: String = "",
    onSelected: (Int, String) -> Unit,
    onDismiss: () -> Unit,
    showRadio: Boolean = false
) {
    val selectedIndex = if (showRadio) options.indexOf(selectedOption) else -1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = {
            LazyColumn {
                itemsIndexed(options) { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(index, option) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showRadio) {
                            RadioButton(
                                selected = index == selectedIndex,
                                onClick = { onSelected(index, option) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = if (!showRadio)
                                Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp)
                            else Modifier
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun reorderableElevation(isDragging: Boolean) = animateDpAsState(
    targetValue = if (isDragging) 4.dp else 0.dp,
    label = "ReorderableElevation"
)

@Composable
fun ReorderableListItem(
    scope: ReorderableCollectionItemScope,
    isDragging: Boolean,
    content: @Composable RowScope.() -> Unit
) {
    val elevation by reorderableElevation(isDragging)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = elevation
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(with(scope) { Modifier.longPressDraggableHandle() }),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun ReorderableGridItem(
    scope: ReorderableCollectionItemScope,
    isDragging: Boolean,
    content: @Composable () -> Unit
) {
    val elevation by reorderableElevation(isDragging)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(with(scope) { Modifier.longPressDraggableHandle() }),
        shadowElevation = elevation
    ) {
        content()
    }
}
