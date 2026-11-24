package com.v2ray.ang.ui.server

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.OutboundEditorFocus
import com.v2ray.ang.ui.compose.tvEditorFocus

internal class TvJsonFocus(val field: Modifier, val passive: Modifier?, val allowKeyboardOnFocus: Boolean)

@Composable
internal fun rememberTvJsonFocus(back: FocusRequester, focus: OutboundEditorFocus, imeVisible: State<Boolean>): TvJsonFocus {
    val requester = remember { FocusRequester() }
    val target = focus.target(requester, key = "json")
    val keyboard = LocalSoftwareKeyboardController.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var editing by rememberSaveable { mutableStateOf(false) }
    var allowKeyboardOnFocus by remember { mutableStateOf(false) }
    var editorFocused by remember { mutableStateOf(false) }
    var restoreBrowse by remember { mutableStateOf(false) }

    LaunchedEffect(editing) {
        // A recreation restores the cursor target through the shared focus registry. Only an
        // explicit activation should show the keyboard again.
        if (editing && allowKeyboardOnFocus) {
            if (requester.requestFocus()) keyboard?.show()
        } else if (restoreBrowse) {
            requester.requestFocus()
            restoreBrowse = false
        }
    }
    BackHandler(enabled = editing) {
        if (imeVisible.value) {
            keyboard?.hide()
        } else {
            restoreBrowse = true
            editing = false
        }
    }

    val label = stringResource(R.string.menu_item_edit_config)
    val field = if (editing) {
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
                    if (it.type == KeyEventType.KeyUp) {
                        // BasicTextField starts an input session when this option changes while
                        // focused. A direct show() alone cannot reopen a session after recreation.
                        if (allowKeyboardOnFocus) keyboard?.show() else allowKeyboardOnFocus = true
                    }
                    true
                }
            }
            .onFocusChanged {
                if (editorFocused && !it.isFocused && editing && lifecycle.currentState == Lifecycle.State.RESUMED) {
                    keyboard?.hide()
                    editing = false
                }
                editorFocused = it.isFocused
            }
    } else Modifier.focusProperties { canFocus = false }.clearAndSetSemantics { }

    val passive = if (editing) null else {
        target.focusProperties { start = back }.tvEditorFocus()
            .semantics { contentDescription = label }
            .clickable(interactionSource = null, indication = null, role = Role.Button) {
                allowKeyboardOnFocus = true
                editing = true
            }
    }
    return TvJsonFocus(field, passive, allowKeyboardOnFocus)
}
