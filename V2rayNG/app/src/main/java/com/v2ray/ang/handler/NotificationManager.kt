package com.v2ray.ang.handler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.ui.MainActivity
import com.v2ray.ang.util.MessageUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

object NotificationManager {
    private const val NOTIFICATION_ID = 1
    private const val NOTIFICATION_PENDING_INTENT_CONTENT = 0
    private const val NOTIFICATION_PENDING_INTENT_STOP_V2RAY = 1
    private const val NOTIFICATION_PENDING_INTENT_RESTART_V2RAY = 2
    private const val NOTIFICATION_ICON_THRESHOLD = 3000

    private var lastQueryTime = 0L
    private var mBuilder: NotificationCompat.Builder? = null
    private var speedNotificationJob: Job? = null
    private var downloadTrackingJob: Job? = null
    private var mNotificationManager: NotificationManager? = null
    private var sessionStartDownload = 0L
    private var currentConfigGuid: String? = null
    private var downloadUpdateCounter = 0

    /**
     * Starts the speed notification.
     * @param currentConfig The current profile configuration.
     */
    fun startSpeedNotification(currentConfig: ProfileItem?) {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED) != true) return
        if (speedNotificationJob != null || V2RayServiceManager.isRunning() == false) return

        lastQueryTime = System.currentTimeMillis()
        sessionStartDownload = 0L
        currentConfigGuid = MmkvManager.getSelectServer()
        downloadUpdateCounter = 0 // Reset counter when starting speed notification
        var lastZeroSpeed = false
        val outboundTags = currentConfig?.getAllOutboundTags()
        outboundTags?.remove(AppConfig.TAG_DIRECT)

        speedNotificationJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val queryTime = System.currentTimeMillis()
                val sinceLastQueryInSeconds = (queryTime - lastQueryTime) / 1000.0
                var proxyTotal = 0L
                var totalDownloadThisQuery = 0L
                val text = StringBuilder()
                outboundTags?.forEach {
                    val up = V2RayServiceManager.queryStats(it, AppConfig.UPLINK)
                    val down = V2RayServiceManager.queryStats(it, AppConfig.DOWNLINK)
                    totalDownloadThisQuery += down
                    if (up + down > 0) {
                        appendSpeedString(text, it, up / sinceLastQueryInSeconds, down / sinceLastQueryInSeconds)
                        proxyTotal += up + down
                    }
                }
                val directUplink = V2RayServiceManager.queryStats(AppConfig.TAG_DIRECT, AppConfig.UPLINK)
                val directDownlink = V2RayServiceManager.queryStats(AppConfig.TAG_DIRECT, AppConfig.DOWNLINK)
                totalDownloadThisQuery += directDownlink

                // Update total download for current config
                sessionStartDownload += totalDownloadThisQuery
                updateTotalDownload(currentConfigGuid, totalDownloadThisQuery)

                val zeroSpeed = proxyTotal == 0L && directUplink == 0L && directDownlink == 0L
                if (!zeroSpeed || !lastZeroSpeed) {
                    if (proxyTotal == 0L) {
                        appendSpeedString(text, outboundTags?.firstOrNull(), 0.0, 0.0)
                    }
                    appendSpeedString(
                        text, AppConfig.TAG_DIRECT, directUplink / sinceLastQueryInSeconds,
                        directDownlink / sinceLastQueryInSeconds
                    )
                    updateNotification(text.toString(), proxyTotal, directDownlink + directUplink)
                }
                lastZeroSpeed = zeroSpeed
                lastQueryTime = queryTime
                delay(3000)
            }
        }
    }

    /**
     * Updates the total download bytes for a config.
     * @param guid The GUID of the config.
     * @param downloadBytes The bytes downloaded in this query.
     */
    private fun updateTotalDownload(guid: String?, downloadBytes: Long) {
        if (guid == null || downloadBytes == 0L) {
            android.util.Log.i(AppConfig.TAG, "DownloadTracking: Skipping update - guid=$guid, bytes=$downloadBytes")
            return
        }

        android.util.Log.i(AppConfig.TAG, "DownloadTracking: Saving $downloadBytes bytes for config $guid")
        MmkvManager.addServerDownloadBytes(guid, downloadBytes)

        // Check what was actually saved
        val aff = MmkvManager.decodeServerAffiliationInfo(guid)
        android.util.Log.i(AppConfig.TAG, "DownloadTracking: Total now: ${aff?.totalDownloadBytes} bytes (${aff?.getTotalDownloadString()})")

        // Update UI every 5 iterations (15 seconds) to show download stats
        downloadUpdateCounter++
        if (downloadUpdateCounter >= 5) {
            downloadUpdateCounter = 0
            android.util.Log.i(AppConfig.TAG, "DownloadTracking: Sending UI update message")
            val service = getService()
            service?.let {
                MessageUtil.sendMsg2UI(it, AppConfig.MSG_DOWNLOAD_STATS_UPDATE, "")
            }
        }
    }

    /**
     * Starts download tracking independently of speed notification.
     * @param currentConfig The current profile configuration.
     */
    fun startDownloadTracking(currentConfig: ProfileItem?) {
        // Stop any existing tracking job
        downloadTrackingJob?.cancel()

        // If speed notification is enabled, it already tracks downloads, so don't start separate tracking
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED) == true) {
            android.util.Log.i(AppConfig.TAG, "DownloadTracking: Speed notification enabled, using speed notification for tracking")
            return
        }

        if (V2RayServiceManager.isRunning() == false) {
            android.util.Log.i(AppConfig.TAG, "DownloadTracking: VPN not running, not starting tracking")
            return
        }

        currentConfigGuid = MmkvManager.getSelectServer()
        downloadUpdateCounter = 0 // Reset counter when starting new tracking
        val outboundTags = currentConfig?.getAllOutboundTags()
        outboundTags?.remove(AppConfig.TAG_DIRECT)

        android.util.Log.i(AppConfig.TAG, "DownloadTracking: Started for config $currentConfigGuid with tags: $outboundTags")

        downloadTrackingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                var totalDownloadThisQuery = 0L

                outboundTags?.forEach {
                    val down = V2RayServiceManager.queryStats(it, AppConfig.DOWNLINK)
                    totalDownloadThisQuery += down
                }
                val directDownlink = V2RayServiceManager.queryStats(AppConfig.TAG_DIRECT, AppConfig.DOWNLINK)
                totalDownloadThisQuery += directDownlink

                android.util.Log.i(AppConfig.TAG, "DownloadTracking: Query returned $totalDownloadThisQuery bytes for $currentConfigGuid")

                // Update total download for current config
                updateTotalDownload(currentConfigGuid, totalDownloadThisQuery)

                delay(3000)
            }
        }
    }

    /**
     * Stops download tracking.
     */
    fun stopDownloadTracking() {
        downloadTrackingJob?.cancel()
        downloadTrackingJob = null
    }

    /**
     * Shows the notification.
     * @param currentConfig The current profile configuration.
     */
    fun showNotification(currentConfig: ProfileItem?) {
        val service = getService() ?: return
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val startMainIntent = Intent(service, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(service, NOTIFICATION_PENDING_INTENT_CONTENT, startMainIntent, flags)

        val stopV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        stopV2RayIntent.`package` = AppConfig.ANG_PACKAGE
        stopV2RayIntent.putExtra("key", AppConfig.MSG_STATE_STOP)
        val stopV2RayPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_STOP_V2RAY, stopV2RayIntent, flags)

        val restartV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        restartV2RayIntent.`package` = AppConfig.ANG_PACKAGE
        restartV2RayIntent.putExtra("key", AppConfig.MSG_STATE_RESTART)
        val restartV2RayPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_RESTART_V2RAY, restartV2RayIntent, flags)

        val channelId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel()
            } else {
                // If earlier version channel ID is not used
                // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                ""
            }

        mBuilder = NotificationCompat.Builder(service, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(currentConfig?.remarks)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_delete_24dp,
                service.getString(R.string.notification_action_stop_v2ray),
                stopV2RayPendingIntent
            )
            .addAction(
                R.drawable.ic_delete_24dp,
                service.getString(R.string.title_service_restart),
                restartV2RayPendingIntent
            )

        //mBuilder?.setDefaults(NotificationCompat.FLAG_ONLY_ALERT_ONCE)

        service.startForeground(NOTIFICATION_ID, mBuilder?.build())
    }

    /**
     * Cancels the notification.
     */
    fun cancelNotification() {
        val service = getService() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            service.stopForeground(true)
        }

        mBuilder = null
        speedNotificationJob?.cancel()
        speedNotificationJob = null
        downloadTrackingJob?.cancel()
        downloadTrackingJob = null
        mNotificationManager = null
    }

    /**
     * Stops the speed notification.
     * @param currentConfig The current profile configuration.
     */
    fun stopSpeedNotification(currentConfig: ProfileItem?) {
        speedNotificationJob?.let {
            it.cancel()
            speedNotificationJob = null
            updateNotification(currentConfig?.remarks, 0, 0)
        }
    }

    /**
     * Creates a notification channel for Android O and above.
     * @return The channel ID.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = AppConfig.RAY_NG_CHANNEL_ID
        val channelName = AppConfig.RAY_NG_CHANNEL_NAME
        val chan = NotificationChannel(
            channelId,
            channelName, NotificationManager.IMPORTANCE_HIGH
        )
        chan.lightColor = Color.DKGRAY
        chan.importance = NotificationManager.IMPORTANCE_NONE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        getNotificationManager()?.createNotificationChannel(chan)
        return channelId
    }

    /**
     * Updates the notification with the given content text and traffic data.
     * @param contentText The content text.
     * @param proxyTraffic The proxy traffic.
     * @param directTraffic The direct traffic.
     */
    private fun updateNotification(contentText: String?, proxyTraffic: Long, directTraffic: Long) {
        if (mBuilder != null) {
            if (proxyTraffic < NOTIFICATION_ICON_THRESHOLD && directTraffic < NOTIFICATION_ICON_THRESHOLD) {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_name)
            } else if (proxyTraffic > directTraffic) {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_proxy)
            } else {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_direct)
            }
            mBuilder?.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            mBuilder?.setContentText(contentText)
            getNotificationManager()?.notify(NOTIFICATION_ID, mBuilder?.build())
        }
    }

    /**
     * Gets the notification manager.
     * @return The notification manager.
     */
    private fun getNotificationManager(): NotificationManager? {
        if (mNotificationManager == null) {
            val service = getService() ?: return null
            mNotificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        return mNotificationManager
    }

    /**
     * Appends the speed string to the given text.
     * @param text The text to append to.
     * @param name The name of the tag.
     * @param up The uplink speed.
     * @param down The downlink speed.
     */
    private fun appendSpeedString(text: StringBuilder, name: String?, up: Double, down: Double) {
        var n = name ?: "no tag"
        n = n.substring(0, min(n.length, 6))
        text.append(n)
        for (i in n.length..6 step 2) {
            text.append("\t")
        }
        text.append("•  ${up.toLong().toSpeedString()}↑  ${down.toLong().toSpeedString()}↓\n")
    }

    /**
     * Gets the service instance.
     * @return The service instance.
     */
    private fun getService(): Service? {
        return V2RayServiceManager.serviceControl?.get()?.getService()
    }
}