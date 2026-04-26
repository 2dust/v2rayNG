package com.v2ray.ang.handler

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import androidx.work.multiprocess.RemoteWorkManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.SubscriptionCache
import com.v2ray.ang.util.LogUtil
import java.util.concurrent.TimeUnit

object SubscriptionUpdater {

    fun scheduleAllTasks(context: Context) {
        val enabled = MmkvManager.decodeSettingsBool(AppConfig.SUBSCRIPTION_AUTO_UPDATE, false)
        if (!enabled) {
            cancelAllTasks(context)
            return
        }

        MmkvManager.decodeSubscriptions().forEach { sub ->
            if (sub.subscription.autoUpdate) {
                scheduleTask(context, sub.guid)
            }
        }
    }

    fun scheduleTask(context: Context, subId: String) {
        val globalEnabled = MmkvManager.decodeSettingsBool(AppConfig.SUBSCRIPTION_AUTO_UPDATE, false)
        val subItem = MmkvManager.decodeSubscription(subId)

        if (!globalEnabled || subItem == null || !subItem.autoUpdate) {
            cancelTask(context, subId)
            return
        }

        var intervalMinutes = subItem.updateInterval ?: MmkvManager.decodeSettingsString(
            AppConfig.SUBSCRIPTION_AUTO_UPDATE_INTERVAL,
            AppConfig.SUBSCRIPTION_DEFAULT_UPDATE_INTERVAL
        ).orEmpty().toIntOrNull() ?: AppConfig.SUBSCRIPTION_DEFAULT_UPDATE_INTERVAL.toInt()

        if (intervalMinutes < 15) {
            intervalMinutes = 15
        }

        val rw = RemoteWorkManager.getInstance(context)

        // Calculate initial delay for smart rescheduling
        val currentTime = System.currentTimeMillis()
        val lastAttempt = subItem.lastUpdateAttempt
        val intervalMillis = intervalMinutes.toLong() * 60 * 1000
        
        val initialDelayMillis = if (lastAttempt <= 0) {
            0L // Never tried before, run immediately
        } else {
            val nextExpectedRun = lastAttempt + intervalMillis
            Math.max(0L, nextExpectedRun - currentTime)
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val data = Data.Builder()
            .putString("subId", subId)
            .build()

        val request = PeriodicWorkRequest.Builder(UpdateTask::class.java, intervalMinutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInputData(data)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .addTag(AppConfig.SUBSCRIPTION_UPDATE_TASK_NAME)
            .build()

        // Always use REPLACE to ensure our calculated initialDelay is applied.
        // This handles app launch (smart delay), manual update (timer reset), and global toggle.
        rw.enqueueUniquePeriodicWork(
            "${AppConfig.SUBSCRIPTION_UPDATE_TASK_NAME}_$subId",
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )

        // Cancel legacy global task if it exists
        rw.cancelUniqueWork(AppConfig.SUBSCRIPTION_UPDATE_TASK_NAME)
    }

    fun cancelAllTasks(context: Context) {
        val rw = RemoteWorkManager.getInstance(context)
        rw.cancelAllWorkByTag(AppConfig.SUBSCRIPTION_UPDATE_TASK_NAME)
    }

    fun cancelTask(context: Context, subId: String) {
        val rw = RemoteWorkManager.getInstance(context)
        rw.cancelUniqueWork("${AppConfig.SUBSCRIPTION_UPDATE_TASK_NAME}_$subId")
    }

    class UpdateTask(context: Context, params: WorkerParameters) :
        CoroutineWorker(context, params) {

        private val notificationManager = NotificationManagerCompat.from(applicationContext)
        private val notification =
            NotificationCompat.Builder(applicationContext, AppConfig.SUBSCRIPTION_UPDATE_CHANNEL)
                .setWhen(0)
                .setTicker("Update")
                .setContentTitle(context.getString(R.string.title_pref_auto_update_subscription))
                .setSmallIcon(R.drawable.ic_stat_name)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        /**
         * Performs the subscription update work.
         * @return The result of the work.
         */
        @SuppressLint("MissingPermission")
        override suspend fun doWork(): Result {
            val subId = inputData.getString("subId")
            LogUtil.i(AppConfig.TAG, "subscription automatic update starting: $subId")

            val subs = if (subId.isNullOrEmpty()) {
                MmkvManager.decodeSubscriptions().filter { it.subscription.autoUpdate }
            } else {
                MmkvManager.decodeSubscription(subId)?.let {
                    listOf(SubscriptionCache(subId, it))
                } ?: emptyList()
            }

            for (sub in subs) {
                val subItem = sub.subscription

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    notification.setChannelId(AppConfig.SUBSCRIPTION_UPDATE_CHANNEL)
                    val channel =
                        NotificationChannel(
                            AppConfig.SUBSCRIPTION_UPDATE_CHANNEL,
                            AppConfig.SUBSCRIPTION_UPDATE_CHANNEL_NAME,
                            NotificationManager.IMPORTANCE_MIN
                        )
                    notificationManager.createNotificationChannel(channel)
                }
                notificationManager.notify(3, notification.build())
                LogUtil.i(AppConfig.TAG, "subscription automatic update: ---${subItem.remarks}")
                AngConfigManager.updateConfigViaSub(sub)
                notification.setContentText("Updating ${subItem.remarks}")
            }
            notificationManager.cancel(3)
            return Result.success()
        }
    }
}
