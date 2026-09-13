package com.v2ray.ang.core

import java.util.concurrent.atomic.AtomicReference

/** Feedback intent, not a replacement for the daemon's native running state. */
internal class ServiceFeedbackState {
    private enum class State { NONE, STARTED, STOP_REQUESTED }

    private val state = AtomicReference(State.NONE)

    fun started() {
        state.set(State.STARTED)
    }

    fun startFailed() {
        state.set(State.NONE)
    }

    fun requestStop() {
        state.compareAndSet(State.STARTED, State.STOP_REQUESTED)
    }

    // Internal reload shutdowns and failed-start cleanup are not successful user-facing stops.
    fun stopped(): Boolean = state.compareAndSet(State.STOP_REQUESTED, State.NONE)
}
