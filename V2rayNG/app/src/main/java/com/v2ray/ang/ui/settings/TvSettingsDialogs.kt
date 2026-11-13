package com.v2ray.ang.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.v2ray.ang.R

@Composable
internal fun Modifier.tvSettingsFocusOutline(radius: Dp = 12.dp): Modifier {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(radius)
    return onFocusChanged { focused = it.isFocused }
        .then(
            if (focused) Modifier.background(MaterialTheme.colorScheme.primaryContainer, shape)
                .border(2.dp, MaterialTheme.colorScheme.primary, shape)
            else Modifier
        )
        .clip(shape)
}

internal enum class TvSettingsEditFocus {
    Field, OpeningEditor, Editor, EditorWithIme, Actions;

    val isEditing get() = this == OpeningEditor || this == Editor || this == EditorWithIme

    fun onImeVisibilityChanged(visible: Boolean): TvSettingsEditFocus = when {
        this == Editor && visible -> EditorWithIme
        this == EditorWithIme && !visible -> Field
        else -> this
    }

    // Replacing the field can briefly focus an action before the requested field attaches.
    fun onActionFocused(): TvSettingsEditFocus = if (this == Editor || this == EditorWithIme) Actions else this
}

@Composable
internal fun TvSettingsEditDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isPassword: Boolean,
    keyboardNumber: Boolean
) {
    var focus by remember { mutableStateOf(TvSettingsEditFocus.Field) }
    val field = remember { FocusRequester() }
    val cancel = remember { FocusRequester() }
    val confirm = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var overlayPadding by remember { mutableStateOf(0.dp) }
    fun onActionFocused() {
        val previous = focus
        focus = previous.onActionFocused()
        if (previous.isEditing && !focus.isEditing) keyboard?.hide()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.navigationBarsPadding().imePadding().padding(bottom = overlayPadding),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text(title) },
        text = {
            val imeVisible = isTvSettingsImeVisible()
            val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
            SideEffect { overlayPadding = tvSettingsImeOverlayPadding(imeVisible, imeBottom) }
            LaunchedEffect(focus, imeVisible) {
                focus = focus.onImeVisibilityChanged(imeVisible)
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (focus.isEditing) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        maxLines = 5,
                        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(keyboardType = when {
                            keyboardNumber -> KeyboardType.Number
                            isPassword -> KeyboardType.Password
                            else -> KeyboardType.Text
                        }),
                        modifier = Modifier.fillMaxWidth()
                            .focusRequester(field)
                            .semantics { contentDescription = title }
                            .focusProperties { down = cancel }
                            .onFocusChanged {
                                if (it.isFocused && focus == TvSettingsEditFocus.OpeningEditor) focus = TvSettingsEditFocus.Editor
                            }
                    )
                    LaunchedEffect(field) {
                        field.requestFocus()
                        keyboard?.show()
                    }
                } else {
                    OutlinedButton(
                        onClick = { focus = TvSettingsEditFocus.OpeningEditor },
                        modifier = Modifier.fillMaxWidth().focusRequester(field)
                            .focusProperties { down = cancel }
                            .tvSettingsFocusOutline(24.dp)
                    ) {
                        Text(if (value.isEmpty()) title else if (isPassword) "•".repeat(value.length) else value)
                    }
                    if (focus == TvSettingsEditFocus.Field) LaunchedEffect(field) { field.requestFocus() }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.focusRequester(cancel)
                    .focusProperties { up = field; end = confirm }
                    .onFocusChanged { if (it.isFocused) onActionFocused() }
                    .tvSettingsFocusOutline(24.dp)
            ) { Text(stringResource(R.string.action_cancel)) }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.focusRequester(confirm)
                    .focusProperties { up = field; start = cancel }
                    .onFocusChanged { if (it.isFocused) onActionFocused() }
                    .tvSettingsFocusOutline(24.dp)
            ) { Text(stringResource(R.string.action_ok)) }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
internal fun TvSettingsListDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedValue: String?,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val selected = remember { selectedValue }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = options.indexOfFirst { it.second == selected }.coerceAtLeast(0))
    val initialFocus = remember { FocusRequester() }
    val cancel = remember { FocusRequester() }
    var needsInitialFocus by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(state = listState) {
                items(options, key = { it.second }) { (label, value) ->
                    val isSelected = value == selectedValue
                    val focus = if (value == selected) Modifier.focusRequester(initialFocus) else Modifier
                    Row(
                        modifier = focus.fillMaxWidth().tvSettingsFocusOutline()
                            .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelected(value) })
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                    if (value == selected && needsInitialFocus) {
                        LaunchedEffect(initialFocus) {
                            needsInitialFocus = !initialFocus.requestFocus()
                        }
                    }
                }
            }
            if (options.isEmpty()) LaunchedEffect(cancel) { cancel.requestFocus() }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.focusRequester(cancel).tvSettingsFocusOutline(24.dp)) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

// The Android 11 TV overlay keyboard can report visibility with no bottom inset. Reserve space
// for the dialog actions only in that case; remove the fallback when supported TV IMEs report insets.
internal fun tvSettingsImeOverlayPadding(visible: Boolean, bottomInset: Int): Dp =
    if (visible && bottomInset == 0) 240.dp else 0.dp

// Remove the opt-in when isImeVisible is stable. A zero-height inset alone cannot tell us
// when an Android TV overlay keyboard has closed.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun isTvSettingsImeVisible(): Boolean = WindowInsets.isImeVisible
