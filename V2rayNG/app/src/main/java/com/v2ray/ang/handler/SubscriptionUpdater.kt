package com.v2ray.ang.handler

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import androidx.work.workDataOf
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.SubscriptionCache
import com.v2ray.ang.util.LogUtil
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
                shouldRun = sub.subscription.autoUpdate,
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
        val subItem = MmkvManager.decodeSubscription(subId) ?: return
        scheduleOne(
            context = context,
            subId = subId,
            shouldRun = subItem.autoUpdate,
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

    // -------------------------------------------------------------------------
    // Internal scheduling logic
    // -------------------------------------------------------------------------

    private fun taskName(subId: String) = "${AppConfig.SUBSCRIPTION_UPDATE_TASK_NAME}_$subId"

    private fun scheduleOne(
        context: Context,
        subId: String,
        shouldRun: Boolean,
        existingWorkPolicy: ExistingPeriodicWorkPolicy
    ) {
        val rw = RemoteWorkManager.getInstance(context)
        if (!shouldRun) {
            rw.cancelUniqueWork(taskName(subId))
            LogUtil.d(AppConfig.TAG, "SubscriptionUpdater: cancelled task for $subId")
            return
        }

        val subItem = MmkvManager.decodeSubscription(subId) ?: return

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
            "SubscriptionUpdater: scheduled [$subId] interval=${intervalMinutes}min " +
                "initialDelay=${initialDelayMillis / 1000}s policy=$existingWorkPolicy"
        )
    }

    // -------------------------------------------------------------------------
    // Worker
    // -------------------------------------------------------------------------

    private const val KEY_SUB_ID = "subId"

    class UpdateTask(context: Context, params: WorkerParameters) :
        CoroutineWorker(context, params) {

        private val notificationManager = NotificationManagerCompat.from(applicationContext)
        private val notification =
            NotificationCompat.Builder(applicationContext, AppConfig.SUBSCRIPTION_UPDATE_CHANNEL)
                .setWhen(0)
                .setTicker("Update")
                .setContentTitle(applicationContext.getString(R.string.title_pref_auto_update_subscription))
                .setSmallIcon(R.drawable.ic_stat_name)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        @SuppressLint("MissingPermission")
        override suspend fun doWork(): Result {
            val subId = inputData.getString(KEY_SUB_ID)
            LogUtil.i(AppConfig.TAG, "SubscriptionUpdater automatic update starting: $subId")

            if (subId.isNullOrEmpty()) {
                LogUtil.w(AppConfig.TAG, "SubscriptionUpdater: missing subId in worker input")
                return Result.success()
            }


            val subItem = MmkvManager.decodeSubscription(subId)
            if (subItem == null) {
                LogUtil.w(AppConfig.TAG, "SubscriptionUpdater: no subscription found for $subId")
                return Result.success()
            }

            if (!subItem.autoUpdate) {
                LogUtil.i(AppConfig.TAG, "SubscriptionUpdater: auto-update disabled for $subId, skip")
                return Result.success()
            }

            val sub = SubscriptionCache(subId, subItem)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager.createNotificationChannel(
                    NotificationChannel(
                        AppConfig.SUBSCRIPTION_UPDATE_CHANNEL,
                        AppConfig.SUBSCRIPTION_UPDATE_CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_MIN
                    )
                )
            }

            notificationManager.notify(3, notification.build())
            LogUtil.i(AppConfig.TAG, "SubscriptionUpdater automatic update: ---${sub.subscription.remarks}")
            AngConfigManager.updateConfigViaSub(sub)
            notification.setContentText("Updating ${sub.subscription.remarks}")

            notificationManager.cancel(3)
            return Result.success()
        }
    }
}