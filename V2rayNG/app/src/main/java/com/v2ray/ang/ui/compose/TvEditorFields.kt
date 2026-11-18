package com.v2ray.ang.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

// Only the outbound TV shell provides this; phone and unrelated forms keep their existing controls.
internal val LocalTvEditor = compositionLocalOf<TvEditorNavigation?> { null }

internal class TvEditorNavigation(val back: FocusRequester, val imeVisible: State<Boolean>)

internal fun tvEditorOverlayPadding(visible: Boolean, inset: Int): Dp =
    if (visible && inset == 0) 240.dp else 0.dp

internal class TvEditorIme(val visible: State<Boolean>, val padding: Modifier)

// Android 11 TV keyboards can report visibility without any bottom inset. Keep the visibility
// opt-in here until isImeVisible is stable; remove the extra space when supported IMEs report insets.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun rememberTvEditorIme(): TvEditorIme {
    val visible = WindowInsets.isImeVisible
    val padding = tvEditorOverlayPadding(visible, WindowInsets.ime.getBottom(LocalDensity.current))
    return TvEditorIme(rememberUpdatedState(visible), Modifier.imePadding().padding(bottom = padding))
}

@Composable
internal fun Modifier.tvEditorFocus(radius: Dp = 8.dp, visible: Boolean = true): Modifier {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(radius)
    return onFocusChanged { focused = it.isFocused }
        .then(
            if (focused && visible) Modifier.background(MaterialTheme.colorScheme.primaryContainer, shape)
                .border(2.dp, MaterialTheme.colorScheme.primary, shape)
            else Modifier
        )
        .clip(shape)
}

@Composable
internal fun TvEditorTextField(
    label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier,
    enabled: Boolean, keyboardType: KeyboardType, placeholder: String?, maxLines: Int,
    isError: Boolean, supportingText: String?
) {
    val navigation = checkNotNull(LocalTvEditor.current)
    val keyboard = LocalSoftwareKeyboardController.current
    val requester = remember { FocusRequester() }
    val fieldFocus = LocalOutboundEditorFocus.current?.target(requester) ?: Modifier.focusRequester(requester)
    val bringIntoView = remember { BringIntoViewRequester() }
    val interaction = remember { MutableInteractionSource() }
    var editing by remember { mutableStateOf(false) }
    var editorFocused by remember { mutableStateOf(false) }
    var sawIme by remember { mutableStateOf(false) }
    var restorePassive by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val imeVisible by navigation.imeVisible

    LaunchedEffect(editing, enabled) {
        if (editing && enabled) {
            if (requester.requestFocus()) keyboard?.show()
        } else if (restorePassive) {
            requester.requestFocus()
            restorePassive = false
        }
    }
    LaunchedEffect(editing, editorFocused, imeVisible, size) {
        if (!editing || !editorFocused) return@LaunchedEffect
        if (imeVisible) {
            sawIme = true
            // Wait for the scroll viewport and a newly wrapped field to finish measuring before
            // relocating it. Repeating on size changes keeps the entire multiline editor visible.
            withFrameNanos { }
            withFrameNanos { }
            bringIntoView.bringIntoView()
        } else if (sawIme) {
            restorePassive = true
            editing = false
        }
    }
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (editing) onValueChange(it) },
            readOnly = !editing,
            enabled = enabled,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            supportingText = supportingText?.let { { Text(it) } },
            isError = isError,
            maxLines = maxLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth()
                .then(if (editing) fieldFocus else Modifier)
                .focusProperties { canFocus = editing && enabled }
                .onFocusChanged {
                    if (editorFocused && !it.isFocused && editing) {
                        editing = false
                        keyboard?.hide()
                    }
                    editorFocused = it.isFocused
                }
                .onSizeChanged { size = it }
                .bringIntoViewRequester(bringIntoView)
                .then(if (editing) Modifier else Modifier.clearAndSetSemantics { })
        )
        if (!editing) {
            Box(
                Modifier.matchParentSize()
                    .then(fieldFocus)
                    .focusProperties { start = navigation.back }
                    .semantics { contentDescription = if (supportingText == null) label else "$label. $supportingText" }
                    .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) {
                        sawIme = false
                        editing = true
                    }
            )
        }
    }
}

internal fun tvEditorOptionIndex(options: List<String>, value: String): Int =
    options.indexOf(value).coerceAtLeast(0)

@Composable
internal fun TvEditorDropdown(
    label: String, value: String, options: List<String>, onValueChange: (String) -> Unit,
    modifier: Modifier, enabled: Boolean, placeholder: String?, supportingText: String?,
    optionLabel: (String) -> String = { it }
) {
    val back = checkNotNull(LocalTvEditor.current).back
    val anchor = remember { FocusRequester() }
    val anchorFocus = LocalOutboundEditorFocus.current?.target(anchor) ?: Modifier.focusRequester(anchor)
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
                        focus.fillMaxWidth().tvEditorFocus()
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
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.tvEditorFocus(24.dp)) { Text(stringResource(R.string.action_cancel)) } }
    )
}
