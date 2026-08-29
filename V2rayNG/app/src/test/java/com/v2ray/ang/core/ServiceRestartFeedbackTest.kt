package com.v2ray.ang.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceRestartFeedbackTest {

    @Test
    fun completionReportsRestartExactlyOnce() {
        val feedback = ServiceRestartFeedback()

        feedback.begin()

        assertTrue(feedback.isRestarting)
        assertTrue(feedback.complete())
        assertFalse(feedback.isRestarting)
        assertFalse(feedback.complete())
    }

    @Test
    fun cancellationClearsRestartFeedback() {
        val feedback = ServiceRestartFeedback()
        feedback.begin()

        feedback.cancel()

        assertFalse(feedback.isRestarting)
        assertFalse(feedback.complete())
    }
}
