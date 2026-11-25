package com.v2ray.ang.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Phone and unrelated forms keep their existing controls.
internal val LocalTvEditor = compositionLocalOf<FocusRequester?> { null }

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
internal fun TvEditorTextField(
    label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier,
    enabled: Boolean, keyboardType: KeyboardType, placeholder: String?, maxLines: Int,
    isError: Boolean, supportingText: String?
) {
    val back = checkNotNull(LocalTvEditor.current)
    val requester = remember { FocusRequester() }
    val editor = rememberTvTextFieldState(requester)
    // The passive button and the active editor represent the same field across recreation.
    val fieldFocus = LocalEditorFocus.current?.target() ?: Modifier
    var input by rememberEditorTextValue(value, editor)
    LaunchedEffect(enabled) { if (!enabled && editor.isEditing) editor.finishEditing() }
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = input.copy(text = value),
            onValueChange = {
                if (editor.isEditing) {
                    input = it
                    if (it.text != value) onValueChange(it.text)
                }
            },
            readOnly = !editor.isEditing,
            enabled = enabled,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            supportingText = supportingText?.let { { Text(it) } },
            isError = isError,
            maxLines = maxLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            interactionSource = editor.interactionSource,
            modifier = Modifier.fillMaxWidth()
                .then(if (editor.isEditing) fieldFocus else Modifier)
                .tvTextFieldEditorFocus(editor)
                .onSizeChanged { editor.measuredSize = it }
                .bringIntoViewRequester(editor.bringIntoViewRequester)
                .then(if (editor.isEditing) Modifier else Modifier.clearAndSetSemantics { })
        )
        if (!editor.isEditing) {
            Box(
                Modifier.matchParentSize()
                    .then(fieldFocus)
                    .focusRequester(requester)
                    .focusProperties { start = back }
                    .semantics { contentDescription = if (supportingText == null) label else "$label. $supportingText" }
                    .clickable(interactionSource = editor.interactionSource, indication = null, enabled = enabled,
                        role = Role.Button, onClick = editor::beginEditing)
            )
        }
    }
}
