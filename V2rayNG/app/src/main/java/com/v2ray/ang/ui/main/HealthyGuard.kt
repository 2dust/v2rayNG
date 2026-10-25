package com.v2ray.ang.ui.main

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/** What the guard needs to know about the selected server before it checks it. */
data class GuardSelection(val guid: String, val storedMs: Long, val manual: Boolean)

/** A backup server chosen for a switch. */
data class GuardSwitch(
    val bestGuid: String,
    val bestName: String,
    val oldGuid: String,
    val oldName: String,
)

/**
 * Everything the guard needs from the outside world. HealthyManager implements it with the real
 * app; the unit tests implement it with a fake and a virtual clock, so no Android code is needed.
 */
interface GuardPorts {
    val now: Long
    val isRunning: Boolean
    val refreshActive: Boolean
    val hasBulkTest: Boolean
    fun hasInternet(): Boolean
    fun recentDownBytes(): Long
    fun log(message: String)
    fun say(message: String)
    fun startProbe()
    fun cancelProbe()
    suspend fun selection(): GuardSelection?
    suspend fun pickBackup(): GuardSwitch?
    fun markSuspect(guid: String)
    fun connectTo(guid: String)

    /** Starts a Healthy refresh. Returns false when one is already running. */
    fun startAutoRefresh(): Boolean
}

data class GuardConfig(
    val maxDelayMs: Long = 4_000L,            // a test slower than this is "bad"
    val probeTimeoutMs: Long = 10_000L,       // no answer within this = bad
    val confirmDelayMs: Long = 3_000L,        // wait before the second test of a bad sample
    val cooldownMs: Long = 60_000L,           // minimum time between two switches
    val connectSettleMs: Long = 5_000L,       // first check this long after connecting
    val duplicateRunningMs: Long = 3_000L,    // a second "running" event this soon is the same connection
    val idleMs: Long = 300_000L,              // test every 5 minutes when there is no traffic
    val idleMinDownBytes: Long = 2_048L,      // enough download in the window = the connection is alive
    val idleBusyRecheckMs: Long = 30_000L,
    val stallCooldownMs: Long = 15_000L,
    val deferMs: Long = 5_000L,               // wait while another test or a refresh is running
    val errorRetryMs: Long = 30_000L,
    val autoRefreshBaseMs: Long = 600_000L,   // first automatic refresh interval: 10 minutes
    val autoRefreshMaxMs: Long = 3_600_000L,  // never wait longer than 1 hour
    val refreshWaitMaxMs: Long = 900_000L,    // safety net if a refresh never reports back
)

/**
 * The connection guard: ONE loop that decides when to test the current server and when to switch.
 *
 * Nobody else tests the current server. Every trigger (connected, network changed, traffic stalled,
 * idle timer, retry) only asks this loop for a check, so two checks can never overlap.
 *
 * When a switch is not possible (no internet, cooldown, no backup) the guard does NOT keep testing.
 * It goes to sleep until the one event that can change the situation:
 *   - no internet: the next network change
 *   - cooldown: the end of the cooldown
 *   - no backup: an automatic Healthy refresh, repeated with a growing delay (10 min .. 1 hour)
 *
 * All methods must be called from one thread (the main thread in the app, the test scheduler in tests).
 */
class HealthyGuard(
    private val scope: CoroutineScope,
    private val ports: GuardPorts,
    private val cfg: GuardConfig = GuardConfig(),
) {
    private companion object {
        const val NEVER = Long.MAX_VALUE
    }

    private var loopJob: Job? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)

    private var dueAt = NEVER          // when the next check is due
    private var idleAt = NEVER         // when the next idle check is due
    private var settleUntil = 0L       // no check before this (the tunnel is still coming up)
    private var connectedAt = 0L
    private var connectPending = false // the next check is the first one on a new connection
    private var skipCooldown = false   // the user picked this server, or it is marked dead
    private var blocked: String? = null
    private var checking = false
    private var lastSwitchAt = -1L
    private var probeWaiter: CompletableDeferred<Long>? = null

    private var lastAutoRefreshAt = -1L
    private var refreshIntervalMs = cfg.autoRefreshBaseMs
    private var autoRefreshPending = false

    /** Time the last test of the current server ended. The traffic counter ignores bytes around it. */
    var lastProbeAt = 0L
        private set

    /** True while a check runs or a switch is blocked; the periodic Healthy test stays away then. */
    val isTroubled: Boolean get() = checking || blocked != null

    fun recentlySwitched(): Boolean = cooldownLeft() > 0L

    // ------------------------------------------------------------------ events

    fun onRunning(running: Boolean) {
        if (!running) {
            loopJob?.cancel()
            loopJob = null
            probeWaiter?.cancel()
            probeWaiter = null
            dueAt = NEVER
            idleAt = NEVER
            blocked = null
            checking = false
            connectPending = false
            return
        }
        val now = ports.now
        val active = loopJob?.isActive == true
        if (active && now - connectedAt < cfg.duplicateRunningMs) return
        connectedAt = now
        blocked = null
        connectPending = true
        skipCooldown = false
        settleUntil = now + cfg.connectSettleMs
        dueAt = settleUntil
        idleAt = now + cfg.idleMs
        if (active) {
            wake.trySend(Unit)
        } else {
            loopJob = scope.launch { monitor() }
        }
    }

    /** The answer of the current-server test. Results nobody asked for are ignored. */
    fun onProbeResult(delayMillis: Long) {
        probeWaiter?.complete(delayMillis)
    }

    fun onNetworkChanged() {
        requestCheck(3_000L, force = true)
    }

    fun onStall() {
        if (blocked != null || checking) return
        if (ports.now - lastProbeAt < cfg.stallCooldownMs) return
        ports.say("Traffic stalled, checking connection...")
        requestCheck(0L, force = false)
    }

    /** Called when any Healthy refresh ends. [foundBackup] = Healthy now has a working server. */
    fun onRefreshFinished(foundBackup: Boolean) {
        if (foundBackup) {
            refreshIntervalMs = cfg.autoRefreshBaseMs
        } else if (autoRefreshPending) {
            refreshIntervalMs = minOf(refreshIntervalMs * 2, cfg.autoRefreshMaxMs)
        }
        autoRefreshPending = false
        requestCheck(2_000L, force = true)
    }

    private fun requestCheck(afterMs: Long, force: Boolean) {
        if (loopJob?.isActive != true) return
        if (blocked != null && !force) return
        if (force) blocked = null
        dueAt = minOf(dueAt, ports.now + afterMs)
        wake.trySend(Unit)
    }

    // ------------------------------------------------------------------ the loop

    private suspend fun monitor() {
        while (true) {
            val checkAt = if (dueAt == NEVER) NEVER else maxOf(dueAt, settleUntil)
            val idleWakeAt = if (idleAt == NEVER) NEVER else maxOf(idleAt, settleUntil)
            val target = minOf(checkAt, idleWakeAt)
            val now = ports.now
            if (target == NEVER) {
                wake.receive()
                continue
            }
            if (target > now) {
                withTimeoutOrNull((target - now).milliseconds) { wake.receive() }
                continue
            }
            try {
                if (checkAt <= now) {
                    dueAt = NEVER
                    runCheck()
                } else {
                    runIdle()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                ports.log("Guard error: ${e.message}")
                dueAt = ports.now + cfg.errorRetryMs
            }
        }
    }

    private suspend fun runIdle() {
        if (blocked != null) {
            idleAt = NEVER
            return
        }
        if (ports.recentDownBytes() >= cfg.idleMinDownBytes) {
            idleAt = ports.now + cfg.idleBusyRecheckMs // traffic is proof enough, look again later
            return
        }
        idleAt = NEVER // set again by a good result
        runCheck()
    }

    private suspend fun runCheck() {
        if (!ports.isRunning) return
        if (ports.refreshActive || ports.hasBulkTest) {
            dueAt = ports.now + cfg.deferMs
            return
        }
        if (!ports.hasInternet()) {
            block("nointernet", "No internet, not switching", NEVER, say = true)
            return
        }
        checking = true
        try {
            var oneBad = false
            if (connectPending) {
                connectPending = false
                val selected = ports.selection()
                if (selected != null) {
                    if (selected.manual || selected.storedMs < 0L) skipCooldown = true
                    if (selected.storedMs < 0L) {
                        ports.log("Selected server is marked dead, checking it now")
                        oneBad = true // one bad sample is enough
                    } else {
                        ports.log("Checking the new connection")
                    }
                }
            }
            val first = probe()
            if (isGood(first)) {
                onGood()
                return
            }
            if (!oneBad) {
                ports.log("Bad sample, confirming in ${cfg.confirmDelayMs / 1000}s")
                delay(cfg.confirmDelayMs.milliseconds)
                if (!ports.isRunning) return
                if (ports.refreshActive || ports.hasBulkTest) {
                    dueAt = ports.now + cfg.deferMs
                    return
                }
                val second = probe()
                if (isGood(second)) {
                    ports.log("Confirmation test passed, staying")
                    onGood()
                    return
                }
            }
            ports.log("Bad sample confirmed")
            trySwitch()
        } finally {
            checking = false
        }
    }

    private fun isGood(delayMillis: Long): Boolean = delayMillis in 1L..cfg.maxDelayMs

    private fun onGood() {
        blocked = null
        skipCooldown = false
        idleAt = ports.now + cfg.idleMs
    }

    /** One test of the current server. Answers come from [onProbeResult]; no answer in time = bad. */
    private suspend fun probe(): Long {
        val answer = CompletableDeferred<Long>()
        probeWaiter = answer
        lastProbeAt = ports.now
        try {
            ports.startProbe()
            val result = withTimeoutOrNull(cfg.probeTimeoutMs.milliseconds) { answer.await() }
            if (result == null) {
                ports.cancelProbe()
                ports.log("Test got no answer")
                return -1L
            }
            return result
        } finally {
            probeWaiter = null
            lastProbeAt = ports.now
        }
    }

    // ------------------------------------------------------------------ switching

    private suspend fun trySwitch() {
        if (ports.hasBulkTest) {
            block("bulk", "Switch postponed: another test is running", ports.now + cfg.deferMs)
            return
        }
        if (!ports.hasInternet()) {
            block("nointernet", "No internet, not switching", NEVER, say = true)
            return
        }
        val left = cooldownLeft()
        if (left > 0L && !skipCooldown) {
            block("cooldown", "Switch postponed: cooldown after the last switch", ports.now + left)
            return
        }
        val pick = ports.pickBackup()
        if (pick == null) {
            noBackup()
            return
        }
        if (!ports.isRunning) return
        if (ports.refreshActive) {
            dueAt = ports.now + cfg.deferMs
            return
        }
        lastSwitchAt = ports.now
        skipCooldown = false
        blocked = null
        idleAt = ports.now + cfg.idleMs
        ports.markSuspect(pick.oldGuid)
        ports.say("Connection problem, switching: ${pick.oldName} -> ${pick.bestName}")
        ports.connectTo(pick.bestGuid)
    }

    private fun noBackup() {
        val now = ports.now
        val nextAllowed = if (lastAutoRefreshAt < 0L) now else lastAutoRefreshAt + refreshIntervalMs
        if (now < nextAllowed) {
            val minutes = (nextAllowed - now) / 60_000L
            block("nobackup", "No backup server, next automatic refresh in about $minutes min", nextAllowed)
            return
        }
        if (ports.startAutoRefresh()) {
            lastAutoRefreshAt = now
            autoRefreshPending = true
            block("refreshing", "No healthy backup, refreshing Healthy", now + cfg.refreshWaitMaxMs, say = true)
        } else {
            dueAt = now + cfg.deferMs // a refresh is already running; look again soon
        }
    }

    /** Switching is not possible now: stop testing and sleep until [retryAt] or a forced event. */
    private fun block(reason: String, message: String, retryAt: Long, say: Boolean = false) {
        if (blocked != reason) {
            if (say) ports.say(message) else ports.log(message)
        }
        blocked = reason
        dueAt = retryAt
        idleAt = NEVER
    }

    private fun cooldownLeft(): Long =
        if (lastSwitchAt < 0L) 0L else maxOf(0L, lastSwitchAt + cfg.cooldownMs - ports.now)
}
