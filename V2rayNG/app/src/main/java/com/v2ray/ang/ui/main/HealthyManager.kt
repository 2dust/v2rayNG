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
}

/**
 * All Healthy-pool logic: refresh, fast connect, connection watchdog,
 * quick switch, periodic Healthy testing, traffic stall detection, auto-connect retry.
 */
class HealthyManager(
    private val scope: CoroutineScope,
    private val dataSource: MainDataSource,
    private val host: HealthyHost,
) {
    private val io = Dispatchers.IO
    private val healthyName = "Healthy"   // name of the pool group
    private val maxHealthy = 50           // max servers kept in the pool

    // ---------- Refresh / fast connect ----------
    private var refreshHealthyJob: Job? = null

    @Volatile
    private var autoConnectOnFirstGood = false
    private val firstGoodMaxDelayMs = 1500L

    @Volatile
    private var refreshActive = false

    // ---------- Watchdog ----------
    private var watchdogJob: Job? = null
    private var networkJob: Job? = null
    private var lastSwitchMs = 0L
    private val watchdogRetryMs = 10_000L       // after a bad sample
    private val watchdogMaxDelayMs = 4000L
    private val watchdogWindowMs = 120_000L     // how far back samples count
    private val watchdogSamples = ArrayDeque<Pair<Long, Boolean>>() // (time, isBad)
    private val watchdogTimeoutMs = 10_000L

    @Volatile
    private var watchdogGotResult = false

    @Volatile
    private var lastProbeDelay = 0L

    @Volatile
    private var lastSampleBad = false
    private val switchCooldownMs = 60_000L
    private val watchdogCheckMs = 30_000L       // first test after connecting
    private val smartMinDownBytes = 2048L       // enough download in the window = connection is alive
    private val smartTrafficWindowMs = 30_000L
    private val smartIdleTestMs = 300_000L      // test every 5 min when there is no traffic
    private var lastTestMs = 0L

    // ---------- Suspect list (used by quick switch) ----------
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

    @Volatile
    private var periodicRunning = false

    // ---------- Traffic stall ----------
    private var stallSamples = 0
    private var lastProbeMs = 0L
    private val stallMinUpBytes = 500L
    private val stallMaxDownBytes = 100L
    private val stallSamplesNeeded = 4          // 4 samples x 3 s = about 12 s
    private val probeCooldownMs = 15_000L
    private var lastRxBytes = -1L
    private var lastTxBytes = -1L
    private val rxHistory = ArrayDeque<Pair<Long, Long>>() // (time, bytes downloaded in that interval)

    // ---------- Auto connect ----------
    private var autoConnectGuid: String? = null
    private var autoConnectRetries = 0

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
        if (running) {
            lastPeriodicMs = System.currentTimeMillis() // first batch 5 minutes after connecting
            startWatchdog()
        } else {
            stopWatchdog()
        }
    }

    fun onDelayResult(delayMillis: Long) {
        watchdogGotResult = true
        lastProbeDelay = delayMillis
        recordWatchdogSample(delayMillis !in 1L..watchdogMaxDelayMs)
    }

    fun onConfigSuccess(guid: String, delayMillis: Long) {
        if (autoConnectOnFirstGood && delayMillis in 1L..firstGoodMaxDelayMs) {
            autoConnectOnFirstGood = false
            host.toast("Connecting to first fast server...")
            host.connectToServer(guid)
        }
    }

    // Called by the Activity when the underlying network changes (Wi-Fi <-> mobile, lost, back)
    fun onNetworkChanged() {
        scope.launch {
            if (!host.isRunning) return@launch
            networkJob?.cancel()
            networkJob = launch {
                delay(3000) // let the new network settle
                if (host.isRunning && !host.hasBulkTest) {
                    probeCurrentServer()
                }
            }
        }
    }

    fun onStartFailure() {
        val failed = autoConnectGuid ?: return
        if (failed != host.selectedGuid) return
        autoConnectGuid = null
        if (autoConnectRetries >= 3) {
            autoConnectRetries = 0
            host.toast("Auto-connect failed, please choose a server manually")
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
                host.toast("Server failed to start, trying another...")
                host.connectToServer(next)
            }
        }
    }

    // =====================================================================
    // Healthy pool refresh
    // =====================================================================

    fun refresh() {
        if (refreshHealthyJob?.isActive == true) {
            host.toast("Healthy refresh is already running")
            return
        }
        refreshHealthyJob = scope.launch {
            try {
                refreshActive = true

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

                var healthyTestedFirst = false
                val selectedNow = host.selectedGuid
                val storedBad = withContext(io) {
                    val ms = selectedNow?.let { dataSource.decodeAffiliationInfo(it)?.testDelayMillis } ?: 0L
                    ms < 0L
                }
                val needSwitch = !host.isRunning || storedBad || !isCurrentConnectionGood()
                if (needSwitch) {
                    val quickGuid = withContext(io) {
                        val currentId = selectedNow?.let { dataSource.decodeServerConfig(it)?.duplicateIdentity() }
                        dataSource.getServerGuidList(healthy.guid)
                            .mapNotNull { guid ->
                                val ms = dataSource.decodeAffiliationInfo(guid)?.testDelayMillis ?: 0L
                                if (ms <= 0L) return@mapNotNull null
                                val id = dataSource.decodeServerConfig(guid)?.duplicateIdentity()
                                if (id != null && id == currentId) return@mapNotNull null
                                guid to ms
                            }
                            .minByOrNull { it.second }?.first
                    }
                    if (quickGuid != null) {
                        host.toast("Connecting to the fastest known server...")
                        host.connectToServer(quickGuid)
                        delay(3000) // let the VPN restart settle before the tests start
                    } else {
                        autoConnectOnFirstGood = true
                        host.toast("Testing Healthy servers first...")
                        testGroupAndWait(healthy.guid)
                        healthyTestedFirst = true
                    }
                }
                host.toast("Updating subscriptions...")
                withContext(io) { dataSource.updateConfigViaSubAll() }

                for (group in sources + healthy) {
                    if (group.guid == healthy.guid && healthyTestedFirst) continue
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

                    var removed = 0
                    healthyGuids.forEach { guid ->
                        if (delayOf(guid) < 0L) {
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
                host.toast(summary)

                val fastestGuid = withContext(io) {
                    dataSource.getServerGuidList(healthy.guid)
                        .map { it to (dataSource.decodeAffiliationInfo(it)?.testDelayMillis ?: 0L) }
                        .filter { it.second > 0L }
                        .minByOrNull { it.second }?.first
                }
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
                        host.toast("Switching to the fastest server...")
                        host.connectToServer(fastestGuid)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Healthy refresh failed", e)
                host.toastFailure()
            } finally {
                autoConnectOnFirstGood = false
                refreshActive = false
                lastPeriodicMs = System.currentTimeMillis() // the refresh just tested everything
            }
        }
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
        delay(500)
    }

    // =====================================================================
    // Connection watchdog
    // =====================================================================

    private fun startWatchdog() {
        if (watchdogJob?.isActive == true) return
        watchdogSamples.clear()
        lastSampleBad = false
        // first test about 30 s after connecting
        lastTestMs = System.currentTimeMillis() - smartIdleTestMs + watchdogCheckMs
        watchdogJob = scope.launch {
            while (true) {
                delay(5_000)
                if (!host.isRunning) break
                if (host.hasBulkTest || refreshActive) continue
                val now = System.currentTimeMillis()
                val trouble = watchdogSamples.any { it.second }
                val due = if (trouble) {
                    // there was a bad sample: re-test fast, whatever the traffic is
                    now - lastTestMs >= watchdogRetryMs
                } else {
                    // healthy traffic is proof enough; only test when it is quiet
                    rxHistory.sumOf { it.second } < smartMinDownBytes &&
                        now - lastTestMs >= smartIdleTestMs
                }
                if (due) probeCurrentServer()
            }
        }
    }

    private fun stopWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
        watchdogSamples.clear()
    }

    private suspend fun probeCurrentServer() {
        if (!host.isRunning) return
        lastTestMs = System.currentTimeMillis()
        watchdogGotResult = false
        host.startCurrentServerTest()
        var waited = 0L
        while (waited < watchdogTimeoutMs && !watchdogGotResult && host.isRunning) {
            delay(250)
            waited += 250
        }
        if (host.isRunning && !watchdogGotResult) {
            // No answer at all: count it as a bad sample and clear the stuck "Testing" state
            host.cancelAllTests()
            recordWatchdogSample(true)
        }
    }

    private suspend fun isCurrentConnectionGood(): Boolean {
        probeCurrentServer()
        return lastProbeDelay in 1L..watchdogMaxDelayMs
    }

    private fun recordWatchdogSample(bad: Boolean) {
        lastSampleBad = bad
        val now = System.currentTimeMillis()
        watchdogSamples.addLast(now to bad)
        while (watchdogSamples.isNotEmpty() &&
            now - watchdogSamples.first().first > watchdogWindowMs
        ) {
            watchdogSamples.removeFirst()
        }
        if (bad && !refreshActive) {
            watchdogSamples.clear()
            quickSwitch()
        }
    }

    // =====================================================================
    // Quick switch: first problem -> fastest known healthy server
    // =====================================================================

    private fun quickSwitch() {
        if (host.hasBulkTest) return
        val now = System.currentTimeMillis()
        if (now - lastSwitchMs < switchCooldownMs) return
        scope.launch {
            val prepared: Triple<String, String, String>? = withContext(io) {
                val healthy = dataSource.getSubscriptions()
                    .firstOrNull { it.subscription.remarks == healthyName }
                    ?: return@withContext null
                val currentGuid = dataSource.getSelectServer() ?: return@withContext null
                val currentProfile = dataSource.decodeServerConfig(currentGuid)
                val currentId = currentProfile?.duplicateIdentity()
                val best = dataSource.getServerGuidList(healthy.guid)
                    .mapNotNull { guid ->
                        if (guid == currentGuid) return@mapNotNull null
                        val markedAt = suspectGuids[guid]
                        if (markedAt != null && now - markedAt < suspectMs) return@mapNotNull null
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
                Triple(best, currentGuid, currentProfile?.subscriptionId.orEmpty())
            }
            if (prepared == null) {
                host.toast("No backup server with a good test result in Healthy")
                return@launch
            }
            if (!host.isRunning || refreshActive) return@launch
            val (bestGuid, oldGuid, oldGroupId) = prepared
            lastSwitchMs = System.currentTimeMillis()
            suspectGuids[oldGuid] = lastSwitchMs
            host.toast("Connection problem, switching to the fastest known server...")
            host.connectToServer(bestGuid)

            // Test the old server in the background after the VPN has settled
            delay(20_000)
            if (host.isRunning && !host.hasBulkTest && !refreshActive &&
                oldGroupId.isNotEmpty()
            ) {
                val requestId = host.beginBulkTest(oldGroupId)
                dataSource.sendMsg2TestService(
                    TestServiceMessage(
                        key = AppConfig.MSG_MEASURE_CONFIG_START,
                        subscriptionId = oldGroupId,
                        serverGuids = listOf(oldGuid),
                        onlyTcp = false
                    ),
                    requestId
                )
            }
        }
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
                if (lastSampleBad) continue                          // current server has a problem, do not add load
                if (now - lastSwitchMs < switchCooldownMs) continue  // VPN is still settling after a switch
                if (now - lastPeriodicMs < periodicIntervalMs) continue
                if (rxHistory.sumOf { it.second } >= heavyTrafficBytes) continue // user is busy
                lastPeriodicMs = now
                try {
                    runPeriodicBatch()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Periodic Healthy test failed", e)
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
            val start = periodicCursor % candidates.size
            val batch = (0 until minOf(periodicBatchSize, candidates.size))
                .map { candidates[(start + it) % candidates.size] }
            periodicCursor = (start + batch.size) % candidates.size
            val previous = batch.associateWith {
                dataSource.decodeAffiliationInfo(it)?.testDelayMillis ?: 0L
            }
            Triple(healthy.guid, batch, previous)
        } ?: return

        val (healthyId, batch, previous) = prepared
        periodicRunning = true
        try {
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
            lastTestMs = System.currentTimeMillis() // our own test bytes must not look like user traffic
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

                    now > 0L -> failStreak.remove(guid)
                }
            }
            Triple(changed, networkProblem, condemned)
        }
        val (changed, networkProblem, condemned) = outcome
        if (changed) host.reloadGroups()
        if (networkProblem) host.toast("Healthy check: all servers failed, ignoring (network problem?)")
        else if (condemned > 0) host.toast("Healthy check: $condemned server(s) marked dead")
    }

    // =====================================================================
    // Traffic stall detection
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
                if (periodicRunning || now - lastTestMs < 8000) continue
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
                        onTrafficStall()
                    }
                } else {
                    stallSamples = 0
                }
            }
        }
    }

    private fun onTrafficStall() {
        val now = System.currentTimeMillis()
        if (!host.isRunning || host.hasBulkTest) return
        if (now - lastProbeMs < probeCooldownMs) return
        lastProbeMs = now
        lastTestMs = now
        host.toast("Traffic stalled, checking connection...")
        scope.launch { probeCurrentServer() }
    }
}
