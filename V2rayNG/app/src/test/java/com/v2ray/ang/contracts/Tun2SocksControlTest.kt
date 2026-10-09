package com.v2ray.ang.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Tun2SocksControlTest {

    private class Tunnel(private val starts: Boolean, private val runs: Boolean) : Tun2SocksControl {
        val calls = mutableListOf<String>()

        override fun startTun2Socks(): Boolean = starts.also { calls.add("start") }
        override fun stopTun2Socks() = Unit.also { calls.add("stop") }
        override fun isTun2SocksRunning(): Boolean = runs.also { calls.add("look") }
    }

    @Test
    fun aStartGoesOnWhenItsTunnelStartedAndStillRuns() {
        val tunnel = Tunnel(starts = true, runs = true)
        assertTrue(Tun2SocksControl.startMayGoOn(tunnel) { it.startTun2Socks() })
        assertTrue(Tun2SocksControl.startMayGoOn(tunnel) { it.isTun2SocksRunning() })
        // One start and one look: nothing keeps looking afterwards.
        assertEquals(listOf("start", "look"), tunnel.calls)
    }

    @Test
    fun aTunnelThatCouldNotBeStartedEndsTheStart() {
        assertFalse(Tun2SocksControl.startMayGoOn(Tunnel(starts = false, runs = false)) { it.startTun2Socks() })
    }

    @Test
    fun aTunnelThatGaveUpWhileSettingItselfUpEndsTheStart() {
        // The start call itself succeeded; the native side ended on its own afterwards.
        assertFalse(Tun2SocksControl.startMayGoOn(Tunnel(starts = true, runs = false)) { it.isTun2SocksRunning() })
    }

    @Test
    fun withoutATunnelThereIsNothingToWaitFor() {
        // The mode where Xray reads the interface itself has no tun2socks at all.
        assertTrue(Tun2SocksControl.startMayGoOn(null) { it.startTun2Socks() })
        assertTrue(Tun2SocksControl.startMayGoOn(null) { it.isTun2SocksRunning() })
    }
}
