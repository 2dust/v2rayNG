package com.v2ray.ang.ui.compose

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

@Composable
fun ConfirmDialog(
    title: String? = null,
    message: String,
    confirmText: String = stringResource(R.string.action_ok),
    dismissText: String? = stringResource(R.string.action_cancel),
    confirmIcon: @Composable (() -> Unit)? = null,
    messageTextAlign: TextAlign = TextAlign.Start,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val confirmFocusRequester = remember { FocusRequester() }
    val dismissFocusRequester = remember { FocusRequester() }

    LaunchedEffect(dismissText) {
        requestFocusWhenReady(if (dismissText != null) dismissFocusRequester else confirmFocusRequester)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = {
            Text(message, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium,
                textAlign = messageTextAlign)
        },
        confirmButton = {
            AppDialogButton(confirmText, { onConfirm(); onDismiss() }, icon = confirmIcon,
                focusRequester = confirmFocusRequester)
        },
        dismissButton = dismissText?.let { text ->
            { AppDialogButton(text, onDismiss, focusRequester = dismissFocusRequester) }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun DeleteConfirmDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    itemName: String? = null
) {
    val iconSize = if (isTelevisionDevice()) 24.dp else 18.dp
    ConfirmDialog(
        message = if (itemName.isNullOrBlank()) message else "$message\n\n$itemName",
        messageTextAlign = TextAlign.Center,
        confirmText = stringResource(R.string.action_delete),
        confirmIcon = { Icon(painterResource(R.drawable.ic_delete_24dp), null, Modifier.size(iconSize)) },
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
internal fun AppDialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    focusRequester: FocusRequester? = null
) {
    if (!isTelevisionDevice()) {
        TextButton(
            onClick = onClick,
            modifier = focusRequester?.let { modifier.focusRequester(it) } ?: modifier
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text)
        }
        return
    }
    val shape = RoundedCornerShape(24.dp)
    Button(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .widthIn(min = 96.dp)
            .dpadFocusOutline(focusRequester = focusRequester, cornerRadius = 24.dp),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp)
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

data class InputField(
    val label: String,
    val value: String,
    val singleLine: Boolean = true,
    val keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
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
    val isTelevision = isTelevisionDevice()
    val firstFieldFocusRequester = rememberDpadFocusRequester(requestFocus = isTelevision && fields.isNotEmpty())
    val remainingFieldFocusRequesters = remember(fields.size) {
        List((fields.size - 1).coerceAtLeast(0)) { FocusRequester() }
    }
    val fieldFocusRequesters = remember(firstFieldFocusRequester, remainingFieldFocusRequesters) {
        if (fields.isEmpty()) {
            emptyList()
        } else {
            listOf(firstFieldFocusRequester) + remainingFieldFocusRequesters
        }
    }
    val dismissFocusRequester = remember { FocusRequester() }
    val confirmFocusRequester = remember { FocusRequester() }
    val buttonFocusOrder = remember(dismissFocusRequester, confirmFocusRequester) {
        listOf(dismissFocusRequester, confirmFocusRequester)
    }
    val contentScrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current

    if (isTelevision) {
        LaunchedEffect(Unit) {
            keyboardController?.hide()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .tvAwareImePadding()
                    .verticalScrollbar(contentScrollState)
                    .verticalScroll(contentScrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                fields.forEachIndexed { index, field ->
                    val tvFieldState = if (isTelevision) {
                        rememberTvTextFieldState(passiveFocusRequester = fieldFocusRequesters[index])
                    } else {
                        null
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvAwareTextFieldFocus(
                                state = tvFieldState,
                                enabled = true,
                                navigation = TvTextFieldNavigation(
                                    onMoveUp = {
                                        if (index > 0) {
                                            fieldFocusRequesters[index - 1].requestFocus()
                                        } else {
                                            true
                                        }
                                    },
                                    onMoveDown = {
                                        fieldFocusRequesters.getOrNull(index + 1)
                                            ?.requestFocus()
                                            ?: dismissFocusRequester.requestFocus()
                                    }
                                ),
                                onActivate = { tvFieldState?.beginEditing() }
                            )
                    ) {
                        OutlinedTextField(
                            value = field.value,
                            onValueChange = { onFieldChange(index, it) },
                            spec = OutlinedTextFieldSpec(
                                label = field.label,
                                singleLine = field.singleLine,
                                maxLines = if (field.singleLine) 1 else 5,
                                keyboardOptions = field.keyboardOptions,
                                visualTransformation = field.visualTransformation
                            ),
                            tvFieldState = tvFieldState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(tvFieldState?.let { Modifier.tvTextFieldEditorFocus(it) } ?: Modifier)
                        )
                    }
                }
            }
        },
        confirmButton = {
            AppDialogButton(
                text = confirmText,
                onClick = onConfirm,
                focusRequester = confirmFocusRequester,
                modifier = Modifier
                    .dpadOrderedFocusNavigation(current = confirmFocusRequester, order = buttonFocusOrder)
                    .dpadVerticalFocusNavigation(
                        onMoveUp = {
                            fieldFocusRequesters.lastOrNull()?.requestFocus() ?: true
                        },
                        onMoveDown = { true }
                    )
            )
        },
        dismissButton = {
            AppDialogButton(
                text = dismissText,
                onClick = onDismiss,
                focusRequester = dismissFocusRequester,
                modifier = Modifier
                    .dpadOrderedFocusNavigation(current = dismissFocusRequester, order = buttonFocusOrder)
                    .dpadVerticalFocusNavigation(
                        onMoveUp = {
                            fieldFocusRequesters.lastOrNull()?.requestFocus() ?: true
                        },
                        onMoveDown = { true }
                    )
            )
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
    val closeFocusRequester = rememberDpadFocusRequester()
    val closeText = stringResource(R.string.action_close)
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
            AppDialogButton(text = closeText, onClick = onDismiss, focusRequester = closeFocusRequester)
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
    showRadio: Boolean = false,
    onMovePrevious: (() -> Unit)? = null
) {
    val isTelevision = isTelevisionDevice()
    val selectedIndex = if (showRadio) options.indexOf(selectedOption) else -1
    val initialIndex = selectedIndex.takeIf { it >= 0 } ?: 0
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val firstOptionFocusRequester = rememberDpadFocusRequester(requestFocus = options.isNotEmpty())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = {
            LazyColumn(
                state = listState,
                modifier = if (onMovePrevious != null) {
                    Modifier.dpadPopupHorizontalNavigation(onMovePrevious)
                } else {
                    Modifier
                },
                verticalArrangement = if (isTelevision && !showRadio) {
                    Arrangement.spacedBy(8.dp)
                } else {
                    Arrangement.Top
                }
            ) {
                items(options.size) { index ->
                    val option = options[index]
                    val isSelected = option == selectedOption
                    if (isTelevision && !showRadio) {
                        AppDialogButton(
                            text = optionText(option),
                            onClick = { onSelected(option) },
                            focusRequester = if (index == initialIndex) {
                                firstOptionFocusRequester
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        DialogOptionRow(
                            text = optionText(option),
                            selected = isSelected.takeIf { showRadio },
                            onClick = { onSelected(option) },
                            focusRequester = firstOptionFocusRequester.takeIf { index == initialIndex }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            AppDialogButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
internal fun DialogOptionRow(
    text: String,
    selected: Boolean?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    contentPadding: PaddingValues = PaddingValues(vertical = 4.dp)
) {
    val selection = if (selected == null) Modifier.dpadClickable(onClick = onClick)
    else Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick)
    Row(
        modifier.fillMaxWidth().dpadFocusOutline(focusRequester).then(selection).padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected != null) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
