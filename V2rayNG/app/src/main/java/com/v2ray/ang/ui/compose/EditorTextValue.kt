package com.v2ray.ang.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

// The form owns the draft text. Save only the selection; IME composition belongs to its window.
internal fun editorSelectionSaver(text: String) = listSaver<TextFieldValue, Int>(
    save = { listOf(it.selection.start, it.selection.end) },
    restore = { TextFieldValue(text, TextRange(it[0], it[1])) }
)

@Composable
internal fun rememberEditorTextValue(text: String) = rememberSaveable(stateSaver = editorSelectionSaver(text)) {
    mutableStateOf(TextFieldValue(text))
}
