package com.v2ray.ang.ui.compose

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField as MaterialOutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

private val TvImeBottomClearance = 32.dp
private const val TvImeLayoutSettlingFrames = 3

internal enum class TvTextFieldRecoveryAction { None, FinishEditing, MaintainEditor, RequestEditorFocus }

internal fun tvTextFieldRecoveryAction(
    isEditing: Boolean,
    editorHasFocus: Boolean,
    imeWasVisible: Boolean,
    isImeVisible: Boolean
): TvTextFieldRecoveryAction = when {
    isEditing && editorHasFocus && imeWasVisible && !isImeVisible -> TvTextFieldRecoveryAction.FinishEditing
    isEditing && editorHasFocus -> TvTextFieldRecoveryAction.MaintainEditor
    isEditing -> TvTextFieldRecoveryAction.RequestEditorFocus
    else -> TvTextFieldRecoveryAction.None
}

data class TvTextFieldNavigation(
    val focusRequester: FocusRequester? = null,
    val onMoveUp: (() -> Boolean)? = null,
    val onMoveDown: (() -> Boolean)? = null,
    val onMovePrevious: (() -> Boolean)? = null,
    val onMoveNext: (() -> Boolean)? = null
)

internal data class OutlinedTextFieldSpec(
    val label: String,
    val placeholder: String? = null,
    val supportingText: String? = null,
    val enabled: Boolean = true,
    val isError: Boolean = false,
    val readOnly: Boolean = false,
    val singleLine: Boolean = false,
    val maxLines: Int = 5,
    val keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    val visualTransformation: VisualTransformation = VisualTransformation.None
)

/**
 * Owns the passive-row/editor handoff and IME positioning for one TV text field. Keeping that
 * behavior here prevents every form and dialog from implementing subtly different state machines.
 */
internal class TvTextFieldState(val passiveFocusRequester: FocusRequester, private val hideKeyboard: () -> Unit) {
    val editorFocusRequester = FocusRequester()
    val interactionSource = MutableInteractionSource()
    val bringIntoViewRequester = BringIntoViewRequester()

    var isEditing by mutableStateOf(false)
        private set
    internal var editorHasFocus by mutableStateOf(false)
    internal var imeWasVisible by mutableStateOf(false)
    internal var restorePassiveFocus by mutableStateOf(false)
    internal var measuredSize by mutableStateOf(IntSize.Zero)

    fun beginEditing() {
        editorHasFocus = false
        imeWasVisible = false
        restorePassiveFocus = false
        isEditing = true
    }

    fun finishEditing(restoreFocus: Boolean = false) {
        val wasEditing = isEditing || editorHasFocus
        isEditing = false
        editorHasFocus = false
        imeWasVisible = false
        restorePassiveFocus = restoreFocus && !passiveFocusRequester.requestFocus()
        if (wasEditing) hideKeyboard()
    }

    internal fun onEditorFocusChanged(isFocused: Boolean) {
        if (isFocused) {
            editorHasFocus = true
        } else if (editorHasFocus) {
            finishEditing()
        }
    }

    internal suspend fun bringEditorAboveIme(bottomClearancePx: Float) {
        val size = measuredSize
        val bounds = if (size == IntSize.Zero) {
            null
        } else {
            Rect(0f, 0f, size.width.toFloat(), size.height + bottomClearancePx)
        }
        bringIntoViewRequester.bringIntoView(bounds)
    }
}

/**
 * Isolates [ExperimentalLayoutApi] for WindowInsets.isImeVisible. Android TV overlay IMEs and
 * resizing multi-line editors can otherwise leave the field under the keyboard or oscillating by
 * one pixel. Reevaluate the opt-in when isImeVisible becomes stable; remove the frame settling
 * workaround when supported TV keyboards report final editor bounds in the visibility frame.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun rememberTvTextFieldState(passiveFocusRequester: FocusRequester? = null): TvTextFieldState {
    val defaultPassiveFocusRequester = remember { FocusRequester() }
    val resolvedPassiveFocusRequester = passiveFocusRequester ?: defaultPassiveFocusRequester
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeBottomClearancePx = with(density) { TvImeBottomClearance.toPx() }
    val currentKeyboardController = rememberUpdatedState(keyboardController)
    val state = remember(resolvedPassiveFocusRequester) {
        TvTextFieldState(
            passiveFocusRequester = resolvedPassiveFocusRequester,
            hideKeyboard = { currentKeyboardController.value?.hide() }
        )
    }
    val isImeVisible = WindowInsets.isImeVisible

    /*
     * Keep this recovery sequence centralized. On Android TV, both the overlay IME and a
     * multi-line field can change size for several frames after visibility is reported. Calling
     * BringIntoViewRequester immediately uses stale bounds, causing the rapid one-pixel jitter or
     * leaving the last line under the keyboard. Three frames is the shortest value verified on the
     * AOSP TV keyboard; it is a compatibility delay, not animation timing.
    */
    LaunchedEffect(state.isEditing, state.editorHasFocus, isImeVisible, state.measuredSize) {
        when (tvTextFieldRecoveryAction(state.isEditing, state.editorHasFocus, state.imeWasVisible, isImeVisible)) {
            TvTextFieldRecoveryAction.FinishEditing -> state.finishEditing(restoreFocus = true)
            TvTextFieldRecoveryAction.MaintainEditor -> {
                if (isImeVisible) {
                    state.imeWasVisible = true
                    repeat(TvImeLayoutSettlingFrames) { withFrameNanos { } }
                    state.bringEditorAboveIme(imeBottomClearancePx)
                }
                keyboardController?.show()
            }
            TvTextFieldRecoveryAction.RequestEditorFocus -> state.editorFocusRequester.requestFocus()
            TvTextFieldRecoveryAction.None -> Unit
        }
    }
    LaunchedEffect(state.isEditing, state.restorePassiveFocus) {
        if (!state.isEditing && state.restorePassiveFocus) {
            state.passiveFocusRequester.requestFocus()
            state.restorePassiveFocus = false
        }
    }
    return state
}

@Composable
internal fun Modifier.tvAwareTextFieldFocus(
    state: TvTextFieldState?,
    enabled: Boolean,
    navigation: TvTextFieldNavigation,
    onActivate: () -> Unit
): Modifier {
    if (state == null) return this
    val focusManager = LocalFocusManager.current
    return bringIntoViewRequester(state.bringIntoViewRequester)
        .onSizeChanged { state.measuredSize = it }
        .tvPassiveTextFieldFocus(
            focusRequester = state.passiveFocusRequester,
            interactionSource = state.interactionSource,
            enabled = enabled,
            onActivate = onActivate,
            canActivate = { !state.isEditing },
            onMoveUp = {
                state.finishEditing()
                navigation.onMoveUp?.invoke()
                    ?: focusManager.moveFocus(FocusDirection.Up)
            },
            onMoveDown = {
                state.finishEditing()
                navigation.onMoveDown?.invoke()
                    ?: focusManager.moveFocus(FocusDirection.Down)
            }
        )
        .then(
            if (navigation.onMovePrevious != null || navigation.onMoveNext != null) {
                Modifier.dpadLogicalHorizontalNavigation(
                    onMovePrevious = {
                        navigation.onMovePrevious?.invoke()
                            ?: state.passiveFocusRequester.requestFocus()
                    },
                    onMoveNext = {
                        navigation.onMoveNext?.invoke()
                            ?: state.passiveFocusRequester.requestFocus()
                    }
                )
            } else {
                Modifier
            }
        )
}

@Composable
internal fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    spec: OutlinedTextFieldSpec,
    tvFieldState: TvTextFieldState?,
    modifier: Modifier = Modifier,
    trailingIcon: (@Composable () -> Unit)? = null,
    interactionSource: MutableInteractionSource? = tvFieldState?.interactionSource
) {
    val isTelevision = isTelevisionDevice()
    MaterialOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(spec.label) },
        placeholder = spec.placeholder?.let { placeholder -> { Text(placeholder) } },
        supportingText = spec.supportingText?.let { text -> { Text(text) } },
        enabled = spec.enabled,
        isError = spec.isError,
        readOnly = spec.readOnly || (tvFieldState != null && !tvFieldState.isEditing),
        singleLine = spec.singleLine,
        maxLines = spec.maxLines,
        keyboardOptions = spec.keyboardOptions,
        visualTransformation = spec.visualTransformation,
        trailingIcon = trailingIcon,
        interactionSource = interactionSource,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedBorderColor = if (isTelevision) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.primary
            },
            focusedLabelColor = if (isTelevision) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.primary
            },
            cursorColor = MaterialTheme.colorScheme.secondary,
            selectionColors = TextSelectionColors(
                handleColor = MaterialTheme.colorScheme.secondary,
                backgroundColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
            )
        ),
        modifier = modifier
    )
}

@Composable
internal fun Modifier.tvPassiveTextFieldFocus(
    focusRequester: FocusRequester,
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    onActivate: () -> Unit,
    canActivate: () -> Boolean = { true },
    onMoveUp: (() -> Boolean)? = null,
    onMoveDown: (() -> Boolean)? = null
): Modifier {
    return onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key.isDpadActivationKey() && canActivate()) {
            onActivate()
            true
        } else {
            false
        }
    }
        .dpadTextFieldNavigation(onMoveUp = onMoveUp, onMoveDown = onMoveDown)
        .focusRequester(focusRequester)
        .focusable(enabled = enabled, interactionSource = interactionSource)
}

internal fun Modifier.tvTextFieldEditorFocus(state: TvTextFieldState): Modifier {
    return focusRequester(state.editorFocusRequester)
        .focusProperties { canFocus = state.isEditing }
        .onFocusChanged { state.onEditorFocusChanged(it.isFocused) }
}
