package com.v2ray.ang.ui.server

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.EditorFocus
import com.v2ray.ang.ui.compose.RecordInteractiveEditorState
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.rememberEditorSelection
import kotlinx.coroutines.flow.first

private enum class JsonMode { Browse, Cursor, Keyboard }

internal class TvJsonFocus(val field: Modifier, val passive: Modifier?, val allowKeyboardOnFocus: Boolean)

@Composable
internal fun rememberTvJsonFocus(back: FocusRequester, focus: EditorFocus, imeVisible: State<Boolean>, text: TextFieldState): TvJsonFocus {
    val requester = remember { FocusRequester() }
    val target = focus.target(requester, key = "json")
    val keyboard = LocalSoftwareKeyboardController.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val savedMode = rememberSaveable { mutableStateOf(JsonMode.Browse) }
    var mode by remember { mutableStateOf(savedMode.value) }
    RecordInteractiveEditorState(mode, savedMode)
    val savedSelection = rememberEditorSelection(text.selection)
    val entrySelection = remember { savedSelection.value }
    RecordInteractiveEditorState(text.selection, savedSelection)
    LaunchedEffect(text) {
        if (mode != JsonMode.Browse && text.selection != entrySelection) text.edit { selection = entrySelection }
    }
    var editorFocused by remember { mutableStateOf(false) }
    var restoreBrowse by remember { mutableStateOf(false) }

    if (mode == JsonMode.Keyboard) {
        val selection = remember { savedSelection.value }
        val draft = remember { text.text }
        LaunchedEffect(Unit) {
            requester.requestFocus()
            keyboard?.show()
            snapshotFlow { imeVisible.value }.first { it }
            // Some TV keyboards move the caret when attaching. Leave any new user input alone.
            if (text.selection != selection && text.text.contentEquals(draft)) text.edit { this.selection = selection }
            snapshotFlow { imeVisible.value }.first { !it }
            mode = JsonMode.Cursor
        }
    } else if (restoreBrowse) {
        LaunchedEffect(Unit) { requester.requestFocus(); restoreBrowse = false }
    }
    BackHandler(enabled = mode != JsonMode.Browse) {
        if (imeVisible.value) {
            keyboard?.hide()
        } else {
            restoreBrowse = true
            mode = JsonMode.Browse
        }
    }

    val label = stringResource(R.string.menu_item_edit_config)
    val field = if (mode != JsonMode.Browse) {
        target.semantics { contentDescription = label }
            .focusProperties {
                up = FocusRequester.Cancel
                down = FocusRequester.Cancel
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
            .onPreviewKeyEvent {
                // The IME owns remote navigation while shown. With it closed, let BasicTextField
                // move the cursor, but reserve Center/Enter for reopening the keyboard.
                if (imeVisible.value || (it.key != Key.DirectionCenter && it.key != Key.Enter && it.key != Key.NumPadEnter)) {
                    false
                } else {
                    if (it.type == KeyEventType.KeyUp) mode = JsonMode.Keyboard
                    true
                }
            }
            .onFocusChanged {
                if (editorFocused && !it.isFocused && lifecycle.currentState == Lifecycle.State.RESUMED) {
                    keyboard?.hide()
                    mode = JsonMode.Browse
                }
                editorFocused = it.isFocused
            }
    } else Modifier.focusProperties { canFocus = false }.clearAndSetSemantics { }

    val passive = if (mode != JsonMode.Browse) null else {
        target.focusProperties { start = back }.dpadFocusOutline(cornerRadius = 8.dp)
            .semantics { contentDescription = label }
            .clickable(interactionSource = null, indication = null, role = Role.Button) {
                mode = JsonMode.Keyboard
            }
    }
    return TvJsonFocus(field, passive, mode == JsonMode.Keyboard)
}
