package com.v2ray.ang.ui.subscription

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.v2ray.ang.ui.compose.formTextFieldColors
import com.v2ray.ang.ui.compose.rememberEditorTextValue

private enum class FieldFocus { None, Focused, Editing }

@Composable
internal fun SubscriptionTextField(label: String, value: String, onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text, isError: Boolean = false, supportingText: String? = null) {
    var input by rememberEditorTextValue(value)
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    var lastFocus by rememberSaveable { mutableStateOf(FieldFocus.None) }
    val restore = remember { lastFocus }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    // Detaching the old window clears Compose focus; it is not a user leaving the field.
    SideEffect {
        if (lifecycle.currentState == Lifecycle.State.RESUMED) {
            lastFocus = if (!focused) FieldFocus.None else if (imeVisible) FieldFocus.Editing else FieldFocus.Focused
        }
    }

    OutlinedTextField(
        value = input.copy(text = value),
        onValueChange = { input = it; if (it.text != value) onValueChange(it.text) },
        label = { Text(label) }, isError = isError, supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType), maxLines = 5, colors = formTextFieldColors(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .focusRequester(requester).onFocusChanged { focused = it.isFocused }
    )
    LaunchedEffect(Unit) {
        if (restore == FieldFocus.None) return@LaunchedEffect
        withFrameNanos { }
        if (requester.requestFocus()) {
            if (restore == FieldFocus.Editing) keyboard?.show() else keyboard?.hide()
        }
    }
}
