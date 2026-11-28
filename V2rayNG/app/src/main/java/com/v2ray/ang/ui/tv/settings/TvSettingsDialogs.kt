package com.v2ray.ang.ui.tv.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.v2ray.ang.R
import com.v2ray.ang.ui.tv.compose.dpadFocusOutline
import com.v2ray.ang.ui.tv.compose.rememberTvTextFieldState
import com.v2ray.ang.ui.tv.compose.requestFocusWhenReady
import com.v2ray.ang.ui.tv.compose.tvTextFieldEditorFocus

@Composable
internal fun TvSettingsEditDialog(title: String, value: String, isPassword: Boolean, keyboardNumber: Boolean,
    onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val draft = remember { TextFieldState(initialText = value) }
    val field = remember { FocusRequester() }
    val cancel = remember { FocusRequester() }
    val confirm = remember { FocusRequester() }
    var overlayPadding by remember { mutableStateOf(0.dp) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.navigationBarsPadding().imePadding().padding(bottom = overlayPadding),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text(title) },
        text = {
            val focus = rememberTvTextFieldState(field)
            val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
            // Android 11 TV overlay IMEs can be visible with a zero inset. Drop this clearance
            // once supported TV keyboards consistently report their occupied window bounds.
            val clearance = tvSettingsImeClearance(focus.imeWasVisible, imeBottom)
            SideEffect { overlayPadding = clearance }
            val activeField = if (focus.isEditing) focus.editorFocusRequester else field
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (focus.isEditing) {
                    OutlinedTextField(
                        state = draft,
                        modifier = Modifier.fillMaxWidth().focusProperties { down = cancel }
                            .bringIntoViewRequester(focus.bringIntoViewRequester).tvTextFieldEditorFocus(focus),
                        label = { Text(title) },
                        keyboardOptions = when {
                            isPassword -> KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
                            keyboardNumber -> KeyboardOptions(keyboardType = KeyboardType.Number)
                            else -> KeyboardOptions.Default
                        },
                        lineLimits = if (isPassword || keyboardNumber) TextFieldLineLimits.SingleLine else TextFieldLineLimits.MultiLine(maxHeightInLines = 5)
                    )
                } else {
                    OutlinedButton(onClick = focus::beginEditing, modifier = Modifier.fillMaxWidth()
                        .focusProperties { down = cancel }.dpadFocusOutline(field, 24.dp)) {
                        Text(draft.text.toString().ifEmpty { title }, maxLines = 3)
                    }
                }
            }
            LaunchedEffect(Unit) { requestFocusWhenReady(field) }
            TvSettingsEditActions(activeField, cancel, confirm, onDismiss) { onConfirm(draft.text.toString()) }
        },
        confirmButton = {},
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun TvSettingsEditActions(
    activeField: FocusRequester, cancel: FocusRequester, confirm: FocusRequester, onDismiss: () -> Unit, onConfirm: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDismiss, modifier = Modifier.focusProperties { up = activeField; end = confirm }.dpadFocusOutline(cancel, 24.dp)) {
            Text(stringResource(R.string.action_cancel))
        }
        TextButton(onClick = onConfirm, modifier = Modifier.focusProperties { up = activeField; start = cancel }.dpadFocusOutline(confirm, 24.dp)) {
            Text(stringResource(R.string.action_ok))
        }
    }
}

@Composable
internal fun TvSettingsListDialog(title: String, options: List<Pair<String, String>>, selectedValue: String?,
    onDismiss: () -> Unit, onSelected: (String) -> Unit) {
    val index = options.indexOfFirst { it.second == selectedValue }.coerceAtLeast(0)
    val initialValue = options.getOrNull(index)?.second
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = index)
    val selected = remember { FocusRequester() }
    val cancel = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(state = listState) {
                items(options, key = { it.second }) { (label, value) ->
                    val checked = value == selectedValue
                    Row(
                        Modifier.fillMaxWidth().dpadFocusOutline(selected.takeIf { value == initialValue })
                            .selectable(checked, role = Role.RadioButton) { onSelected(value) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(checked, onClick = null, modifier = Modifier.clearAndSetSemantics {})
                        Text(label)
                    }
                }
            }
            // Focus once per opening, not every time the selected option scrolls back into view.
            LaunchedEffect(Unit) { requestFocusWhenReady(if (options.isEmpty()) cancel else selected) }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.dpadFocusOutline(cancel, 24.dp)) { Text(stringResource(R.string.action_cancel)) }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

internal fun tvSettingsImeClearance(visible: Boolean, bottomInset: Int) = if (visible && bottomInset == 0) 240.dp else 0.dp
