package com.v2ray.ang.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationHelperTest {

    @Test
    fun `disabled notifications cannot be posted`() {
        assertFalse(
            canPostNotification(
                notificationsEnabled = false,
                permissionRequired = false,
                permissionGranted = true,
            )
        )
    }

    @Test
    fun `notification permission is not required before Android 13`() {
        assertTrue(
            canPostNotification(
                notificationsEnabled = true,
                permissionRequired = false,
                permissionGranted = false,
            )
        )
    }

    @Test
    fun `granted notification permission allows posting`() {
        assertTrue(
            canPostNotification(
                notificationsEnabled = true,
                permissionRequired = true,
                permissionGranted = true,
            )
        )
    }

    @Test
    fun `denied notification permission prevents posting`() {
        assertFalse(
            canPostNotification(
                notificationsEnabled = true,
                permissionRequired = true,
                permissionGranted = false,
            )
        )
    }
}
