package com.v2ray.ang.extension

import android.content.Context
import com.v2ray.ang.helper.NotificationHelper
import com.v2ray.ang.ui.compose.AppSnackbarMessage
import com.v2ray.ang.ui.compose.AppSnackbarManager
import com.v2ray.ang.ui.compose.ToastType

enum class AccessibilityLiveRegionMode {
    POLITE,
    ASSERTIVE,
}

/**
 * Shows a toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 */
fun Context.toast(
    message: Int,
    liveRegionMode: AccessibilityLiveRegionMode? = null,
) {
    val text = getString(message)
    dispatchMessage(text, ToastType.NORMAL, liveRegionMode)
}

/**
 * Shows a toast message with the given text.
 *
 * @param message The text of the message to show.
 */
fun Context.toast(
    message: CharSequence,
    liveRegionMode: AccessibilityLiveRegionMode? = null,
) {
    dispatchMessage(message, ToastType.NORMAL, liveRegionMode)
}

/**
 * Shows a toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 */
fun Context.toastSuccess(
    message: Int,
    liveRegionMode: AccessibilityLiveRegionMode? = null,
    accessibilityMessage: CharSequence? = null,
) {
    val text = getString(message)
    dispatchMessage(text, ToastType.SUCCESS, liveRegionMode, accessibilityMessage)
}

/**
 * Shows a toast message with the given text.
 *
 * @param message The text of the message to show.
 */
fun Context.toastSuccess(
    message: CharSequence,
    liveRegionMode: AccessibilityLiveRegionMode? = null,
    accessibilityMessage: CharSequence? = null,
) {
    dispatchMessage(message, ToastType.SUCCESS, liveRegionMode, accessibilityMessage)
}

/**
 * Shows a toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 */
fun Context.toastError(
    message: Int,
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
) {
    val text = getString(message)
    dispatchMessage(text, ToastType.ERROR, liveRegionMode)
}

/**
 * Shows a toast message with the given text.
 *
 * @param message The text of the message to show.
 */
fun Context.toastError(
    message: CharSequence,
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
) {
    dispatchMessage(message, ToastType.ERROR, liveRegionMode)
}

/**
 * Shows an info toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 */
fun Context.toastInfo(message: Int) {
    val text = getString(message)
    dispatchMessage(text, ToastType.INFO, liveRegionMode = null)
}

/**
 * Shows an info toast message with the given text.
 *
 * @param message The text of the message to show.
 */
fun Context.toastInfo(message: CharSequence) {
    dispatchMessage(message, ToastType.INFO, liveRegionMode = null)
}

private fun Context.dispatchMessage(
    message: CharSequence,
    type: ToastType,
    liveRegionMode: AccessibilityLiveRegionMode?,
    accessibilityMessage: CharSequence? = null,
    long: Boolean = false,
) {
    val event = AppSnackbarMessage(
        message = message,
        type = type,
        long = long,
        liveRegionMode = liveRegionMode,
        accessibilityMessage = accessibilityMessage,
    )
    deliverTransientMessage(
        event = event,
        foregroundDelivery = AppSnackbarManager::show,
        backgroundDelivery = { NotificationHelper.notifyTransientMessage(this, it.message) }
    )
}

internal enum class TransientMessageDelivery {
    FOREGROUND_SNACKBAR,
    BACKGROUND_NOTIFICATION,
    UNAVAILABLE,
}

internal fun deliverTransientMessage(
    event: AppSnackbarMessage,
    foregroundDelivery: (AppSnackbarMessage) -> Boolean,
    backgroundDelivery: (AppSnackbarMessage) -> Boolean,
): TransientMessageDelivery {
    if (foregroundDelivery(event)) return TransientMessageDelivery.FOREGROUND_SNACKBAR
    return if (backgroundDelivery(event)) {
        TransientMessageDelivery.BACKGROUND_NOTIFICATION
    } else {
        TransientMessageDelivery.UNAVAILABLE
    }
}
