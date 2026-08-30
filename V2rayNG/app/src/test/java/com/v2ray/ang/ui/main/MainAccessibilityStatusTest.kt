package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.ConnectionTestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MainAccessibilityStatusTest {

    @Test
    fun connectionAccessibilityStatusIgnoresDisplayedTestProgress() {
        assertSame(MainStatus.Connected, accessibilityConnectionStatus(isRunning = true))
        assertSame(MainStatus.Disconnected, accessibilityConnectionStatus(isRunning = false))
    }

    @Test
    fun testingStateStartsAndFinishesWithoutExposingProgressAsConnectionState() {
        val started = MainUiState(isRunning = true).withTestingStarted()
        assertTrue(started.isTesting)
        assertSame(MainStatus.Testing, started.status)

        val completed = started.withTestingFinished(completedBulkTest = true)
        assertFalse(completed.isTesting)
        assertSame(MainStatus.TestCompleted, completed.status)

        val cancelled = started.withTestingFinished(completedBulkTest = false)
        assertFalse(cancelled.isTesting)
        assertSame(MainStatus.Connected, cancelled.status)

        val disconnected = MainUiState(isRunning = false, isTesting = true)
            .withTestingFinished(completedBulkTest = false)
        assertSame(MainStatus.Disconnected, disconnected.status)
    }

    @Test
    fun currentTestPreservesSuccessAndFailureAsTerminalResults() {
        val success = ConnectionTestResult(delayMillis = 20L)
        val successState = MainUiState(isTesting = true).withCurrentTestResult(success)
        assertFalse(successState.isTesting)
        assertEquals(success, (successState.status as MainStatus.ConnectionTest).result)

        val failure = ConnectionTestResult(delayMillis = -1L, errorMessage = "failure")
        val failureState = MainUiState(isTesting = true).withCurrentTestResult(failure)
        assertFalse(failureState.isTesting)
        assertEquals(failure, (failureState.status as MainStatus.ConnectionTest).result)
    }

    @Test
    fun onlyTestStartAndTerminalResultsAreAnnouncements() {
        assertTrue(MainStatus.Testing.isTestAnnouncement())
        assertTrue(MainStatus.TestCompleted.isTestAnnouncement())
        assertTrue(
            MainStatus.ConnectionTest(ConnectionTestResult(delayMillis = 20L))
                .isTestAnnouncement()
        )

        assertFalse(MainStatus.TestProgress("1 / 10").isTestAnnouncement())
        assertFalse(MainStatus.Connected.isTestAnnouncement())
        assertFalse(MainStatus.Disconnected.isTestAnnouncement())
    }
}
