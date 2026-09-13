package com.v2ray.ang.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TetheringCoreSyncTest {

    @Test
    fun coreSyncHookNeverLeaksFeatureFailuresIntoThePrimaryCore() {
        var failure: Throwable? = null
        val expected = IllegalStateException("Shizuku unavailable")

        assertFalse(runCoreSyncHook({ throw expected }) { failure = it })
        assertSame(expected, failure)
        assertTrue(runCoreSyncHook({}, {}))
        assertFalse(runCoreSyncHook({ throw expected }) { throw IllegalStateException("logger failed") })
    }

    @Test
    fun binderDeathRecoversOnlyAStillRunningCore() {
        val stopped = TetheringRecoveryState().onBinderDied().onBinderReceived()
        assertEquals(TetheringRecoveryAction.NONE, stopped.action)

        val running = TetheringRecoveryState().onCoreStarted().onBinderDied()
        assertTrue(running.recoverWhenShizukuReturns)
        val recovered = running.onBinderReceived()
        assertEquals(TetheringRecoveryAction.RECOVER, recovered.action)
        assertFalse(recovered.state.recoverWhenShizukuReturns)
        assertEquals(TetheringRecoveryAction.NONE, recovered.state.onBinderReceived().action)

        assertEquals(TetheringRecoveryState(), running.onCoreStopped())
    }

    @Test
    fun foregroundRecoveryIsApiBoundedAndCoalescedUntilItCompletes() {
        val waiting = TetheringRecoveryState().onCoreStarted().onBinderDied()
        assertEquals(TetheringRecoveryAction.NONE, waiting.onAppForegrounded(33).action)

        val requested = waiting.onAppForegrounded(34)
        assertEquals(TetheringRecoveryAction.REQUEST_BINDER, requested.action)
        assertTrue(requested.state.foregroundRequestPending)
        assertEquals(TetheringRecoveryAction.NONE, requested.state.onAppForegrounded(37).action)

        val retry = requested.state.onForegroundRequestFailed().onAppForegrounded(37)
        assertEquals(TetheringRecoveryAction.REQUEST_BINDER, retry.action)

        val received = retry.state.onBinderReceived()
        assertEquals(TetheringRecoveryAction.RECOVER, received.action)
        assertFalse(received.state.foregroundRequestPending)
    }
}
