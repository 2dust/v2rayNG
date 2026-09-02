package com.v2ray.ang.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.semantics.LiveRegionMode
import kotlinx.coroutines.delay

/** Window-owned status for custom actions; repeated messages remain distinct events. */
internal class AccessibilityActionFeedbackState {
    private var nextId = 0L
    var message by mutableStateOf<Pair<Long, String>?>(null)
        private set

    fun show(text: String) {
        message = ++nextId to text
    }

    fun clear(id: Long) {
        if (message?.first == id) message = null
    }
}

internal val LocalAccessibilityActionFeedback = staticCompositionLocalOf<AccessibilityActionFeedbackState> {
    error("Accessibility action feedback must be hosted by AppTheme")
}

@Composable
internal fun rememberAccessibilityActionFeedback(): (String) -> Unit {
    val state = LocalAccessibilityActionFeedback.current
    return remember(state) { state::show }
}

@Composable
internal fun AccessibilityActionFeedbackHost(state: AccessibilityActionFeedbackState) {
    val message = state.message
    LaunchedEffect(message?.first) {
        val id = message?.first ?: return@LaunchedEffect
        delay(1000L)
        state.clear(id)
    }
    AccessibilityLiveRegionText(
        eventId = message?.first,
        text = message?.second.orEmpty(),
        mode = LiveRegionMode.Polite,
    )
}
