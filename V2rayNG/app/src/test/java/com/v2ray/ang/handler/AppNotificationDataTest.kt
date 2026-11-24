package com.v2ray.ang.handler

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppNotificationDataTest {
    @Test
    fun notificationTypesUseTheDisplayedTextAndPolitePriorityByDefault() {
        for (type in AppNotificationType.entries) {
            val notification = AppNotificationData("Updated configs", type)
            assertEquals(notification.message, notification.announcementText)
            assertFalse(notification.assertive)
        }
    }

    @Test
    fun serviceFeedbackKeepsTheDisplayedTextAndDeliversAnAssertiveAnnouncement() = runBlocking {
        val received = async(start = CoroutineStart.UNDISPATCHED) { AppNotificationManager.notifications.first() }
        AppNotificationManager.show(
            message = "Service started successfully",
            type = AppNotificationType.SUCCESS,
            durationMs = 2500,
            assertive = true,
            accessibilityMessage = "Service started successfully. Now connected to Lab.",
        )

        val notification = withTimeout(1000) { received.await() }
        assertEquals("Service started successfully", notification.message)
        assertEquals(AppNotificationType.SUCCESS, notification.type)
        assertEquals(2500L, notification.durationMs)
        assertTrue(notification.assertive)
        assertEquals("Service started successfully. Now connected to Lab.", notification.announcementText)
    }

    @Test
    fun blankAccessibilityOverrideFallsBackToTheDisplayedMessage() {
        val notification = AppNotificationData("Service started successfully", accessibilityMessage = " ")
        assertEquals(notification.message, notification.announcementText)
    }
}
