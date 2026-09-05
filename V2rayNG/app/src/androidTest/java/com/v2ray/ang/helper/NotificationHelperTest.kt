package com.v2ray.ang.helper

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.UiAutomation
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import com.v2ray.ang.enums.NotificationChannelType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.ui.compose.AppSnackbarManager
import com.v2ray.ang.ui.main.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
class NotificationHelperTest {
    @Before
    fun allowNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                .grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @Test
    fun backgroundMessagesReplaceEachOtherAndForegroundDeliveryCancelsTheFallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val notificationId = NotificationChannelType.TRANSIENT_MESSAGE.notificationId
        try {
            await { !AppSnackbarManager.hasActiveHost() }
            context.toast("First background result")
            await { manager.activeNotifications.any { it.id == notificationId } }
            context.toast("Second background result")
            await {
                manager.activeNotifications.singleOrNull { it.id == notificationId }
                    ?.notification?.extras?.getCharSequence(Notification.EXTRA_TEXT) == "Second background result"
            }
            val notification = manager.activeNotifications.single { it.id == notificationId }.notification
            assertEquals(10_000L, notification.timeoutAfter)

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                await { AppSnackbarManager.hasActiveHost() }
                scenario.onActivity { it.toast("Foreground result") }
                await { manager.activeNotifications.none { it.id == notificationId } }
            }
        } finally {
            NotificationHelper.cancelTransientMessage(context)
        }
    }

    @Test
    fun renamingAnExistingChannelPreservesItsBehavior() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "test_transient_channel_${System.nanoTime()}"
        try {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Old channel title", NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    setSound(null, null)
                }
            )
            val original = manager.getNotificationChannel(channelId)
            NotificationHelper.ensureNotificationChannel(
                context, channelId, R.string.notification_channel_other, NotificationManager.IMPORTANCE_LOW,
            )
            val renamed = manager.getNotificationChannel(channelId)
            assertEquals(
                AppLocaleManager.localizedContext(context).getString(R.string.notification_channel_other),
                renamed.name.toString(),
            )
            assertEquals(original.importance, renamed.importance)
            assertEquals(original.sound, renamed.sound)
            assertTrue(renamed.shouldVibrate())
        } finally {
            manager.deleteNotificationChannel(channelId)
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5000L
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20L)
        assertTrue("Timed out waiting for notification delivery", condition())
    }
}
