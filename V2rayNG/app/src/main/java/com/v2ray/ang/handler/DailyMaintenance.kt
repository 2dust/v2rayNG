package com.v2ray.ang.handler

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.service.DailyMaintenanceWorker
import java.util.concurrent.TimeUnit

/**
 * Keeps the app current on its own: one daily job that looks for a new build of the engine, refreshes
 * the subscriptions that ask for it and tidies the local data.
 *
 * The job is periodic WorkManager work, so it survives reboots and does not keep a process alive.
 * Everything it needs beyond the network (routing rules, cached groups) is already inside the app,
 * so the offline part also runs on a device that never sees Wi-Fi.
 */
object DailyMaintenance {

    fun isEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DAILY_MAINTENANCE, true)

    /** Re-applies the schedule after the preference changed. */
    fun sync(context: Context = AngApplication.application) {
        if (isEnabled()) schedule(context) else cancel(context)
    }

    fun schedule(context: Context = AngApplication.application) {
        val request = PeriodicWorkRequestBuilder<DailyMaintenanceWorker>(1, TimeUnit.DAYS)
            .addTag(AppConfig.DAILY_MAINTENANCE_WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AppConfig.DAILY_MAINTENANCE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context = AngApplication.application) {
        WorkManager.getInstance(context)
            .cancelUniqueWork(AppConfig.DAILY_MAINTENANCE_WORK_NAME)
    }

    /** Runs the whole job once, now; used by the settings entry so the behaviour can be checked. */
    fun runNow(context: Context = AngApplication.application) {
        val request = OneTimeWorkRequestBuilder<DailyMaintenanceWorker>()
            .addTag(AppConfig.DAILY_MAINTENANCE_WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
