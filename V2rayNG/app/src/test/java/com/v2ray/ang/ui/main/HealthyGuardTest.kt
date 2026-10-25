package com.v2ray.ang.ui.main

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests of the connection guard on a virtual clock: hours of behaviour run in milliseconds.
 * They check the logic only (no loops, no overlapping tests, backoff, cooldown, no internet).
 * Real VPN and network behaviour still has to be checked on the phone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HealthyGuardTest {

    private class FakePorts(private val scope: TestScope) : GuardPorts {
        lateinit var guard: HealthyGuard

        override val now: Long get() = scope.currentTime
        override var isRunning: Boolean = true
        override var refreshActive: Boolean = false
        override var hasBulkTest: Boolean = false

        var internet = true
        var downBytes = 0L

        // answer of the n-th test (n starts at 0); a value outside 1..4000 is a bad sample
        var probeAnswer: (Int) -> Long = { 150L }
        var probeLatencyMs = 200L
        var silentProbe = false
        var selectionInfo: GuardSelection? = GuardSelection("A", 150L, false)
        var backupProvider: (Int) -> GuardSwitch? = { n -> GuardSwitch("B$n", "B$n", "A", "A") }
        var restartOnConnect = false
        var refreshAccepts = true
        var refreshFindsBackup = false

        var probes = 0
        var cancelledProbes = 0
        var picks = 0
        val switches = mutableListOf<Pair<Long, String>>()
        val suspects = mutableListOf<String>()
        val refreshStarts = mutableListOf<Long>()
        val logs = mutableListOf<String>()
        val says = mutableListOf<String>()

        override fun hasInternet(): Boolean = internet
        override fun recentDownBytes(): Long = downBytes

        override fun log(message: String) {
            logs.add(message)
        }

        override fun say(message: String) {
            says.add(message)
            logs.add(message)
        }

        override fun startProbe() {
            val index = probes++
            if (silentProbe) return
            val answer = probeAnswer(index)
            scope.backgroundScope.launch {
                delay(probeLatencyMs)
                guard.onProbeResult(answer)
            }
        }

        override fun cancelProbe() {
            cancelledProbes++
        }

        override suspend fun selection(): GuardSelection? = selectionInfo

        override suspend fun pickBackup(): GuardSwitch? = backupProvider(picks++)

        override fun markSuspect(guid: String) {
            suspects.add(guid)
        }

        override fun connectTo(guid: String) {
            switches.add(now to guid)
            if (restartOnConnect) {
                // the VPN restarts: off, then on again
                scope.backgroundScope.launch {
                    delay(1_000)
                    guard.onRunning(false)
                    delay(1_500)
                    guard.onRunning(true)
                }
            }
        }

        override fun startAutoRefresh(): Boolean {
            if (!refreshAccepts) return false
            refreshStarts.add(now)
            scope.backgroundScope.launch {
                delay(30_000)
                guard.onRefreshFinished(refreshFindsBackup)
            }
            return true
        }
    }

    private fun TestScope.newGuard(configure: FakePorts.() -> Unit = {}): Pair<FakePorts, HealthyGuard> {
        val ports = FakePorts(this)
        ports.configure()
        val guard = HealthyGuard(backgroundScope, ports)
        ports.guard = guard
        return ports to guard
    }

    private fun TestScope.advance(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun quietHealthyServerIsTestedOnlyEveryFiveMinutes() = runTest {
        val (p, g) = newGuard()
        g.onRunning(true)
        advance(600_000)
        // the check 5 s after connecting, then one idle test
        assertEquals(2, p.probes)
        assertTrue(p.switches.isEmpty())
    }

    @Test
    fun busyConnectionIsNotTestedByTheIdleTimer() = runTest {
        val (p, g) = newGuard { downBytes = 1_000_000L }
        g.onRunning(true)
        advance(900_000)
        // only the first check after connecting; the idle timer sees traffic and stays away
        assertEquals(1, p.probes)
    }

    @Test
    fun confirmedBadSampleSwitchesToBackup() = runTest {
        val (p, g) = newGuard { probeAnswer = { -1L } }
        g.onRunning(true)
        advance(30_000)
        assertEquals(2, p.probes)
        assertEquals(1, p.switches.size)
        assertEquals("B0", p.switches[0].second)
        assertTrue(p.switches[0].first in 8_000L..9_000L)
        assertEquals(listOf("A"), p.suspects)
    }

    @Test
    fun badSampleThatPassesTheSecondTestStaysAndClearsTrouble() = runTest {
        val (p, g) = newGuard { probeAnswer = { i -> if (i == 0) -1L else 150L } }
        g.onRunning(true)
        advance(240_000)
        assertEquals(2, p.probes)
        assertTrue(p.switches.isEmpty())
        assertFalse(g.isTroubled)
    }

    @Test
    fun slowAnswerCountsAsBad() = runTest {
        val (p, g) = newGuard { probeAnswer = { 4_500L } }
        g.onRunning(true)
        advance(30_000)
        assertEquals(1, p.switches.size)
    }

    @Test
    fun deadSelectedServerSwitchesWithinThirtySeconds() = runTest {
        val (p, g) = newGuard {
            selectionInfo = GuardSelection("A", -1L, true)
            probeAnswer = { -1L }
        }
        g.onRunning(true)
        advance(30_000)
        assertEquals(1, p.probes) // one bad sample is enough
        assertEquals(1, p.switches.size)
        assertTrue(p.switches[0].first <= 6_000L)
    }

    @Test
    fun deadSelectedServerIgnoresTheCooldown() = runTest {
        val (p, g) = newGuard {
            probeAnswer = { -1L }
            restartOnConnect = true
        }
        g.onRunning(true)
        advance(9_000)
        assertEquals(1, p.switches.size)
        // the user now picks a dead (red) server by hand
        p.selectionInfo = GuardSelection("B0", -1L, true)
        advance(60_000)
        assertEquals(2, p.switches.size)
        assertTrue(p.switches[1].first - p.switches[0].first < 20_000L)
    }

    @Test
    fun cooldownWaitsOnceInsteadOfTestingAgainAndAgain() = runTest {
        val (p, g) = newGuard {
            probeAnswer = { -1L }
            restartOnConnect = true
        }
        g.onRunning(true)
        advance(100_000)
        assertEquals(2, p.switches.size)
        assertTrue(p.switches[1].first - p.switches[0].first >= 60_000L)
        // the old loop tested every 10-20 s during the cooldown; now only a few tests happen
        assertTrue(p.probes <= 10, "too many tests: ${p.probes}")
    }

    @Test
    fun noBackupStartsAnAutomaticRefreshWithGrowingDelay() = runTest {
        val (p, g) = newGuard {
            probeAnswer = { -1L }
            backupProvider = { null }
        }
        g.onRunning(true)
        advance(100 * 60_000L)
        // refreshes after about 0, 20 and 60 minutes (10 -> 20 -> 40 -> 60 min delays)
        assertEquals(3, p.refreshStarts.size)
        assertTrue(p.probes <= 14, "too many tests: ${p.probes}")
        assertTrue(p.switches.isEmpty())
    }

    @Test
    fun refreshThatFindsBackupResetsTheDelayAndSwitches() = runTest {
        val (p, g) = newGuard {
            probeAnswer = { -1L }
            refreshFindsBackup = true
            backupProvider = { n -> if (n == 0) null else GuardSwitch("B", "B", "A", "A") }
        }
        g.onRunning(true)
        advance(120_000)
        assertEquals(1, p.refreshStarts.size)
        assertEquals(1, p.switches.size)
        assertEquals("B", p.switches[0].second)
    }

    @Test
    fun noInternetMeansNoTestsAndNoSwitch() = runTest {
        val (p, g) = newGuard { internet = false }
        g.onRunning(true)
        advance(600_000)
        assertEquals(0, p.probes)
        assertTrue(p.switches.isEmpty())
        assertEquals(1, p.says.count { it.contains("No internet") })
        // the network comes back
        p.internet = true
        g.onNetworkChanged()
        advance(10_000)
        assertTrue(p.probes >= 1)
        assertFalse(g.isTroubled)
    }

    @Test
    fun severalTriggersAtTheSameTimeMakeOneTest() = runTest {
        val (p, g) = newGuard()
        g.onRunning(true)
        g.onNetworkChanged()
        g.onNetworkChanged()
        g.onNetworkChanged()
        g.onStall()
        advance(20_000)
        assertEquals(1, p.probes)
    }

    @Test
    fun testsWaitWhileAnotherBulkTestRuns() = runTest {
        val (p, g) = newGuard { hasBulkTest = true }
        g.onRunning(true)
        advance(120_000)
        assertEquals(0, p.probes)
        p.hasBulkTest = false
        advance(10_000)
        assertTrue(p.probes >= 1)
    }

    @Test
    fun testsWaitWhileARefreshRuns() = runTest {
        val (p, g) = newGuard { refreshActive = true }
        g.onRunning(true)
        advance(60_000)
        assertEquals(0, p.probes)
        p.refreshActive = false
        g.onRefreshFinished(true)
        advance(5_000)
        assertTrue(p.probes >= 1)
    }

    @Test
    fun testWithoutAnswerTimesOutAndCountsAsBad() = runTest {
        val (p, g) = newGuard { silentProbe = true }
        g.onRunning(true)
        advance(40_000)
        assertEquals(2, p.cancelledProbes)
        assertEquals(1, p.switches.size)
    }

    @Test
    fun trafficStallTriggersATestWhenTheConnectionWasQuiet() = runTest {
        val (p, g) = newGuard()
        g.onRunning(true)
        advance(60_000)
        assertEquals(1, p.probes)
        g.onStall()
        advance(1_000)
        assertEquals(2, p.probes)
    }

    @Test
    fun nothingHappensWhileTheVpnIsOff() = runTest {
        val (p, g) = newGuard { isRunning = false }
        g.onNetworkChanged()
        g.onStall()
        g.onProbeResult(-1L) // an answer nobody asked for is ignored
        advance(600_000)
        assertEquals(0, p.probes)
        assertTrue(p.switches.isEmpty())
    }
}
