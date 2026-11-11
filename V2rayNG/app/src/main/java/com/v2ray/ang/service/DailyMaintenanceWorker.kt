package com.v2ray.ang.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.SubscriptionUpdateMessage
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.handler.CloudflareAssistant
import com.v2ray.ang.handler.DailyMaintenance
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.handler.UpdateCheckerManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The daily maintenance pass.
 *
 * 1. Engine: the Xray core travels inside the APK, so a new build *is* the engine update. When one
 *    exists it is downloaded in the background and an install notification is posted; the actual
 *    install stays a single tap the user owns, because Android never lets an app install silently.
 * 2. Configs: subscriptions that are marked for automatic updates are refreshed.
 * 3. Local: orphaned profiles are removed and the cached group tabs are rebuilt so the next launch
 *    starts from consistent data. This part needs no network at all.
 *
 * Every phase is independent and swallows its own failures: a flaky network must not turn into a
 * retry storm, so the worker always reports success.
 */
class DailyMaintenanceWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!DailyMaintenance.isEnabled()) {
            LogUtil.i(AppConfig.TAG, "Daily maintenance is disabled, skipping")
            return@withContext Result.success()
        }

        LogUtil.i(AppConfig.TAG, "Daily maintenance started")

        runCatching { updateEngineIfAvailable() }
            .onFailure { LogUtil.w(AppConfig.TAG, "Daily maintenance: engine update failed", it) }

        runCatching { refreshSubscriptions() }
            .onFailure { LogUtil.w(AppConfig.TAG, "Daily maintenance: subscription refresh failed", it) }

        runCatching { repairCloudflareConfig() }
            .onFailure { LogUtil.w(AppConfig.TAG, "Daily maintenance: Cloudflare repair failed", it) }

        runCatching { tidyLocalData() }
            .onFailure { LogUtil.w(AppConfig.TAG, "Daily maintenance: local tidy-up failed", it) }

        LogUtil.i(AppConfig.TAG, "Daily maintenance finished")
        Result.success()
    }

    // -------------------------------------------------------------------------
    // 1. Engine / app update
    // -------------------------------------------------------------------------

    private suspend fun updateEngineIfAvailable() {
        if (!isOnline()) return

        val result = UpdateCheckerManager.checkForUpdate(includePreRelease = false)
        val url = result.downloadUrl
        val version = result.latestVersion
        if (!result.hasUpdate || url.isNullOrEmpty() || version.isNullOrEmpty()) return

        val updateDir = File(applicationContext.cacheDir, "update").apply { mkdirs() }
        val target = File(updateDir, "RX-VPN-$version.apk")
        // Keep a download from an earlier pass instead of fetching the same megabytes again.
        if (target.length() <= 0) {
            val downloaded = HttpUtil.downloadToFile(
                UrlContentRequest(url = url, timeout = 60_000),
                target
            )
            if (!downloaded) {
                target.delete()
                LogUtil.w(AppConfig.TAG, "Daily maintenance: update download failed")
                return
            }
        }

        // Older builds accumulate in the cache; keep only the one that is being offered.
        updateDir.listFiles()
            ?.filter { it != target }
            ?.forEach { it.delete() }

        notifyUpdateReady(target, version)
    }

    private fun notifyUpdateReady(apk: File, version: String) {
        val context = applicationContext
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            LogUtil.i(AppConfig.TAG, "Daily maintenance: notifications are off, update stays cached")
            return
        }

        ensureChannel(context)

        val uri = FileProvider.getUriForFile(
            context,
            BuildConfig.APPLICATION_ID + ".cache",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(context.getString(R.string.daily_update_title))
            .setContentText(context.getString(R.string.daily_update_text, version))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            LogUtil.w(AppConfig.TAG, "Daily maintenance: could not post the update notification", e)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        if (system.getNotificationChannel(CHANNEL_ID) != null) return
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_daily_maintenance),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    // -------------------------------------------------------------------------
    // 2. Configs
    // -------------------------------------------------------------------------

    private fun refreshSubscriptions() {
        if (!isOnline()) return

        val subIds = MmkvManager.decodeSubscriptions()
            .filter { it.subscription.autoUpdate && it.subscription.url.isNotEmpty() }
            .map { it.guid }
        if (subIds.isEmpty()) return

        // Same path the per-subscription periodic worker uses, so the update, the notification and
        // the "last updated" bookkeeping stay in one place.
        subIds.forEach { SubscriptionUpdater.updateLastUpdatedAndReschedule(applicationContext, it) }
        MessageHelper.sendMsg2SubscriptionService(
            applicationContext,
            SubscriptionUpdateMessage(AppConfig.MSG_SUB_UPDATE_START, true, subIds)
        )
        LogUtil.i(AppConfig.TAG, "Daily maintenance: refreshing ${subIds.size} subscription(s)")
    }

    // -------------------------------------------------------------------------
    // 3. Local, no network needed
    // -------------------------------------------------------------------------

    /** Repairs the selected Cloudflare config when it stopped answering; no-op otherwise. */
    private suspend fun repairCloudflareConfig() {
        if (!CloudflareAssistant.isAutoEnabled()) return
        val report = CloudflareAssistant.autoRepair(
            context = applicationContext,
            groupName = applicationContext.getString(R.string.cloudflare_group_name),
            remarksSuffix = applicationContext.getString(R.string.cloudflare_copy_suffix),
        )
        if (!report.isCloudflare) return
        // A repair leaves the user's own profile alone and writes a Cloudflare copy, so the tab has
        // to be rebuilt for the new entry to show up.
        if (report.newConfigGuid != null) SettingsChangeManager.makeSetupGroupTab()
        LogUtil.i(
            AppConfig.TAG,
            "Daily maintenance: Cloudflare latency=${report.latencyMs} " +
                    "unblocked=${report.unblocked} address=${report.switchedAddress} " +
                    "copy=${report.newConfigName}"
        )
    }

    private fun tidyLocalData() {
        MmkvManager.removeOrphanedServerProfiles()
        // Rebuild the cached group tabs so the home screen opens on consistent data.
        com.v2ray.ang.handler.SettingsChangeManager.makeSetupGroupTab()
    }

    private fun isOnline(): Boolean {
        val manager = applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
        const val CHANNEL_ID = "daily_maintenance_channel"
        const val NOTIFICATION_ID = 21
    }
}
