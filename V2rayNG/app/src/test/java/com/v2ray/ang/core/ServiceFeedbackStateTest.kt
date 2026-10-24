package com.v2ray.ang.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServiceFeedbackStateTest {
    @Test
    fun explicitRestartShutdownIsSilentButLaterStopIsReported() {
        val state = ServiceFeedbackState()
        state.started()
        state.restarting()
        repeat(2) {
            state.requestStop()
            assertFalse(state.stopped())
        }
        state.started()
        state.requestStop()
        assertTrue(state.stopped())
        assertFalse(state.stopped())
    }

    @Test
    fun restartDoesNotEraseAnAlreadyRequestedStop() {
        val state = ServiceFeedbackState()
        state.started()
        state.requestStop()
        state.restarting()
        assertTrue(state.stopped())
    }

    @Test
    fun failedRestartDoesNotProduceSuccessfulStopFeedback() {
        val state = ServiceFeedbackState()
        state.started()
        state.restarting()
        assertFalse(state.stopped())
        state.startFailed()
        state.requestStop()
        assertFalse(state.stopped())
    }

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

    @Test
    fun successfulReloadPreservesAStopRequestedBeforeItFinished() {
        val state = ServiceFeedbackState()
        state.started()
        assertFalse(state.stopped()) // Internal shutdown before starting the replacement core.
        state.requestStop()
        state.started()
        assertTrue(state.stopped())
        assertFalse(state.stopped())
    }

    @Test
    fun successfulReloadAfterFailureRearmsLaterStopFeedback() {
        val state = ServiceFeedbackState()
        state.started()
        state.startFailed()
        assertFalse(state.stopped())
        state.started()
        state.requestStop()
        assertTrue(state.stopped())
    }
}
