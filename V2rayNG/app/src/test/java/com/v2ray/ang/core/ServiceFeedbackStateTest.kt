package com.v2ray.ang.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceFeedbackStateTest {
    @Test
    fun requestedStopIsAcknowledgedOnlyOnceAtNativeCompletion() {
        val state = ServiceFeedbackState()
        state.started()
        state.requestStop()
        state.requestStop()
        assertTrue(state.stopped())
        assertFalse(state.stopped())
    }

    @Test
    fun failedStartCleanupAndUnstartedStopsDoNotReplaceFailureFeedback() {
        val state = ServiceFeedbackState()
        state.requestStop()
        assertFalse(state.stopped())
        state.started()
        state.startFailed()
        state.requestStop()
        assertFalse(state.stopped())
    }

    @Test
    fun internalReloadShutdownIsSilentAndLaterUserStopIsStillReported() {
        val state = ServiceFeedbackState()
        state.started()
        assertFalse(state.stopped())
        state.started()
        state.requestStop()
        assertTrue(state.stopped())
        state.started()
        state.requestStop()
        assertTrue(state.stopped())
    }
}
