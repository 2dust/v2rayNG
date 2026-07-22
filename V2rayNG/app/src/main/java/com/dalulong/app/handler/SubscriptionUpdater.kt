package com.dalulong.app.handler

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import androidx.work.workDataOf
import com.dalulong.app.AngApplication
import com.dalulong.app.AppConfig
import com.dalulong.app.R
import com.dalulong.app.core.CoreNativeManager
import com.dalulong.app.dto.RealPingEvent
import com.dalulong.app.dto.entities.SubscriptionCache
import com.dalulong.app.enums.NotificationChannelType
import com.dalulong.app.service.RealPingWorkerService
import com.dalulong.app.util.LogUtil
import com.dalulong.app.util.NotificationHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.TimeUnit

object SubscriptionUpdater {

    // -------------------------------------------------------------------------
    // Public API — the only methods external callers should ever use
    // -------------------------------------------------------------------------

    /**
     * Sync all subscription tasks with current settings.
     *
     * Startup/boot callers should use the default mode so existing periodic work is kept.
     * Use forceReschedule=true only when the next run time needs to be recalculated from
     * the latest persisted subscription state (for example after a manual refresh).
     * Call from: MainActivity.onCreate(), BootReceiver.onReceive().
     */
    fun sync(
        context: Context = AngApplication.application,
        forceReschedule: Boolean = false
    ) {
        val existingWorkPolicy =
            if (forceReschedule) {
                ExistingPeriodicWorkPolicy.REPLACE
            } else {
                ExistingPeriodicWorkPolicy.KEEP
            }

        MmkvManager.decodeSubscriptions().forEach { sub ->
            scheduleOne(
                context = context,
                subId = sub.guid,
                existingWorkPolicy = existingWorkPolicy
            )
        }
        LogUtil.i(
            AppConfig.TAG,
            "SubscriptionUpdater: sync complete forceReschedule=$forceReschedule"
        )
    }

    /**
     * Sync a single subscription's task.
     * Call from: SubEditActivity after saving, after a manual update (to reset the timer).
     */
    fun syncOne(context: Context = AngApplication.application, subId: String) {
        scheduleOne(
            context = context,
            subId = subId,
            existingWorkPolicy = ExistingPeriodicWorkPolicy.REPLACE
        )
    }

    /**
     * Cancel the auto-update task for a single subscription.
     * Call from: when a subscription is deleted.
     */
    fun cancelOne(context: Context = AngApplication.application, subId: String) {
        RemoteWorkManager.getInstance(context)
            .cancelUniqueWork(taskName(subId))
    }

    /**
     * Update all subscriptions immediately in the background.
     * Call from: SubscriptionsViewModel.updateSubscriptions().
     */
    fun updateAllByManual(context: Context = AngApplication.application) {
        LogUtil.i(AppConfig.TAG, "SubscriptionUpdater: manual updateAll triggered")
        val rw = RemoteWorkManager.getInstance(context)

        MmkvManager.decodeSubscriptions().forEach { sub ->
            if (!sub.subscription.enabled || sub.subscription.url.isEmpty()) {
                return@forEach
            }
            val subId = sub.guid
            val request = OneTimeWorkRequestBuilder<UpdateTask>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInputData(
                    workDataOf(
                        KEY_SUB_ID to subId,
                    )
                )
                .addTag(AppConfig.SUBSCRIPTION_UPDATE_TASK_NAME)
                .build()

            rw.enqueueUniqueWork(
                "${taskName(subId)}_manual",
                ExistingWorkPolicy.KEEP,
                request
            )
            LogUtil.d(AppConfig.TAG, "SubscriptionUpdater: enqueued manual task for ${sub.subscription.remarks} ($subId)")
        }
    }

    // -------------------------------------------------------------------------
    // Internal scheduling logic
    // -------------------------------------------------------------------------

    private val updateSemaphore = Semaphore(2)

    private fun taskName(subId: String) = "${AppConfig.SUBSCRIPTION_UPDATE_TASK_NAME}_$subId"

    private fun scheduleOne(
        context: Context,
        subId: String,
        existingWorkPolicy: ExistingPeriodicWorkPolicy
    ) {
        val subItem = MmkvManager.decodeSubscription(subId) ?: return
        val rw = RemoteWorkManager.getInstance(context)
        if (!subItem.autoUpdate) {
            cancelOne(context, subId)
            LogUtil.d(AppConfig.TAG, "SubscriptionUpdater: cancelled task for ${subItem.remarks}")
            return
        }

        val intervalMinutes = maxOf(
            AppConfig.SUBSCRIPTION_MIN_INTERVAL_MINUTES,
            subItem.updateInterval
        )

        // Base initial delay on the last successful update time persisted in subscription.
        val lastUpdated = subItem.lastUpdated
        val intervalMillis = intervalMinutes * 60 * 1000L
        val now = System.currentTimeMillis()
        val initialDelayMillis = if (lastUpdated <= 0L) {
            0L
        } else {
            maxOf(0L, lastUpdated + intervalMillis - now)
        }

        val request = PeriodicWorkRequestBuilder<UpdateTask>(intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(workDataOf(KEY_SUB_ID to subId))
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .addTag(AppConfig.SUBSCRIPTION_UPDATE_TASK_NAME)
            .build()

        rw.enqueueUniquePeriodicWork(
            taskName(subId),
            existingWorkPolicy,
            request
        )

        LogUtil.i(
            AppConfig.TAG,
            "SubscriptionUpdater: scheduled [${subItem.remarks}] interval=${intervalMinutes}min " +
                    "initialDelay=${initialDelayMillis / 1000}s policy=$existingWorkPolicy"
        )
    }

    // -------------------------------------------------------------------------
    // Worker
    // -------------------------------------------------------------------------

    private const val KEY_SUB_ID = "subId"

    class UpdateTask(context: Context, params: WorkerParameters) :
        CoroutineWorker(context, params) {

        @SuppressLint("MissingPermission")
        override suspend fun doWork(): Result = updateSemaphore.withPermit {
            val subId = inputData.getString(KEY_SUB_ID)
            LogUtil.i(AppConfig.TAG, "SubscriptionUpdater update starting: $subId")

            if (subId.isNullOrEmpty()) {
                LogUtil.w(AppConfig.TAG, "SubscriptionUpdater: missing subId in worker input")
                return Result.success()
            }

            val subItem = MmkvManager.decodeSubscription(subId)
            if (subItem == null) {
                LogUtil.w(AppConfig.TAG, "SubscriptionUpdater: no subscription found for $subId")
                return Result.success()
            }

            if (subItem.url.isEmpty()) {
                LogUtil.i(AppConfig.TAG, "SubscriptionUpdater: url isEmpty for ${subItem.remarks}, skip")
                return Result.success()
            }

            val sub = SubscriptionCache(subId, subItem)

            // Notify about update start
            showNotification(
                applicationContext,
                R.string.title_pref_auto_update_subscription,
                "Updating ${sub.subscription.remarks}"
            )

            LogUtil.i(AppConfig.TAG, "SubscriptionUpdater update: ---${subItem.remarks}")
            AngConfigManager.updateConfigViaSub(sub)

            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_TEST_AFTER_UPDATE_SUBSCRIPTION, false)) {
                testSubscriptionServers(applicationContext, sub)

                if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST, false)) {
                    LogUtil.i(AppConfig.TAG, "SubscriptionUpdater: removing invalid servers for ${subItem.remarks}")
                    showNotification(
                        applicationContext,
                        R.string.title_del_invalid_config,
                        sub.subscription.remarks
                    )
                    AngConfigManager.removeInvalidServer(subId)
                }
                if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SORT_AFTER_TEST, false)) {
                    LogUtil.i(AppConfig.TAG, "SubscriptionUpdater: sorting servers for ${subItem.remarks}")
                    showNotification(
                        applicationContext,
                        R.string.title_sort_by_test_results,
                        sub.subscription.remarks
                    )
                    AngConfigManager.sortByTestResultsForSub(subId)
                }
            }

            // Clear notification
            NotificationHelper.cancel(NotificationChannelType.SUBSCRIPTION_UPDATE, applicationContext)

            // Reset periodic task timer to align with this successful update
            syncOne(applicationContext, subId)

            LogUtil.i(AppConfig.TAG, "SubscriptionUpdater update finished and rescheduled: ${subItem.remarks}")

            return Result.success()
        }
    }

    private suspend fun testSubscriptionServers(context: Context, sub: SubscriptionCache) {
        val subId = sub.guid
        LogUtil.i(AppConfig.TAG, "SubscriptionUpdater: starting test phase for ${sub.subscription.remarks}")
        showNotification(
            context,
            R.string.title_real_ping_all_server,
            sub.subscription.remarks
        )
        CoreNativeManager.initCoreEnv(context)
        val guids = MmkvManager.decodeServerList(subId)
        if (guids.isNotEmpty()) {
            val deferred = CompletableDeferred<Unit>()
            val worker = RealPingWorkerService(
                context = context,
                guids = guids,
                onEvent = { event ->
                    when (event) {
                        is RealPingEvent.Progress -> {
                            showNotification(
                                context,
                                R.string.title_real_ping_all_server,
                                "${event.text} in ${sub.subscription.remarks}"
                            )
                            LogUtil.i(AppConfig.TAG, "SubscriptionUpdater: ${event.text} in ${sub.subscription.remarks}")
                        }

                        is RealPingEvent.Result -> {
                            MmkvManager.encodeServerTestDelayMillis(event.guid, event.delayMillis)
                        }

                        is RealPingEvent.Finish -> {
                            deferred.complete(Unit)
                        }
                    }
                }
            )
            worker.start()
            deferred.await()
            LogUtil.i(AppConfig.TAG, "SubscriptionUpdater: test phase finished for ${sub.subscription.remarks}")
        }
    }

    private fun showNotification(context: Context, titleResId: Int, content: String) {
        NotificationHelper.notify(
            NotificationChannelType.SUBSCRIPTION_UPDATE,
            context,
            context.getString(titleResId),
            content
        )
    }
}
