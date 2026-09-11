package com.v2ray.ang.extension

import android.content.Context
import com.v2ray.ang.R
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
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
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
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
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
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
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
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
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

/** Shared text for the service's background notification and foreground live region. */
internal fun Context.serviceStartedMessage(serverName: String): String {
    val name = serverName.trim()
    return if (name.isEmpty()) {
        getString(R.string.toast_services_success)
    } else {
        getString(R.string.acc_service_started_connected_to, name)
    }
}

private fun Context.dispatchMessage(
    message: CharSequence,
    type: ToastType,
    liveRegionMode: AccessibilityLiveRegionMode,
    accessibilityMessage: CharSequence? = null,
) {
    val event = AppSnackbarMessage(
        message = message,
        type = type,
        liveRegionMode = liveRegionMode,
        accessibilityMessage = accessibilityMessage,
    )
    if (AppSnackbarManager.show(event)) {
        NotificationHelper.cancelTransientMessage(this)
    } else {
        NotificationHelper.notifyTransientMessage(this, accessibilityMessage ?: message)
    }
}
