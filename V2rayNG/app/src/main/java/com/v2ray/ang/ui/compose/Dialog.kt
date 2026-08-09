package com.v2ray.ang.ui.compose

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

@Composable
fun ConfirmDialog(
    title: String? = null,
    message: String,
    confirmText: String = stringResource(R.string.action_ok),
    dismissText: String? = stringResource(R.string.action_cancel),
    confirmIcon: @Composable (() -> Unit)? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val confirmFocusRequester = remember { FocusRequester() }
    val dismissFocusRequester = remember { FocusRequester() }

    LaunchedEffect(dismissText) {
        if (dismissText != null) dismissFocusRequester.requestFocus()
        else confirmFocusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(); onDismiss() },
                modifier = Modifier.focusRequester(confirmFocusRequester)
            ) {
                confirmIcon?.invoke()
                if (confirmIcon != null) Spacer(Modifier.width(8.dp))
                Text(confirmText)
            }
        },
        dismissButton = dismissText?.let { text ->
            {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(dismissFocusRequester)
                ) {
                    Text(text)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun DeleteConfirmDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        message = message,
        confirmText = stringResource(R.string.action_delete),
        confirmIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_delete_24dp),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        onConfirm = onConfirm,
        onDismiss = onDismiss
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
                        singleLine = false,
                        maxLines = 5,
                        visualTransformation = field.visualTransformation,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.secondary,
                            selectionColors = TextSelectionColors(
                                handleColor = MaterialTheme.colorScheme.secondary,
                                backgroundColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                            )
                        ),
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

/**
 * When showRadio is true, displays RadioButton (single selection mode);
 * otherwise, plain clickable list mode.
 * The selectedOption parameter is used to highlight the selected item only when showRadio is true.
 */
@Composable
fun <T> SelectListDialog(
    options: List<T>,
    optionText: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
    selectedOption: T? = null,
    showRadio: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = {
            LazyColumn {
                items(options) { option ->
                    val isSelected = option == selectedOption
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (showRadio) Modifier.selectable(
                                    selected = isSelected,
                                    onClick = { onSelected(option) },
                                    role = Role.RadioButton
                                ) else Modifier.clickable { onSelected(option) }
                            )
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showRadio) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
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
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
