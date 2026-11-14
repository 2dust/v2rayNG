package com.v2ray.ang.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.currentCompositeKeyHashCode
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager

internal val LocalOutboundEditorFocus = compositionLocalOf<OutboundEditorFocus?> { null }

internal class OutboundEditorFocus(private val onFocused: (String) -> Unit) {
    private val targets = mutableMapOf<String, FocusRequester>()

    fun restore(id: String?): Boolean = targets[id]?.requestFocus() == true

    @Composable
    fun target(requester: FocusRequester = remember { FocusRequester() }): Modifier {
        // The form uses fixed composable call sites. This identifies the field independently of
        // its translated label, value, or position among the currently visible controls.
        val id = currentCompositeKeyHashCode.toString()
        DisposableEffect(id, requester) {
            targets[id] = requester
            onDispose { targets.remove(id, requester) }
        }
        return Modifier.focusRequester(requester).onFocusChanged { if (it.isFocused) onFocused(id) }
    }
}

@Composable
internal fun rememberOutboundEditorFocus(fallback: FocusRequester? = null): OutboundEditorFocus {
    val focused = rememberSaveable { mutableStateOf<String?>(null) }
    val focusedWithKeyboard = rememberSaveable { mutableStateOf(false) }
    val initialTarget = remember { focused.value }
    val restoreKeyboardMode = remember { focusedWithKeyboard.value }
    val inputMode = LocalInputModeManager.current
    val focus = remember(inputMode) {
        OutboundEditorFocus {
            focused.value = it
            focusedWithKeyboard.value = inputMode.inputMode == InputMode.Keyboard
        }
    }
    LaunchedEffect(focus) {
        // Scaffold attaches the toolbar and form in its first layout.
        withFrameNanos { }
        // A rebuilt window can start in touch mode, where non-text controls reject focus.
        if (restoreKeyboardMode) inputMode.requestInputMode(InputMode.Keyboard)
        if (!focus.restore(initialTarget)) fallback?.requestFocus()
    }
    return focus
}
