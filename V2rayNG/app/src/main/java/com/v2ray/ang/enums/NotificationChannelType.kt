package com.v2ray.ang.enums

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
    val category: String = NotificationCompat.CATEGORY_SERVICE,
) {
    SUBSCRIPTION_UPDATE(
        channelId = "subscription_update_channel",
        channelNameRes = R.string.notification_channel_subscription_updates,
        notificationId = 13
    ),
    CORE_TEST(
        channelId = "core_test_channel",
        channelNameRes = R.string.notification_channel_connection_checks,
        notificationId = 12
    ),
    TRANSIENT_MESSAGE(
        channelId = "transient_message_channel",
        channelNameRes = R.string.notification_channel_other,
        notificationId = 14,
        category = NotificationCompat.CATEGORY_STATUS,
    )
}
