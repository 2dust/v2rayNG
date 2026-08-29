package com.v2ray.ang.enums

import android.app.NotificationManager
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.v2ray.ang.R

/**
 * Enum defining different notification channels.
 * Each channel has a unique channelId, notificationId, and display name.
 */
enum class NotificationChannelType(
    val channelId: String,
    @StringRes val channelNameRes: Int,
    val notificationId: Int,
    val importance: Int = NotificationManager.IMPORTANCE_LOW,
    val priority: Int = NotificationCompat.PRIORITY_LOW,
    val category: String = NotificationCompat.CATEGORY_SERVICE,
) {
    SUBSCRIPTION_UPDATE(
        channelId = "subscription_update_channel",
        channelNameRes = R.string.title_sub_update,
        notificationId = 13
    ),
    CORE_TEST(
        channelId = "core_test_channel",
        channelNameRes = R.string.connection_test_pending,
        notificationId = 12
    ),
    TRANSIENT_MESSAGE(
        channelId = "transient_message_channel",
        channelNameRes = R.string.permission_notification,
        notificationId = 14,
        category = NotificationCompat.CATEGORY_STATUS,
    )
}
