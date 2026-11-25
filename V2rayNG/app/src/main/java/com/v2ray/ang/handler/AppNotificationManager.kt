package com.v2ray.ang.handler

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class AppNotificationType {
    NORMAL, SUCCESS, ERROR, INFO
}

/**
 * Calculates display duration based on text length.
 * Base 1500ms + 30ms per character, min 2000ms (2.0s), max 5000ms (5.0s).
 */
fun calculateNotificationDuration(message: CharSequence): Long {
    val length = message.length
    val calculated = 1500L + (length * 30L)
    return calculated.coerceIn(2000L, 5000L)
}

data class AppNotificationData(
    val message: CharSequence,
    val type: AppNotificationType = AppNotificationType.NORMAL,
    val durationMs: Long = calculateNotificationDuration(message)
)

object AppNotificationManager {
    private val _notifications = MutableSharedFlow<AppNotificationData>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val notifications = _notifications.asSharedFlow()

    fun show(
        message: CharSequence,
        type: AppNotificationType = AppNotificationType.NORMAL,
        durationMs: Long = calculateNotificationDuration(message)
    ) {
        _notifications.tryEmit(
            AppNotificationData(
                message = message,
                type = type,
                durationMs = durationMs
            )
        )
    }
}
