package com.v2ray.ang.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.flow.first

// The form owns the draft text. Save only the selection; IME composition belongs to its window.
internal val editorSelectionSaver = listSaver<TextRange, Int>(
    save = { listOf(it.start, it.end) },
    restore = { TextRange(it[0], it[1]) }
)

@Composable
internal fun rememberEditorSelection(initial: TextRange = TextRange.Zero) = rememberSaveable(stateSaver = editorSelectionSaver) {
    mutableStateOf(initial)
}

@Composable
internal fun rememberEditorTextValue(text: String, editor: TvTextFieldState): MutableState<TextFieldValue> {
    val lastInteractive = rememberEditorSelection()
    val input = remember { mutableStateOf(TextFieldValue(text, lastInteractive.value)) }
    RecordInteractiveEditorState(input.value.selection, lastInteractive)
    if (editor.isEditing) {
        val beforeIme = remember { input.value.copy(text = text) }
        LaunchedEffect(editor) {
            snapshotFlow { editor.imeWasVisible }.first { it }
            // TV keyboards can move the caret on attachment. Restore it once unless typing already changed the draft.
            if (input.value.text == beforeIme.text && input.value.selection != beforeIme.selection) {
                input.value = input.value.copy(selection = beforeIme.selection)
            }
        }
    }
    return input
}
