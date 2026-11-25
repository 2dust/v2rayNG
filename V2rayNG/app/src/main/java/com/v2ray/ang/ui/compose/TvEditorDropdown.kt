package com.v2ray.ang.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

internal fun tvEditorOptionIndex(options: List<String>, value: String): Int =
    options.indexOf(value).coerceAtLeast(0)

@Composable
internal fun TvEditorDropdown(
    label: String, value: String, options: List<String>, onValueChange: (String) -> Unit,
    modifier: Modifier, enabled: Boolean, placeholder: String?, supportingText: String?,
    optionLabel: (String) -> String = { it }
) {
    val back = checkNotNull(LocalTvEditor.current)
    val anchor = remember { FocusRequester() }
    val anchorFocus = LocalEditorFocus.current?.target(anchor) ?: Modifier.focusRequester(anchor)
    val interaction = remember { MutableInteractionSource() }
    var open by remember { mutableStateOf(false) }
    var restoreAnchor by remember { mutableStateOf(false) }
    fun close() {
        open = false
        restoreAnchor = true
    }
    LaunchedEffect(open) {
        if (!open && restoreAnchor) {
            anchor.requestFocus()
            restoreAnchor = false
        }
    }
    val displayValue = optionLabel(value)
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            supportingText = supportingText?.let { { Text(it) } },
            trailingIcon = { Icon(painterResource(R.drawable.ic_expand_more_24dp), contentDescription = null) },
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth().focusProperties { canFocus = false }.clearAndSetSemantics { }
        )
        Box(
            Modifier.matchParentSize()
                .then(anchorFocus)
                .focusProperties { start = back }
                .semantics { contentDescription = if (displayValue.isBlank()) label else "$label: $displayValue" }
                .clickable(interactionSource = interaction, indication = null, enabled = enabled && options.isNotEmpty(), role = Role.Button) {
                    open = true
                }
        )
    }
    if (open) {
        TvEditorOptions(label, value, options, optionLabel, onDismiss = ::close, onSelect = { close(); onValueChange(it) })
    }
}

@Composable
private fun TvEditorOptions(
    label: String, value: String, options: List<String>, optionLabel: (String) -> String,
    onDismiss: () -> Unit, onSelect: (String) -> Unit
) {
    val initialIndex = remember { tvEditorOptionIndex(options, value) }
    val selected = remember { FocusRequester() }
    val scroll = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    var needsFocus by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            LazyColumn(state = scroll) {
                itemsIndexed(options) { index, option ->
                    val focus = if (index == initialIndex) Modifier.focusRequester(selected) else Modifier
                    Row(
                        focus.fillMaxWidth().dpadFocusOutline(cornerRadius = 8.dp)
                            .selectable(selected = option == value, role = Role.RadioButton, onClick = { onSelect(option) })
                            .padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == value, onClick = null, modifier = Modifier.padding(12.dp))
                        Text(optionLabel(option).ifBlank { "---" }, modifier = Modifier.padding(vertical = 12.dp))
                    }
                    if (index == initialIndex && needsFocus) {
                        LaunchedEffect(selected) { needsFocus = !selected.requestFocus() }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.dpadFocusOutline(cornerRadius = 24.dp)) { Text(stringResource(R.string.action_cancel)) } }
    )
}
