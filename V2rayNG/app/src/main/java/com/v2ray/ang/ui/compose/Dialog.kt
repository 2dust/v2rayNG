package com.v2ray.ang.ui.compose

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

private val DialogGap = 8.dp
private val OptionVerticalPad = 12.dp
private val OptionHorizontalPad = 4.dp
private val ConfirmIconSize = 18.dp
private const val OptionContentType = "select-option"

@Composable
fun ConfirmDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
    confirmText: String = stringResource(R.string.action_ok),
    dismissText: String? = stringResource(R.string.action_cancel),
    scrollableMessage: Boolean = false,
    confirmIcon: @Composable (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = if (scrollableMessage) {
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .verticalScrollbar(scrollState)
                } else {
                    Modifier
                }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                if (confirmIcon != null) {
                    confirmIcon()
                    Spacer(Modifier.width(DialogGap))
                }
                Text(confirmText)
            }
        },
        dismissButton = dismissText?.let { text ->
            { TextButton(onClick = onDismiss) { Text(text) } }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun DeleteConfirmDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmDialog(
        message = message,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.action_delete),
        confirmIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_delete_24dp),
                contentDescription = null,
                modifier = Modifier.size(ConfirmIconSize)
            )
        }
    )
}

@Immutable
data class InputField(
    val label: String,
    val value: String,
    val singleLine: Boolean = true,
    val keyboardType: KeyboardType = KeyboardType.Text,
    val visualTransformation: VisualTransformation = VisualTransformation.None,
    val onValueChange: (String) -> Unit
)

@Composable
fun InputDialog(
    title: String,
    fields: List<InputField>,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DialogGap)
            ) {
                fields.forEach { field ->
                    OutlinedTextField(
                        value = field.value,
                        onValueChange = field.onValueChange,
                        label = { Text(field.label) },
                        singleLine = field.singleLine,
                        maxLines = if (field.singleLine) 1 else 5,
                        keyboardOptions = KeyboardOptions(keyboardType = field.keyboardType),
                        visualTransformation = field.visualTransformation,
                        colors = appFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun QRCodeDialog(bitmap: Bitmap?, onDismiss: () -> Unit) {
    if (bitmap == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.acc_qr_code),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun <T> SelectListDialog(
    options: List<T>,
    optionText: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
    selectedOption: T? = null,
    showRadio: Boolean = false,
    optionKey: (T) -> Any = { it as Any }
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = {
            LazyColumn {
                items(
                    items = options,
                    key = { optionKey(it) },
                    contentType = { OptionContentType }
                ) { option ->
                    val isSelected = option == selectedOption
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (showRadio) {
                                    Modifier.selectable(
                                        selected = isSelected,
                                        onClick = { onSelected(option) },
                                        role = Role.RadioButton
                                    )
                                } else {
                                    Modifier.clickable { onSelected(option) }
                                }
                            )
                            .padding(
                                vertical = OptionVerticalPad,
                                horizontal = OptionHorizontalPad
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showRadio) {
                            RadioButton(selected = isSelected, onClick = null)
                            Spacer(modifier = Modifier.width(DialogGap))
                        }
                        Text(
                            text = optionText(option),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

// ===== previews =====

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConfirmDialogPreview() = AppTheme {
    ConfirmDialog(
        title = "Remove",
        message = "A confirmation body long enough to wrap onto a second and even a third line.",
        onConfirm = {},
        onDismiss = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun InputDialogPreview() = AppTheme {
    InputDialog(
        title = "Socks port",
        fields = listOf(
            InputField(
                label = "Port",
                value = "10808",
                keyboardType = KeyboardType.Number,
                onValueChange = {}
            )
        ),
        confirmText = "OK",
        dismissText = "Cancel",
        onConfirm = {},
        onDismiss = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun SelectListDialogPreview() = AppTheme {
    SelectListDialog(
        title = "Log level",
        options = listOf("warning", "debug", "info"),
        optionText = { it },
        selectedOption = "warning",
        showRadio = true,
        onSelected = {},
        onDismiss = {}
    )
}
