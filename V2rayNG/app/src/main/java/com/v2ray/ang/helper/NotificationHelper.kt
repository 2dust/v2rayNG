package com.v2ray.ang.helper

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.v2ray.ang.R
import com.v2ray.ang.dto.UserMessage
import com.v2ray.ang.enums.NotificationChannelType
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.ui.feedback.UserMessageActivity
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil
import java.util.UUID

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

    /** Routine background feedback is replaced; long errors remain independently dismissible. */
    fun notifyTransientMessage(context: Context, message: UserMessage) {
        val content = message.text
        if (content.isBlank()) return
        val appContext = context.applicationContext
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val localizedContext = AppLocaleManager.localizedContext(appContext)
        val manager = getNotificationManager(appContext)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = localizedContext.getString(R.string.notification_channel_other)
                val channel = manager.getNotificationChannel(TRANSIENT_MESSAGE_CHANNEL_ID)
                    ?: NotificationChannel(TRANSIENT_MESSAGE_CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW)
                // Rename an existing channel without changing the user's behavior settings.
                channel.name = name
                manager.createNotificationChannel(channel)
            }
            val notificationTag = if (message.requiresDismissal) UUID.randomUUID().toString() else null
            val target = if (notificationTag != null) {
                Intent(appContext, UserMessageActivity::class.java).apply {
                    // This notification-only viewer must not replace the app's task or editor drafts.
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    // Extras do not distinguish PendingIntents. Each retained error needs its own identity.
                    data = Uri.fromParts("v2rayng-message", notificationTag, null)
                    putExtra(UserMessageActivity.EXTRA_MESSAGE, content)
                }
            } else {
                Intent(appContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            }
            val contentIntent = PendingIntent.getActivity(
                appContext,
                TRANSIENT_MESSAGE_NOTIFICATION_ID,
                target,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(appContext, TRANSIENT_MESSAGE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle(localizedContext.getString(R.string.app_name))
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setTimeoutAfter(if (message.requiresDismissal) 0L else TRANSIENT_MESSAGE_TIMEOUT_MS)
                .build()
            manager.notify(notificationTag, TRANSIENT_MESSAGE_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            LogUtil.w(message = "NotificationHelper: failed to post transient message", throwable = e)
        }
    }

    /** New foreground feedback supersedes the last routine notification, but not undismissed errors. */
    fun cancelTransientMessage(context: Context) {
        getNotificationManager(context.applicationContext).cancel(TRANSIENT_MESSAGE_NOTIFICATION_ID)
    }

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
     * @param action An optional action retained when the notification is updated
     */
    fun startForeground(
        service: Service,
        channelType: NotificationChannelType,
        title: String,
        content: String,
        action: NotificationCompat.Action? = null
    ) {
        ensureChannelCreated(channelType, service)
        val builder = buildNotificationBuilder(channelType, service, title, content, action)
        builderCache[channelType.notificationId] = builder
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
        content: String,
        action: NotificationCompat.Action? = null
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
            .apply { action?.let(::addAction) }
    }
}

private const val TRANSIENT_MESSAGE_CHANNEL_ID = "transient_message_channel"
private const val TRANSIENT_MESSAGE_NOTIFICATION_ID = 14
private const val TRANSIENT_MESSAGE_TIMEOUT_MS = 10_000L
