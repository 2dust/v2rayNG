package com.v2ray.ang.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.v2ray.ang.R
import com.v2ray.ang.enums.NotificationChannelType

/**
 * Unified notification helper for different notification channels.
 * Supports both regular notifications and foreground service notifications.
 *
 * Performance: NotificationManager is cached. Builder is created once per update.
 * Safe for high-frequency updates (100+ times/second).
 */
object NotificationHelper {

    // Cached instances for performance
    private var cachedNotificationManager: NotificationManager? = null
    private val builderCache = mutableMapOf<Int, NotificationCompat.Builder>()

    /**
     * Notify with a regular notification (non-foreground).
     *
     * @param channelType The notification channel type (defines channelId, notificationId, etc.)
     * @param context The context for building the notification
     * @param title The notification title
     * @param content The notification content text
     */
    fun notify(
        channelType: NotificationChannelType,
        context: Context,
        title: String,
        content: String
    ) {
        ensureChannelCreated(channelType, context)
        val notificationManager = getNotificationManager(context)
        val builder = buildNotificationBuilder(channelType, context, title, content)
        notificationManager.notify(channelType.notificationId, builder.build())
    }

    /**
     * Update an existing notification's content.
     * Optimized for high-frequency updates (100+/sec).
     * Reuses cached Builder to minimize allocation overhead.
     *
     * @param channelType The notification channel type
     * @param context The context
     * @param content The new content text
     */
    fun updateNotification(
        channelType: NotificationChannelType,
        context: Context,
        title: String,
        content: String
    ) {
        val notificationManager = getNotificationManager(context)

        // Get or create builder from cache
        val builder = builderCache.getOrPut(channelType.notificationId) {
            buildNotificationBuilder(channelType, context, title, content)
        }

        // Update only the content text (fast operation)
        builder.setContentText(content)
        notificationManager.notify(channelType.notificationId, builder.build())
    }

    /**
     * Start a foreground service with a notification.
     *
     * @param service The service to set as foreground
     * @param channelType The notification channel type
     * @param title The notification title
     * @param content The notification content text
     */
    fun startForeground(
        service: Service,
        channelType: NotificationChannelType,
        title: String,
        content: String
    ) {
        ensureChannelCreated(channelType, service)
        val builder = buildNotificationBuilder(channelType, service, title, content)
        service.startForeground(channelType.notificationId, builder.build())
    }

    /**
     * Stop the foreground notification for a service.
     *
     * @param service The service to stop foreground on
     */
    fun stopForeground(service: Service) {
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
    }

    /**
     * Cancel a notification and clean up cached builder.
     *
     * @param channelType The notification channel type
     * @param context The context
     */
    fun cancel(
        channelType: NotificationChannelType,
        context: Context
    ) {
        getNotificationManager(context).cancel(channelType.notificationId)
        builderCache.remove(channelType.notificationId)  // Clean up cache
    }

    // ====== Private helper methods ======

    private fun getNotificationManager(context: Context): NotificationManager {
        if (cachedNotificationManager == null) {
            cachedNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        return cachedNotificationManager!!
    }

    private fun ensureChannelCreated(channelType: NotificationChannelType, context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel(channelType.channelId) != null) return

        val channel = NotificationChannel(
            channelType.channelId,
            channelType.channelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotificationBuilder(
        channelType: NotificationChannelType,
        context: Context,
        title: String,
        content: String
    ): NotificationCompat.Builder {
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelType.channelId
        } else {
            ""
        }

        val displayTitle = title.ifEmpty { context.getString(R.string.app_name) }
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(displayTitle)
            .setContentText(content)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
    }
}

