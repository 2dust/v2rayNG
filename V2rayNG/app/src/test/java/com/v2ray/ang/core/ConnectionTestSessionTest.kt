package com.v2ray.ang.core

import com.v2ray.ang.dto.ConnectionTestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionTestSessionTest {
    private val result = ConnectionTestResult(42, country = "DE")

    @Test
    fun stoppedServiceCannotBeginMeasurement() {
        assertNull(ConnectionTestSession().beginTest())
    }

    @Test
    fun testTransitionsFromPendingToSuccessOrError() {
        val session = ConnectionTestSession()
        session.started("a")
        val first = session.beginTest()!!
        assertTrue(session.state.value.isTesting)
        assertNull(session.state.value.result)
        assertTrue(session.complete(first, result))
        assertFalse(session.state.value.isTesting)
        assertEquals(result, session.state.value.result)

        val second = session.beginTest()!!
        assertNull(session.state.value.result)
        val failure = ConnectionTestResult(-1, errorMessage = "timeout")
        assertTrue(session.complete(second, failure))
        assertEquals(failure, session.state.value.result)
    }

    @Test
    fun lateResultFromReplacedProfileIsRejected() {
        val session = ConnectionTestSession()
        session.started("a")
        val request = session.beginTest()!!
        session.stopped()
        session.started("b")
        assertFalse(session.complete(request, result))
        assertEquals(CoreConnectionState("b"), session.state.value)
    }

    @Test
    fun lateResultFromSameProfileBeforeRestartIsRejected() {
        val session = ConnectionTestSession()
        session.started("a")
        val request = session.beginTest()!!
        session.stopped()
        session.started("a")
        assertFalse(session.complete(request, result))
        assertNull(session.state.value.result)
    }

    @Test
    fun newestRequestWinsEvenWhenOlderTestCompletesLast() {
        val session = ConnectionTestSession()
        session.started("a")
        val older = session.beginTest()!!
        val newer = session.beginTest()!!
        assertTrue(session.complete(newer, result))
        assertFalse(session.complete(older, ConnectionTestResult(999)))
        assertEquals(result, session.state.value.result)
    }

    @Test
    fun stopClearsResultsAndRejectsPendingCompletion() {
        val session = ConnectionTestSession()
        session.started("a")
        val request = session.beginTest()!!
        session.stopped()
        session.stopped()
        assertFalse(session.complete(request, result))
        assertEquals(CoreConnectionState(), session.state.value)
    }

    @Test
    fun completedRequestCannotReplaceItsResult() {
        val session = ConnectionTestSession()
        session.started("a")
        val request = session.beginTest()!!
        assertTrue(session.complete(request, result))
        assertFalse(session.complete(request, ConnectionTestResult(999)))
        assertEquals(result, session.state.value.result)
    }
}
