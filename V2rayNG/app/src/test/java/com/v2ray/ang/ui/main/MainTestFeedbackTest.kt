package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.ConnectionTestResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MainTestFeedbackTest {
    @Test
    fun fastResultWaitsBehindStartAndStalePublicationCannotAdvanceIt() {
        val messages = MainTestAnnouncements()
        val start = MainTestAnnouncement(1, MainStatus.Testing)
        val result = MainTestAnnouncement(2, MainStatus.ConnectionTest(ConnectionTestResult(20)))
        messages.offer(start)
        messages.offer(result)
        assertEquals(start, messages.current)
        messages.advance(start.id)
        assertEquals(result, messages.current)
        messages.advance(start.id)
        assertEquals(result, messages.current)
        messages.advance(result.id)
        assertNull(messages.current)
    }

    @Test
    fun identicalConsecutiveResultsRemainSeparateEvents() {
        val messages = MainTestAnnouncements()
        val status = MainStatus.ConnectionTest(ConnectionTestResult(-1, "failure"))
        messages.offer(MainTestAnnouncement(1, status))
        messages.offer(MainTestAnnouncement(2, status))
        messages.advance(1)
        assertEquals(MainTestAnnouncement(2, status), messages.current)
        messages.advance(2)
        assertNull(messages.current)
    }

    @Test
    fun cancellationOrLeavingTheScreenDropsCurrentAndPendingFeedback() {
        for (cancelWithEvent in listOf(false, true)) {
            val messages = MainTestAnnouncements()
            messages.offer(MainTestAnnouncement(1, MainStatus.Testing))
            messages.offer(MainTestAnnouncement(2, MainStatus.TestCompleted))
            if (cancelWithEvent) messages.offer(null) else messages.clear()
            messages.advance(1)
            assertNull(messages.current)
            val next = MainTestAnnouncement(3, MainStatus.Testing)
            messages.offer(next)
            messages.advance(2)
            assertEquals(next, messages.current)
        }
    }

    @Test
    fun statusLabelOmitsProgressInterimResultsAndResultsFromADisconnectedService() {
        val messages = MainTestAnnouncements()
        val finalResult = MainStatus.ConnectionTest(ConnectionTestResult(20))
        val interimResult = MainStatus.ConnectionTest(ConnectionTestResult(20, isFinal = false))
        val hiddenStatuses = listOf(
            MainStatus.Disconnected,
            MainStatus.Connected,
            MainStatus.Testing,
            MainStatus.TestProgress("1"),
            MainStatus.TestCompleted,
            interimResult,
        )
        hiddenStatuses.forEach { assertNull(messages.connectionResultForAccessibility(true, it)) }
        assertNull(messages.connectionResultForAccessibility(false, finalResult))
    }

    @Test
    fun statusLabelRetainsFinalSuccessOrFailureAfterPublicationAndOnRevisit() {
        for (result in listOf(ConnectionTestResult(20), ConnectionTestResult(-1, "failure"))) {
            val status = MainStatus.ConnectionTest(result)
            val messages = MainTestAnnouncements()
            messages.offer(MainTestAnnouncement(1, MainStatus.Testing))
            messages.offer(MainTestAnnouncement(2, status))
            assertNull(messages.connectionResultForAccessibility(true, status))
            messages.advance(1)
            assertNull(messages.connectionResultForAccessibility(true, status))
            messages.advance(2)
            assertEquals(status, messages.connectionResultForAccessibility(true, status))

            val revisited = MainTestAnnouncements()
            assertNull(revisited.current)
            assertEquals(status, revisited.connectionResultForAccessibility(true, status))
        }
    }
}
