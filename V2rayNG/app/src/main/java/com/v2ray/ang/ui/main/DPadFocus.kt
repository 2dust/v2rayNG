package com.v2ray.ang.ui.main

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

class DPadFocusTargets {
    val fab = FocusRequester()
    val firstDrawerItem = FocusRequester()
    val menuButton = FocusRequester()
}

val LocalDPadFocusTargets = staticCompositionLocalOf<DPadFocusTargets?> { null }

internal fun isLastRowItem(index: Int, itemCount: Int, columns: Int): Boolean {
    if (itemCount <= 0 || columns <= 0 || index !in 0 until itemCount) return false
    val lastRowStart = itemCount - ((itemCount - 1) % columns + 1)
    return index >= lastRowStart
}

fun FocusRequester.tryRequestFocus(): Boolean =
    try {
        requestFocus()
        true
    } catch (_: IllegalStateException) {
        false
    }

fun Modifier.dPadDownTo(target: FocusRequester?): Modifier {
    if (target == null) return this
    return focusProperties { down = target }
        .onKeyEvent { event ->
            event.type == KeyEventType.KeyDown &&
                event.key == Key.DirectionDown &&
                target.tryRequestFocus()
        }
}
