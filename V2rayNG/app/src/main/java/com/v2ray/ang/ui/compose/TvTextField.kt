package com.v2ray.ang.ui.compose

import android.content.res.Configuration
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

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

/**
 * Owns the handoff between the passive TV search focus target and the IME editor.
 */
internal class TvTextFieldState(val passiveFocusRequester: FocusRequester, initiallyEditing: Boolean, private val hideKeyboard: () -> Unit) {
    val editorFocusRequester = FocusRequester()
    val interactionSource = MutableInteractionSource()
    val bringIntoViewRequester = BringIntoViewRequester()

    var isEditing by mutableStateOf(initiallyEditing)
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
        restorePassiveFocus = restoreFocus
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

// A TV IME can finish composing before onPause, after the application has the new configuration
// but while the old window still has focus. Neither its cursor nor its keyboard state is user input.
@Composable
internal fun <T> RecordInteractiveEditorState(value: T, saved: MutableState<T>) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val resources = LocalContext.current.applicationContext.resources
    val windowConfig = remember(LocalConfiguration.current) { Configuration(resources.configuration) }
    SideEffect {
        if (lifecycle.currentState == Lifecycle.State.RESUMED && windowConfig == resources.configuration) saved.value = value
    }
}

/**
 * Isolates [ExperimentalLayoutApi] for WindowInsets.isImeVisible so Back from the TV IME returns
 * focus to the passive field. Reevaluate the opt-in when isImeVisible becomes stable.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun rememberTvTextFieldState(passiveFocusRequester: FocusRequester, initiallyEditing: Boolean = false): TvTextFieldState {
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeBottomClearancePx = with(density) { TvImeBottomClearance.toPx() }
    val currentKeyboardController = rememberUpdatedState(keyboardController)
    val resumeEditing = rememberSaveable { mutableStateOf(initiallyEditing) }
    val state = remember(passiveFocusRequester) {
        TvTextFieldState(
            passiveFocusRequester = passiveFocusRequester,
            initiallyEditing = resumeEditing.value,
            hideKeyboard = { currentKeyboardController.value?.hide() }
        )
    }
    RecordInteractiveEditorState(state.isEditing, resumeEditing)
    if (state.isEditing) {
        val isImeVisible = WindowInsets.isImeVisible

        /*
         * AOSP TV's overlay IME can report visibility before the layout settles. Wait before using
         * the editor bounds so bring-into-view does not oscillate. Remove this delay when supported
         * TV IMEs report final bounds in the visibility frame.
         */
        LaunchedEffect(state.editorHasFocus, isImeVisible, state.measuredSize) {
            when (tvTextFieldRecoveryAction(state.isEditing, state.editorHasFocus, state.imeWasVisible, isImeVisible)) {
                TvTextFieldRecoveryAction.FinishEditing -> state.finishEditing(restoreFocus = true)
                TvTextFieldRecoveryAction.MaintainEditor -> {
                    if (isImeVisible) {
                        state.imeWasVisible = true
                        repeat(TvImeLayoutSettlingFrames) { withFrameNanos { } }
                        state.bringEditorAboveIme(imeBottomClearancePx)
                    } else keyboardController?.show()
                }
                TvTextFieldRecoveryAction.RequestEditorFocus -> state.editorFocusRequester.requestFocus()
                TvTextFieldRecoveryAction.None -> Unit
            }
        }
    } else if (state.restorePassiveFocus) {
        LaunchedEffect(state) {
            state.passiveFocusRequester.requestFocus()
            state.restorePassiveFocus = false
        }
    }
    return state
}

@Composable
internal fun Modifier.tvSearchFieldFocus(
    state: TvTextFieldState,
    focusOrder: List<FocusRequester>,
    onMoveDown: () -> Boolean
): Modifier {
    return bringIntoViewRequester(state.bringIntoViewRequester)
        .onSizeChanged { state.measuredSize = it }
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                event.key.isDpadActivationKey() && !state.isEditing -> {
                    state.beginEditing()
                    true
                }
                event.key == Key.DirectionUp || event.key == Key.DirectionDown -> {
                    state.finishEditing()
                    event.key == Key.DirectionUp || onMoveDown()
                }
                else -> false
            }
        }
        .dpadOrderedFocusNavigation(state.passiveFocusRequester, focusOrder)
        .then(if (state.isEditing) Modifier else Modifier.focusRequester(state.passiveFocusRequester))
        .focusable(enabled = !state.isEditing, interactionSource = state.interactionSource)
}

internal fun Modifier.tvTextFieldEditorFocus(state: TvTextFieldState): Modifier {
    return focusRequester(state.editorFocusRequester)
        .focusProperties { canFocus = state.isEditing }
        .onFocusChanged { state.onEditorFocusChanged(it.isFocused) }
}
