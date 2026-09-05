package com.v2ray.ang.enums

import com.v2ray.ang.R
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationChannelTypeTest {

    @Test
    fun `channels use dedicated category title resources`() {
        assertEquals(
            R.string.notification_channel_subscription_updates,
            NotificationChannelType.SUBSCRIPTION_UPDATE.channelNameRes,
        )
        assertEquals(
            R.string.notification_channel_connection_checks,
            NotificationChannelType.CORE_TEST.channelNameRes,
        )
        assertEquals(
            R.string.notification_channel_other,
            NotificationChannelType.TRANSIENT_MESSAGE.channelNameRes,
        )
    }

    @Test
    fun `channel names can change without replacing existing channels`() {
        assertEquals("subscription_update_channel", NotificationChannelType.SUBSCRIPTION_UPDATE.channelId)
        assertEquals("core_test_channel", NotificationChannelType.CORE_TEST.channelId)
        assertEquals("transient_message_channel", NotificationChannelType.TRANSIENT_MESSAGE.channelId)
    }
}
