package com.v2ray.ang.ui.main

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.extension.delay
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * What HealthyManager needs from the screen's ViewModel.
 * MainViewModel implements this, so HealthyManager never touches its internals.
 */
interface HealthyHost {
    val isRunning: Boolean
    val selectedGuid: String?
    val hasBulkTest: Boolean
    fun isBulkTest(requestId: String): Boolean
    fun beginBulkTest(groupId: String): String
    fun showTesting()
    fun startCurrentServerTest()
    fun cancelAllTests()
    suspend fun reloadGroups()
    fun connectToServer(guid: String)
    fun toast(message: String)
    fun toastFailure()
    fun hasInternet(): Boolean
    fun copyText(label: String, text: String)
}

/**
 * All Healthy-pool logic: refresh, fast connect, periodic Healthy testing, traffic stall detection,
 * auto-connect retry. The decisions about the current connection (when to test it, when to switch)
 * live in [HealthyGuard]; this class connects the guard to the real app.
 */
class HealthyManager(
    private val scope: CoroutineScope,
    private val dataSource: MainDataSource,
    private val host: HealthyHost,
) {
    private val io = Dispatchers.IO
    private val healthyName = "Healthy"   // name of the pool group
    private val maxHealthy = 50           // max servers kept in the pool

    // ---------- Event log (kept in memory, copied to the clipboard at the end of a refresh) ----------
    private val logLines = ArrayDeque<String>()
    private val logTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
    private val logMaxLines = 80

    private fun log(message: String) {
        synchronized(logLines) {
            logLines.addLast("${logTime.format(java.util.Date())} $message")
            while (logLines.size > logMaxLines) logLines.removeFirst()
        }
    }

    // Log it and show it as a toast
    private fun say(message: String) {
        log(message)
        host.toast(message)
    }

    fun logText(): String = synchronized(logLines) { logLines.joinToString("\n") }

    // ---------- Refresh / fast connect ----------
    private var refreshHealthyJob: Job? = null

    @Volatile
    private var autoConnectOnFirstGood = false
    private val firstGoodMaxDelayMs = 1500L

    @Volatile
    private var refreshActive = false

    // ---------- Suspect list (servers we just left; tested first by the periodic test) ----------
    private val suspectGuids = ConcurrentHashMap<String, Long>() // guid -> time it was marked suspect
    private val suspectMs = 300_000L

    // ---------- Periodic Healthy testing ----------
    private var periodicJob: Job? = null
    private val periodicIntervalMs = 300_000L   // one batch every 5 minutes
    private val periodicCheckMs = 30_000L       // how often we check whether a batch is due
    private val periodicBatchSize = 10
    private val periodicMaxWaitMs = 60_000L
    private val heavyTrafficBytes = 3_000_000L  // downloaded in the traffic window = busy, do not test
    private val failsToCondemn = 3              // consecutive failed batches before a server gets -1
    private var lastPeriodicMs = 0L
    private var periodicCursor = 0
    private val failStreak = ConcurrentHashMap<String, Int>()
    private var quietUntilMs = 0L               // bytes of our own tests must not look like user traffic

    @Volatile
    private var periodicRunning = false

    private data class PenaltyOutcome(
        val changed: Boolean,
        val networkProblem: Boolean,
        val condemned: Int,
        val ok: Int,
        val failed: Int,
    )

    // ---------- Traffic stall ----------
    private var stallSamples = 0
    private val stallMinUpBytes = 500L
    private val stallMaxDownBytes = 100L
    private val stallSamplesNeeded = 4          // 4 samples x 3 s = about 12 s
    private val smartTrafficWindowMs = 30_000L
    private var lastRxBytes = -1L
    private var lastTxBytes = -1L
    private val rxHistory = ArrayDeque<Pair<Long, Long>>() // (time, bytes downloaded in that interval)

    // ---------- Auto connect ----------
    private var autoConnectGuid: String? = null
    private var autoConnectRetries = 0

    // ---------- The connection guard ----------
    private val guard = HealthyGuard(scope, Ports())

    private inner class Ports : GuardPorts {
        override val now: Long get() = System.currentTimeMillis()
        override val isRunning: Boolean get() = host.isRunning
        override val refreshActive: Boolean get() = this@HealthyManager.refreshActive
        override val hasBulkTest: Boolean get() = host.hasBulkTest

        override fun hasInternet(): Boolean = host.hasInternet()

        override fun recentDownBytes(): Long = rxHistory.sumOf { it.second }

        override fun log(message: String) {
            this@HealthyManager.log(message)
        }

        override fun say(message: String) {
            this@HealthyManager.say(message)
        }

        override fun startProbe() {
            host.startCurrentServerTest()
        }

        override fun cancelProbe() {
            host.cancelAllTests()
        }

        override suspend fun selection(): GuardSelection? {
            val guid = host.selectedGuid ?: return null
            val storedMs = withContext(io) {
                dataSource.decodeAffiliationInfo(guid)?.testDelayMillis ?: 0L
            }
            // not chosen by this manager = chosen by the user
            return GuardSelection(guid, storedMs, guid != autoConnectGuid)
        }

        override suspend fun pickBackup(): GuardSwitch? = withContext(io) {
            val healthy = dataSource.getSubscriptions()
                .firstOrNull { it.subscription.remarks == healthyName }
                ?: return@withContext null
            val currentGuid = dataSource.getSelectServer() ?: return@withContext null
            val currentProfile = dataSource.decodeServerConfig(currentGuid)
            val currentId = currentProfile?.duplicateIdentity()
            val nowMs = System.currentTimeMillis()
            val best = dataSource.getServerGuidList(healthy.guid)
                .mapNotNull { guid ->
                    if (guid == currentGuid) return@mapNotNull null
                    val markedAt = suspectGuids[guid]
                    if (markedAt != null && nowMs - markedAt < suspectMs) return@mapNotNull null
                    if ((failStreak[guid] ?: 0) > 0) return@mapNotNull null // failed its last periodic test
                    val ms = dataSource.decodeAffiliationInfo(guid)?.testDelayMillis ?: 0L
                    if (ms <= 0L) return@mapNotNull null
                    val id = dataSource.decodeServerConfig(guid)?.duplicateIdentity()
                    if (id != null && id == currentId) return@mapNotNull null
                    guid to ms
                }
                .minByOrNull { it.second }
                ?.first
                ?: return@withContext null
            GuardSwitch(
                bestGuid = best,
                bestName = dataSource.decodeServerConfig(best)?.remarks ?: best.take(6),
                oldGuid = currentGuid,
                oldName = currentProfile?.remarks ?: currentGuid.take(6),
            )
        }

        override fun markSuspect(guid: String) {
            suspectGuids[guid] = System.currentTimeMillis()
        }

        override fun connectTo(guid: String) {
            host.connectToServer(guid)
        }

        override fun startAutoRefresh(): Boolean = startRefresh(auto = true)
    }

    // =====================================================================
    // Entry points called by MainViewModel
    // =====================================================================

    fun start() {
        collectTraffic()
        startPeriodic()
    }

    fun noteConnectRequest(guid: String) {
        autoConnectGuid = guid
    }

    fun onStartSuccess() {
        autoConnectRetries = 0
    }

    fun onRunningChanged(running: Boolean) {
        if (running) lastPeriodicMs = System.currentTimeMillis() // first batch 5 minutes after connecting
        guard.onRunning(running)
    }

    fun onDelayResult(delayMillis: Long) {
        guard.onProbeResult(delayMillis)
    }

    fun onConfigSuccess(guid: String, delayMillis: Long) {
        if (!autoConnectOnFirstGood || delayMillis !in 1L..firstGoodMaxDelayMs) return
        autoConnectOnFirstGood = false
        if (host.isRunning && guid == host.selectedGuid) {
            log("First good server is the current one, staying")
            return
        }
        say("Connecting to first fast server (${delayMillis}ms)...")
        host.connectToServer(guid)
    }

    // Called by the Activity when the underlying network changes (Wi-Fi <-> mobile, lost, back)
    fun onNetworkChanged() {
        scope.launch {
            if (!host.isRunning) return@launch
            log("Network changed")
            guard.onNetworkChanged()
        }
    }

    fun onStartFailure() {
        val failed = autoConnectGuid ?: return
        if (failed != host.selectedGuid) return
        autoConnectGuid = null
        if (autoConnectRetries >= 3) {
            autoConnectRetries = 0
            say("Auto-connect failed, please choose a server manually")
            return
        }
        autoConnectRetries++
        scope.launch {
            val next = withContext(io) {
                val healthy = dataSource.getSubscriptions()
                    .firstOrNull { it.subscription.remarks == healthyName }
                    ?: return@withContext null
                val failedId = dataSource.decodeServerConfig(failed)?.duplicateIdentity()
                com.v2ray.ang.handler.MmkvManager.encodeServerTestDelayMillis(failed, -1L)
                dataSource.getServerGuidList(healthy.guid)
                    .mapNotNull { guid ->
                        val ms = dataSource.decodeAffiliationInfo(guid)?.testDelayMillis ?: 0L
                        if (ms <= 0L) return@mapNotNull null
                        val id = dataSource.decodeServerConfig(guid)?.duplicateIdentity()
                        if (id != null && id == failedId) return@mapNotNull null
                        guid to ms
                    }
                    .minByOrNull { it.second }
                    ?.first
            }
            if (next != null) {
                say("Server failed to start, trying another...")
                host.connectToServer(next)
            }
        }
    }

    // =====================================================================
    // Healthy pool refresh
    // =====================================================================

    fun refresh() {
        startRefresh(auto = false)
    }

    /** Returns false (and does nothing) when a refresh is already running. */
    private fun startRefresh(auto: Boolean): Boolean {
        if (refreshHealthyJob?.isActive == true) {
            if (!auto) host.toast("Healthy refresh is already running")
            return false
        }
        refreshHealthyJob = scope.launch {
            var foundBackup = false
            try {
                refreshActive = true
                log("Refresh started (running=${host.isRunning}, auto=$auto)")

                var subs = withContext(io) { dataSource.getSubscriptions() }
                if (subs.none { it.subscription.remarks == healthyName }) {
                    withContext(io) {
                        com.v2ray.ang.handler.MmkvManager.encodeSubscription(
                            com.v2ray.ang.util.Utils.getUuid(),
                            com.v2ray.ang.dto.entities.SubscriptionItem(
                                remarks = healthyName,
                                enabled = false,
                                autoUpdate = false
                            )
                        )
                    }
                    subs = withContext(io) { dataSource.getSubscriptions() }
                }
                val healthy = subs.first { it.subscription.remarks == healthyName }
                val sources = subs.filter {
                    it.guid != healthy.guid && it.guid != AppConfig.DEFAULT_SUBSCRIPTION_ID
                }

                // Always: test Healthy first and connect to the first fast server,
                // whether the VPN is off, on a dead server, or on a good one.
                // If Healthy has no fast server, the first fast server found
                // while testing the subscriptions is used (the flag stays on).
                autoConnectOnFirstGood = true
                say("Testing Healthy servers first...")
                testGroupAndWait(healthy.guid)

                say("Updating subscriptions...")
                withContext(io) { dataSource.updateConfigViaSubAll() }

                for (group in sources) {
                    testGroupAndWait(group.guid)
                }

                val summary = withContext(io) {
                    fun delayOf(guid: String): Long =
                        dataSource.decodeAffiliationInfo(guid)?.testDelayMillis ?: 0L

                    val sourceResults = sources
                        .flatMap { dataSource.getServerGuidList(it.guid) }
                        .mapNotNull { guid ->
                            val profile = dataSource.decodeServerConfig(guid) ?: return@mapNotNull null
                            profile to delayOf(guid)
                        }
                    val healthyGuids = dataSource.getServerGuidList(healthy.guid)

                    val anyWorking = sourceResults.any { it.second > 0L } ||
                            healthyGuids.any { delayOf(it) > 0L }
                    if (!anyWorking) {
                        return@withContext "No working server found (network down?). Nothing changed."
                    }

                    val connectedGuid = host.selectedGuid
                    var removed = 0
                    healthyGuids.forEach { guid ->
                        if (delayOf(guid) < 0L) {
                            if (guid == connectedGuid) {
                                // never delete the server we are connected to; the next Refresh removes it
                                log("Dead server is the current one, keeping it for now")
                                return@forEach
                            }
                            dataSource.removeServer(guid)
                            failStreak.remove(guid)
                            removed++
                        }
                    }

                    val keptGuids = dataSource.getServerGuidList(healthy.guid)
                    val existing = HashSet<ProfileItem>()
                    keptGuids.forEach { guid ->
                        dataSource.decodeServerConfig(guid)?.let { existing.add(it.duplicateIdentity()) }
                    }

                    var added = 0
                    for ((profile, ms) in sourceResults.filter { it.second > 0L }.sortedBy { it.second }) {
                        if (keptGuids.size + added >= maxHealthy) break
                        if (profile.configType.isComplexType()) continue
                        if (!existing.add(profile.duplicateIdentity())) continue
                        val newGuid = com.v2ray.ang.handler.MmkvManager.encodeServerConfig(
                            "",
                            profile.copy(
                                subscriptionId = healthy.guid,
                                addedTime = System.currentTimeMillis()
                            )
                        )
                        com.v2ray.ang.handler.MmkvManager.encodeServerTestDelayMillis(newGuid, ms)
                        added++
                    }

                    dataSource.sortByTestResultsForSub(healthy.guid)
                    "Healthy: -$removed dead, +$added new, total ${keptGuids.size + added}"
                }

                host.reloadGroups()
                say(summary)

                val fastestGuid = withContext(io) {
                    dataSource.getServerGuidList(healthy.guid)
                        .map { it to (dataSource.decodeAffiliationInfo(it)?.testDelayMillis ?: 0L) }
                        .filter { it.second > 0L }
                        .minByOrNull { it.second }?.first
                }
                foundBackup = fastestGuid != null
                if (fastestGuid != null) {
                    val currentGuid = host.selectedGuid
                    val shouldSwitch = withContext(io) {
                        if (!host.isRunning) return@withContext true
                        val best = dataSource.decodeServerConfig(fastestGuid)?.duplicateIdentity()
                        val current = currentGuid?.let { dataSource.decodeServerConfig(it)?.duplicateIdentity() }
                        if (best != null && best == current) return@withContext false
                        val currentMs = currentGuid?.let { dataSource.decodeAffiliationInfo(it)?.testDelayMillis } ?: 0L
                        val bestMs = dataSource.decodeAffiliationInfo(fastestGuid)?.testDelayMillis ?: 0L
                        currentMs <= 0L || bestMs * 2 < currentMs
                    }
                    if (shouldSwitch) {
                        say("Switching to the fastest server...")
                        host.connectToServer(fastestGuid)
                    } else {
                        log("Refresh done: current server is good enough, staying")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Healthy refresh failed", e)
                log("Refresh failed: ${e.message}")
                host.toastFailure()
            } finally {
                autoConnectOnFirstGood = false
                refreshActive = false
                lastPeriodicMs = System.currentTimeMillis() // the refresh just tested everything
                host.copyText("Healthy log", logText()) // temporary: paste it in the chat for diagnosis
                guard.onRefreshFinished(foundBackup)
            }
        }
        return true
    }

    private suspend fun testGroupAndWait(groupId: String) {
        val guids = withContext(io) { dataSource.getServerGuidList(groupId) }
        if (guids.isEmpty()) return
        withContext(io) { dataSource.clearAllTestDelayResults(guids) }
        val requestId = host.beginBulkTest(groupId)
        host.showTesting()
        dataSource.sendMsg2TestService(
            TestServiceMessage(
                key = AppConfig.MSG_MEASURE_CONFIG_START,
                subscriptionId = groupId,
                serverGuids = emptyList(),
                onlyTcp = false
            ),
            requestId
        )
        var waited = 0
        while (host.isBulkTest(requestId) && waited < 600) { // up to 5 minutes
            delay(500)
            waited++
        }
        // A test that never ends would block the guard for good, so stop it
        if (host.isBulkTest(requestId)) host.cancelAllTests()
        delay(500)
    }

    // =====================================================================
    // Periodic Healthy testing: keep ready servers fresh while connected
    // =====================================================================

    private fun startPeriodic() {
        if (periodicJob?.isActive == true) return
        periodicJob = scope.launch {
            while (true) {
                delay(periodicCheckMs)
                val now = System.currentTimeMillis()
                if (!host.isRunning || refreshActive || host.hasBulkTest || periodicRunning) continue
                if (guard.isTroubled) continue                        // current server has a problem, do not add load
                if (guard.recentlySwitched()) continue                // VPN is still settling after a switch
                if (now - lastPeriodicMs < periodicIntervalMs) continue
                if (rxHistory.sumOf { it.second } >= heavyTrafficBytes) continue // user is busy
                lastPeriodicMs = now
                try {
                    runPeriodicBatch()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Periodic Healthy test failed", e)
                    log("Periodic test failed: ${e.message}")
                }
            }
        }
    }

    private suspend fun runPeriodicBatch() {
        val prepared: Triple<String, List<String>, Map<String, Long>> = withContext(io) {
            val healthy = dataSource.getSubscriptions()
                .firstOrNull { it.subscription.remarks == healthyName }
                ?: return@withContext null
            val currentGuid = host.selectedGuid
            val currentId = currentGuid?.let { dataSource.decodeServerConfig(it)?.duplicateIdentity() }
            val candidates = dataSource.getServerGuidList(healthy.guid).filter { guid ->
                if (guid == currentGuid) return@filter false
                val ms = dataSource.decodeAffiliationInfo(guid)?.testDelayMillis ?: 0L
                if (ms < 0L) return@filter false // already condemned, the next Refresh removes it
                val id = dataSource.decodeServerConfig(guid)?.duplicateIdentity()
                !(id != null && id == currentId)
            }
            if (candidates.isEmpty()) return@withContext null
            // Servers we just left come first, then the rotating part of the pool
            val suspects = candidates.filter { suspectGuids.containsKey(it) }.take(periodicBatchSize)
            val rest = candidates.filter { it !in suspects }
            val fillCount = minOf(periodicBatchSize - suspects.size, rest.size)
            val start = if (rest.isEmpty()) 0 else periodicCursor % rest.size
            val fill = (0 until fillCount).map { rest[(start + it) % rest.size] }
            periodicCursor = if (rest.isEmpty()) 0 else (start + fillCount) % rest.size
            val batch = suspects + fill
            val previous = batch.associateWith {
                dataSource.decodeAffiliationInfo(it)?.testDelayMillis ?: 0L
            }
            Triple(healthy.guid, batch, previous)
        } ?: return

        val (healthyId, batch, previous) = prepared
        periodicRunning = true
        try {
            log("Periodic test of ${batch.size} Healthy servers")
            val requestId = host.beginBulkTest(healthyId)
            dataSource.sendMsg2TestService(
                TestServiceMessage(
                    key = AppConfig.MSG_MEASURE_CONFIG_START,
                    subscriptionId = healthyId,
                    serverGuids = batch,
                    onlyTcp = false
                ),
                requestId
            )
            var waited = 0L
            while (host.isBulkTest(requestId) && waited < periodicMaxWaitMs) {
                delay(500)
                waited += 500
            }
            if (host.isBulkTest(requestId)) host.cancelAllTests()
            delay(500)
            applyPenaltyRule(batch, previous)
        } finally {
            periodicRunning = false
            quietUntilMs = System.currentTimeMillis() + 8_000L
        }
    }

    /**
     * The test service writes -1 for every failure. Here a single failure is softened:
     * - every server of the batch failed (3 or more): network problem, restore everything, count nothing;
     * - otherwise a failed server gets its old result back until it fails [failsToCondemn] times in a row.
     */
    private suspend fun applyPenaltyRule(batch: List<String>, previous: Map<String, Long>) {
        val outcome = withContext(io) {
            val results = batch.associateWith {
                dataSource.decodeAffiliationInfo(it)?.testDelayMillis ?: 0L
            }
            val failedCount = results.values.count { it < 0L }
            val okCount = results.values.count { it > 0L }
            val networkProblem = batch.size >= 3 && failedCount == batch.size
            var changed = false
            var condemned = 0
            for (guid in batch) {
                val now = results[guid] ?: 0L
                val old = previous[guid] ?: 0L
                when {
                    now < 0L && networkProblem -> {
                        com.v2ray.ang.handler.MmkvManager.encodeServerTestDelayMillis(guid, old)
                        changed = true
                    }

                    now < 0L -> {
                        val streak = (failStreak[guid] ?: 0) + 1
                        failStreak[guid] = streak
                        if (streak < failsToCondemn) {
                            com.v2ray.ang.handler.MmkvManager.encodeServerTestDelayMillis(guid, old)
                            changed = true
                        } else {
                            condemned++
                        }
                    }

                    now > 0L -> {
                        failStreak.remove(guid)
                        suspectGuids.remove(guid) // it answered again, it may be a backup again
                    }
                }
            }
            PenaltyOutcome(changed, networkProblem, condemned, okCount, failedCount)
        }
        log("Periodic done: ${outcome.ok} ok, ${outcome.failed} failed")
        if (outcome.changed) host.reloadGroups()
        if (outcome.networkProblem) {
            say("Healthy check: all servers failed, ignoring (network problem?)")
        } else if (outcome.condemned > 0) {
            say("Healthy check: ${outcome.condemned} server(s) marked dead")
        }
    }

    // =====================================================================
    // Traffic stall detection (the guard decides what to do about it)
    // =====================================================================

    private fun collectTraffic() {
        scope.launch {
            val uid = android.os.Process.myUid()
            while (true) {
                delay(3000)
                val rx = android.net.TrafficStats.getUidRxBytes(uid)
                val tx = android.net.TrafficStats.getUidTxBytes(uid)
                if (rx < 0 || tx < 0) continue // -1 = not supported on this device
                val up = if (lastTxBytes >= 0) tx - lastTxBytes else 0L
                val down = if (lastRxBytes >= 0) rx - lastRxBytes else 0L
                lastTxBytes = tx
                lastRxBytes = rx
                val now = System.currentTimeMillis()
                if (!host.isRunning) {
                    stallSamples = 0
                    rxHistory.clear()
                    continue
                }
                // Ignore the bytes of our own tests, so they do not look like real traffic
                if (periodicRunning || now < quietUntilMs || now - guard.lastProbeAt < 8000) continue
                rxHistory.addLast(now to down.coerceAtLeast(0L))
                while (rxHistory.isNotEmpty() &&
                    now - rxHistory.first().first > smartTrafficWindowMs
                ) {
                    rxHistory.removeFirst()
                }
                if (up >= stallMinUpBytes && down <= stallMaxDownBytes) {
                    stallSamples++
                    if (stallSamples >= stallSamplesNeeded) {
                        stallSamples = 0
                        guard.onStall()
                    }
                } else {
                    stallSamples = 0
                }
            }
        }
    }
}
