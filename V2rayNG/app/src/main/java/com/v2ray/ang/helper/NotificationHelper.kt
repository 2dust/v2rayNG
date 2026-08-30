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
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.v2ray.ang.R
import com.v2ray.ang.enums.NotificationChannelType
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil

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
     * Posts transient feedback when there is no active in-app Snackbar host.
     * All fallback messages share one notification ID, so new feedback replaces the old message.
     *
     * Android recommends notifications for relevant background feedback. The notification is
     * skipped when the user has disabled notifications or denied the runtime permission.
     * https://developer.android.com/guide/topics/ui/notifiers/toasts#Alternatives
     */
    fun notifyTransientMessage(context: Context, content: CharSequence): Boolean {
        if (content.isBlank()) return false

        val appContext = context.applicationContext
        if (!canPostNotifications(appContext)) return false
        val localizedContext = AppLocaleManager.localizedContext(appContext)

        val channelType = NotificationChannelType.TRANSIENT_MESSAGE
        return try {
            ensureChannelCreated(channelType, localizedContext)
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val contentIntent = PendingIntent.getActivity(
                appContext,
                channelType.notificationId,
                Intent(appContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                },
                flags
            )
            val builder = buildNotificationBuilder(
                channelType = channelType,
                context = appContext,
                title = localizedContext.getString(R.string.app_name),
                content = content.toString()
            ).setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(false)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setTimeoutAfter(TRANSIENT_MESSAGE_TIMEOUT_MS)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

            getNotificationManager(appContext).notify(channelType.notificationId, builder.build())
            true
        } catch (e: SecurityException) {
            LogUtil.w(
                message = "NotificationHelper: failed to post transient message",
                throwable = e
            )
            false
        }
    }

    /** Removes stale background feedback once the same event is delivered in the foreground. */
    fun cancelTransientMessage(context: Context) {
        getNotificationManager(context.applicationContext)
            .cancel(NotificationChannelType.TRANSIENT_MESSAGE.notificationId)
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

    private fun ensureChannelCreated(channelType: NotificationChannelType, context: Context) =
        ensureNotificationChannel(
            context = context,
            channelId = channelType.channelId,
            channelNameRes = channelType.channelNameRes,
            importance = channelType.importance,
        ) {
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }

    /**
     * Creates a channel or updates only its localized name.
     *
     * Android lets apps rename an existing channel, while its behavior remains under user control.
     */
    internal fun ensureNotificationChannel(
        context: Context,
        channelId: String,
        @StringRes channelNameRes: Int,
        importance: Int,
        configureNewChannel: NotificationChannel.() -> Unit = {},
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val localizedName = AppLocaleManager.localizedContext(context).getString(channelNameRes)
        val existingChannel = notificationManager.getNotificationChannel(channelId)
        if (existingChannel == null) {
            NotificationChannel(channelId, localizedName, importance)
                .apply(configureNewChannel)
                .also(notificationManager::createNotificationChannel)
        } else if (notificationChannelNameNeedsUpdate(existingChannel.name, localizedName)) {
            existingChannel.name = localizedName
            notificationManager.createNotificationChannel(existingChannel)
        }
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

        val displayTitle = title.ifEmpty {
            AppLocaleManager.localizedContext(context).getString(R.string.app_name)
        }
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(displayTitle)
            .setContentText(content)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setPriority(channelType.priority)
            .setCategory(channelType.category)
            .apply { action?.let(::addAction) }
    }

    private fun canPostNotifications(context: Context): Boolean {
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val permissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val permissionGranted = !permissionRequired || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        return canPostNotification(notificationsEnabled, permissionRequired, permissionGranted)
    }
}

internal fun canPostNotification(
    notificationsEnabled: Boolean,
    permissionRequired: Boolean,
    permissionGranted: Boolean,
): Boolean {
    return notificationsEnabled && (!permissionRequired || permissionGranted)
}

internal fun notificationChannelNameNeedsUpdate(
    existingName: CharSequence?,
    localizedName: String,
): Boolean = existingName?.toString() != localizedName

private const val TRANSIENT_MESSAGE_TIMEOUT_MS = 10_000L
