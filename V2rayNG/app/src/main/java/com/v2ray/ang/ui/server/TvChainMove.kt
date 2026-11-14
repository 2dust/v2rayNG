package com.v2ray.ang.ui.server

import android.view.ViewConfiguration
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class TvChainMove(private val quietMillis: Long) {
    var moving by mutableStateOf(false)
        private set
    private var pressed = false
    private var released = false
    private var dropAfter = 0L
    private var dropping = false

    fun down(time: Long, repeated: Boolean) {
        if (dropping) return
        if (moving) {
            if (released && !repeated && time > dropAfter) {
                drop()
                dropping = true
            } else {
                dropAfter = time + quietMillis
            }
        } else {
            pressed = true
            if (repeated) start()
        }
    }

    fun up(time: Long, canceled: Boolean): Boolean {
        if (dropping) {
            dropping = false
            return false
        }
        if (moving) {
            released = true
            dropAfter = time + quietMillis
        }
        return (pressed && !moving && !canceled).also { pressed = false }
    }

    fun start() {
        moving = true
        released = false
        pressed = false
    }

    fun drop() {
        moving = false
        pressed = false
        dropping = false
    }

    fun awaitingDropRelease(): Boolean = dropping
}

@Composable
internal fun Modifier.tvChainMove(state: TvChainMove, onMove: (Int) -> Unit, onClick: () -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    val hold = remember(state) { arrayOfNulls<Job>(1) }
    DisposableEffect(state) {
        onDispose { hold[0]?.cancel() }
    }
    return onFocusChanged {
        if (!it.isFocused) {
            hold[0]?.cancel()
            hold[0] = null
            state.drop()
        }
    }.onPreviewKeyEvent { event ->
        val native = event.nativeKeyEvent
        val down = event.type == KeyEventType.KeyDown
        when (event.key) {
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.Spacebar -> {
                if (down) {
                    val wasMoving = state.moving
                    state.down(native.eventTime, native.repeatCount > 0 || native.isLongPress)
                    if (!wasMoving && !state.moving && !state.awaitingDropRelease() && hold[0] == null) {
                        hold[0] = scope.launch {
                            delay(ViewConfiguration.getLongPressTimeout().toLong())
                            state.start()
                        }
                    }
                } else {
                    hold[0]?.cancel()
                    hold[0] = null
                    if (state.up(native.eventTime, native.isCanceled)) onClick()
                }
                true
            }
            Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> {
                if (!state.moving) return@onPreviewKeyEvent false
                if (down && event.key == Key.DirectionUp) onMove(-1)
                if (down && event.key == Key.DirectionDown) onMove(1)
                true
            }
            else -> false
        }
    }.semantics { onLongClick { state.start(); true } }
        .clickable(role = Role.Button) { if (state.moving) state.drop() else onClick() }
}

// Some remotes and keyboard bridges emit repeated down/up pairs with repeatCount=0.
// Preserve the quiet period after release until those inputs report a real held key.
internal fun tvChainQuietMillis(keyRepeatDelay: Int): Long = maxOf(500L, keyRepeatDelay.toLong() * 2)
