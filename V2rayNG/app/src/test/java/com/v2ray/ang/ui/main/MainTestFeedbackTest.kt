package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.ConnectionTestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun onlyTheLatestCurrentRequestCanFinishOnce() {
        val requests = MainTestRequests()
        assertFalse(requests.isTesting)
        assertFalse(requests.completeCurrent(""))
        val first = requests.beginCurrent()
        val second = requests.beginCurrent()
        assertNotEquals(first, second)
        assertFalse(requests.completeCurrent(first))
        assertTrue(requests.isTesting)
        assertTrue(requests.completeCurrent(second))
        assertFalse(requests.completeCurrent(second))
        assertFalse(requests.isTesting)
    }

    @Test
    fun stopAndRestartInvalidateThePreviousConnectionRequest() {
        val requests = MainTestRequests()
        val stopped = requests.beginCurrent()
        requests.invalidateCurrent()
        assertFalse(requests.completeCurrent(stopped))
        val restarted = requests.beginCurrent()
        assertFalse(requests.completeCurrent(stopped))
        assertTrue(requests.completeCurrent(restarted))
    }

    @Test
    fun bulkCancellationAndReplacementRejectLateCompletionEvenInTheSameGroup() {
        val requests = MainTestRequests()
        val cancelled = requests.beginBulk("group")
        requests.cancelBulk()
        assertNull(requests.completeBulk(cancelled.id))
        val replaced = requests.beginBulk("group")
        val current = requests.beginBulk("group")
        assertNull(requests.completeBulk(replaced.id))
        assertEquals(current, requests.bulk)
        assertEquals(current, requests.completeBulk(current.id))
        assertNull(requests.completeBulk(current.id))
        assertFalse(requests.isTesting)
    }

    @Test
    fun currentResultDoesNotFinishAnIndependentBulkTest() {
        val requests = MainTestRequests()
        val bulk = requests.beginBulk("") // All servers is a valid group ID.
        val current = requests.beginCurrent()
        assertTrue(requests.completeCurrent(current))
        assertTrue(requests.isTesting)
        assertEquals(bulk, requests.completeBulk(bulk.id))
        assertFalse(requests.isTesting)
    }
}
