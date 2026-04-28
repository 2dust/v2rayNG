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
     * Call from: MainActivity.onCreate(), BootReceiver.onReceive(),
     *            global auto-update toggle, global interval change.
     */
    fun sync(context: Context = AngApplication.application) {
        val globalEnabled = MmkvManager.decodeSettingsBool(AppConfig.SUBSCRIPTION_AUTO_UPDATE, false)
        if (!globalEnabled) {
            cancelAll(context)
            LogUtil.i(AppConfig.TAG, "SubscriptionUpdater: global switch OFF, all tasks cancelled")
            return
        }
        MmkvManager.decodeSubscriptions().forEach { sub ->
            scheduleOne(context, sub.guid, sub.subscription.autoUpdate)
        }
        LogUtil.i(AppConfig.TAG, "SubscriptionUpdater: sync complete")
    }

    /**
     * Sync a single subscription's task.
     * Call from: SubEditActivity after saving, after a manual update (to reset the timer).
     */
    fun syncOne(context: Context = AngApplication.application, subId: String) {
        val globalEnabled = MmkvManager.decodeSettingsBool(AppConfig.SUBSCRIPTION_AUTO_UPDATE, false)
        val subItem = MmkvManager.decodeSubscription(subId) ?: return
        scheduleOne(context, subId, globalEnabled && subItem.autoUpdate)
    }

    /**
     * Cancel all auto-update tasks.
     * Normally called internally by sync(); exposed for edge cases.
     */
    fun cancelAll(context: Context = AngApplication.application) {
        RemoteWorkManager.getInstance(context)
            .cancelAllWorkByTag(AppConfig.SUBSCRIPTION_UPDATE_TASK_NAME)
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

    private fun scheduleOne(context: Context, subId: String, shouldRun: Boolean) {
        val rw = RemoteWorkManager.getInstance(context)
        if (!shouldRun) {
            rw.cancelUniqueWork(taskName(subId))
            LogUtil.d(AppConfig.TAG, "SubscriptionUpdater: cancelled task for $subId")
            return
        }

        val subItem = MmkvManager.decodeSubscription(subId) ?: return

        val globalDefault = MmkvManager.decodeSettingsString(
            AppConfig.SUBSCRIPTION_AUTO_UPDATE_INTERVAL,
            AppConfig.SUBSCRIPTION_DEFAULT_UPDATE_INTERVAL
        )?.toLongOrNull() ?: AppConfig.SUBSCRIPTION_DEFAULT_UPDATE_INTERVAL.toLong()

        val intervalMinutes = maxOf(
            AppConfig.SUBSCRIPTION_MIN_INTERVAL_MINUTES,
            subItem.updateInterval?.toLong() ?: globalDefault
        )

        // Smart initialDelay: avoid a burst of updates immediately after reboot/reinstall
        val lastAttempt = MmkvManager.decodeSubLastAttempt(subId)
        val intervalMillis = intervalMinutes * 60 * 1000L
        val now = System.currentTimeMillis()
        val initialDelayMillis = if (lastAttempt <= 0L) {
            0L
        } else {
            maxOf(0L, lastAttempt + intervalMillis - now)
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
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )

        LogUtil.i(
            AppConfig.TAG,
            "SubscriptionUpdater: scheduled [$subId] interval=${intervalMinutes}min " +
                "initialDelay=${initialDelayMillis / 1000}s"
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
            LogUtil.i(AppConfig.TAG, "subscription automatic update starting: $subId")

            if (!subId.isNullOrEmpty()) {
                MmkvManager.encodeSubLastAttempt(subId, System.currentTimeMillis())
            }

            val subs = if (!subId.isNullOrEmpty()) {
                MmkvManager.decodeSubscription(subId)
                    ?.let { listOf(SubscriptionCache(subId, it)) }
                    ?: emptyList()
            } else {
                // Fallback: legacy global task compatibility
                MmkvManager.decodeSubscriptions().filter { it.subscription.autoUpdate }
            }

            if (subs.isEmpty()) {
                LogUtil.w(AppConfig.TAG, "SubscriptionUpdater: no subscription found for $subId")
                return Result.success()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager.createNotificationChannel(
                    NotificationChannel(
                        AppConfig.SUBSCRIPTION_UPDATE_CHANNEL,
                        AppConfig.SUBSCRIPTION_UPDATE_CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_MIN
                    )
                )
            }

            for (sub in subs) {
                notificationManager.notify(3, notification.build())
                LogUtil.i(AppConfig.TAG, "subscription automatic update: ---${sub.subscription.remarks}")
                AngConfigManager.updateConfigViaSub(sub)
                notification.setContentText("Updating ${sub.subscription.remarks}")
            }

            notificationManager.cancel(3)
            return Result.success()
        }
    }
}