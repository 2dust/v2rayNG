package com.v2ray.ang.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Transient presentation only. Durable test results remain in MainUiState. */
internal class MainTestAnnouncements {
    var current by mutableStateOf<MainTestAnnouncement?>(null)
        private set
    private val pending = ArrayDeque<MainTestAnnouncement>()

    fun offer(event: MainTestAnnouncement?) {
        if (event == null) {
            clear()
        } else if (current == null) {
            current = event
        } else {
            pending.addLast(event)
        }
    }

    fun advance(id: Long) {
        if (current?.id == id) current = pending.removeFirstOrNull()
    }

    fun clear() {
        current = null
        pending.clear()
    }
}
