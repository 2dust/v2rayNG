package com.v2ray.ang.extension

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig

private const val DuplicateAnnouncementWindowMs = 1_000L

/**
 * Sends important transient information without adding a focusable accessibility node.
 * Visual toasts deliberately stay outside the accessibility tree and opt in here instead.
 */
fun Context.announceImportantForAccessibility(message: CharSequence) {
    AccessibilityAnnouncementDispatcher.announce(this, message)
}

fun Context.isTouchExplorationEnabled(): Boolean =
    ContextCompat.getSystemService(this, AccessibilityManager::class.java)
        ?.isTouchExplorationEnabled == true

/**
 * The foreground notification owns routine connected/disconnected announcements whenever it is
 * visible. App-side announcements are then suppressed to avoid TalkBack reading both sources.
 */
fun Context.areCoreServiceNotificationsEnabled(): Boolean {
    if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true

    val manager = ContextCompat.getSystemService(this, NotificationManager::class.java)
        ?: return false
    val channel = manager.getNotificationChannel(AppConfig.RAY_NG_CHANNEL_ID)
    return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
}

private object AccessibilityAnnouncementDispatcher {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastMessage: String? = null
    private var lastAnnouncementAt = 0L

    fun announce(context: Context, message: CharSequence) {
        val appContext = context.applicationContext
        val text = message.toString()
        if (text.isBlank()) return

        mainHandler.post {
            val manager = ContextCompat.getSystemService(
                appContext,
                AccessibilityManager::class.java,
            )
            if (manager?.isEnabled != true || !manager.isTouchExplorationEnabled) return@post

            val now = SystemClock.elapsedRealtime()
            if (text == lastMessage && now - lastAnnouncementAt < DuplicateAnnouncementWindowMs) {
                return@post
            }
            lastMessage = text
            lastAnnouncementAt = now

            @Suppress("DEPRECATION")
            val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT).apply {
                packageName = appContext.packageName
                className = appContext.javaClass.name
                this.text.add(text)
            }
            manager.sendAccessibilityEvent(event)
        }
    }
}
