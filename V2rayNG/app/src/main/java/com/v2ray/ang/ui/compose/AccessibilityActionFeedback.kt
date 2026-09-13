package com.v2ray.ang.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay

/** Window-owned status for custom actions; repeated messages remain distinct events. */
internal class AccessibilityActionFeedbackState {
    private var nextId = 0L
    private var resumed = false
    private val pending = ArrayDeque<Pair<Long, String>>()
    var message by mutableStateOf<Pair<Long, String>?>(null)
        private set
    var publishedId by mutableStateOf<Long?>(null)
        private set

    fun resume() {
        resumed = true
    }

    fun pause() {
        resumed = false
        pending.clear()
        message = null
        publishedId = null
    }

    fun show(text: String) {
        if (!resumed || text.isBlank()) return
        val next = ++nextId to text
        if (message == null) message = next else pending.addLast(next)
    }

    fun published(id: Long) {
        if (message?.first == id) publishedId = id
    }

    fun finish(id: Long) {
        if (message?.first != id || publishedId != id) return
        publishedId = null
        message = pending.removeFirstOrNull()
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
    LifecycleResumeEffect(state) {
        state.resume()
        onPauseOrDispose { state.pause() }
    }
    val message = state.message
    LaunchedEffect(state, state.publishedId) {
        val id = state.publishedId ?: return@LaunchedEffect
        // Hold each published update briefly before advancing. This is not a speech timer;
        // accessibility services own speech scheduling. Pausing discards obsolete feedback.
        delay(1000L)
        state.finish(id)
    }
    AccessibilityLiveRegionText(
        eventId = message?.first,
        text = message?.second.orEmpty(),
        mode = LiveRegionMode.Polite,
        onPublished = state::published,
    )
}
