package com.v2ray.ang.service

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.OutboundProbePlan
import com.v2ray.ang.dto.OutboundProbeProfilePlan
import com.v2ray.ang.dto.RealPingEvent
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import libv2ray.OutboundProbeHandler
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs one complete UI delay-test batch through one native Xray instance.
 *
 * CoreTestService hosts this worker in a dedicated process. AndroidLib keeps
 * one Xray instance, limits active configuration groups with the user's True
 * delay concurrency setting, and publishes a snapshot whenever one candidate
 * finishes. This avoids the former one-temporary-core-per-profile fan-out.
 */
class RealPingWorkerService(
    private val context: Context,
    private val guids: List<String>,
    private val onEvent: (RealPingEvent) -> Unit = {},
) {
    private val job = Job()
    private val scope = CoroutineScope(job + Dispatchers.IO + CoroutineName("OutboundProbeBatch"))
    private val controller = CoreNativeManager.newOutboundProbeController()
    private val networkIdentity = NetworkIdentityResolver.resolveCurrent(context)
    private val finished = AtomicBoolean(false)
    private val emittedDelays = mutableMapOf<String, Long>()
    private val emittedRoutes = mutableMapOf<String, String>()
    private val completedGuids = mutableSetOf<String>()
    private var lastPayload = ""
    private var totalProfiles = 0

    fun start() {
        scope.launch {
            try {
                val plan = CoreConfigManager.getV2rayConfig4BatchSpeedtest(context, guids)
                totalProfiles = (plan.profiles.map { it.guid } + plan.failedGuids).distinct().size
                plan.failedGuids.forEach { emitResult(it, -1L, completed = true) }

                if (plan.probeGroups.isNotEmpty()) {
                    val concurrency = SettingsManager.getRealPingConcurrency()
                    LogUtil.i(
                        AppConfig.TAG,
                        "Starting ${plan.profiles.size} real-delay profiles with concurrency $concurrency",
                    )
                    val finalResult = CoreNativeManager.probeOutboundGroups(
                        controller = controller,
                        config = plan.content,
                        outboundGroupsJson = JsonUtil.toJson(plan.probeGroups),
                        balancerTagsJson = JsonUtil.toJson(plan.balancerTags),
                        maxConcurrency = concurrency,
                        samples = plan.samples,
                        handler = object : OutboundProbeHandler {
                            override fun onOutboundProbeUpdate(payload: String?): Long {
                                payload?.let { processPayload(plan, it) }
                                return 0
                            }
                        },
                    )
                    if (finished.get()) return@launch
                    processSnapshot(plan, finalResult)
                    completeMissing(plan)
                    finish(if (finalResult.cancelled) "-1" else "0")
                } else {
                    completeMissing(plan)
                    finish("0")
                }
            } catch (_: CancellationException) {
                finish("-1")
            } catch (error: Throwable) {
                LogUtil.e(AppConfig.TAG, "Outbound probe batch failed", error)
                finish("-1")
            }
        }
    }

    fun cancel() {
        controller.cancel()
        job.cancel()
        finish("-1")
    }

    @Synchronized
    private fun processPayload(plan: OutboundProbePlan, payload: String) {
        if (finished.get()) return
        if (payload == lastPayload) return
        lastPayload = payload
        val snapshot = JsonUtil.fromJsonSafe(payload, CoreNativeManager.OutboundProbeBatchResult::class.java)
            ?: return
        processSnapshot(plan, snapshot)
    }

    @Synchronized
    private fun processSnapshot(
        plan: OutboundProbePlan,
        snapshot: CoreNativeManager.OutboundProbeBatchResult,
    ) {
        if (finished.get()) return
        val statuses = snapshot.results.associateBy { it.outboundTag }
        plan.profiles.forEach { profile ->
            val candidateStatuses = profile.candidates.mapNotNull { candidate ->
                statuses[candidate.probeTag]?.let { candidate to it }
            }
            val allCandidatesCompleted = candidateStatuses.size == profile.candidates.size &&
                candidateStatuses.all { (_, status) -> status.samples >= plan.samples }

            val selected = selectProfileResult(profile, snapshot, candidateStatuses)
            if (selected != null) {
                val (candidate, status) = selected
                val viableRoute = candidate.runtimeTag.takeIf {
                    status.alive && profile.balancerTag != null
                }.orEmpty()
                emitResult(
                    profile.guid,
                    if (status.alive) status.delay else -1L,
                    viableRoute,
                    allCandidatesCompleted,
                )
            } else if (allCandidatesCompleted) {
                emitResult(profile.guid, -1L, completed = true)
            }
        }
    }

    private fun selectProfileResult(
        profile: OutboundProbeProfilePlan,
        snapshot: CoreNativeManager.OutboundProbeBatchResult,
        statuses: List<Pair<com.v2ray.ang.dto.OutboundProbeCandidate, CoreNativeManager.OutboundProbeStatus>>,
    ): Pair<com.v2ray.ang.dto.OutboundProbeCandidate, CoreNativeManager.OutboundProbeStatus>? {
        if (profile.balancerTag == null) return statuses.firstOrNull()
        val selectedTag = snapshot.balancerTargets[profile.balancerTag] ?: return null
        return statuses.firstOrNull { (candidate, _) -> candidate.probeTag == selectedTag }
    }

    @Synchronized
    private fun completeMissing(plan: OutboundProbePlan) {
        (plan.profiles.map { it.guid } + plan.failedGuids).distinct().forEach { guid ->
            if (guid !in completedGuids) emitResult(guid, emittedDelays[guid] ?: -1L, completed = true)
        }
    }

    @Synchronized
    private fun emitResult(
        guid: String,
        delay: Long,
        viableOutboundTag: String = "",
        completed: Boolean,
    ) {
        if (emittedDelays[guid] != delay ||
            (viableOutboundTag.isNotEmpty() && emittedRoutes[guid] != viableOutboundTag)
        ) {
            emittedDelays[guid] = delay
            if (viableOutboundTag.isNotEmpty()) emittedRoutes[guid] = viableOutboundTag
            onEvent(
                RealPingEvent.Result(
                    guid = guid,
                    delayMillis = delay,
                    viableOutboundTag = viableOutboundTag,
                    networkKey = networkIdentity?.key,
                    networkHandle = networkIdentity?.networkHandle,
                )
            )
        }
        if (completed && completedGuids.add(guid)) {
            val remaining = (totalProfiles - completedGuids.size).coerceAtLeast(0)
            onEvent(RealPingEvent.Progress("$remaining / $totalProfiles"))
        }
    }

    @Synchronized
    private fun finish(status: String) {
        if (finished.compareAndSet(false, true)) {
            onEvent(RealPingEvent.Finish(status))
        }
    }
}
