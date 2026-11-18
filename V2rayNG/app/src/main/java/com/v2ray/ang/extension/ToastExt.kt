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

/** Shows normal feedback and mirrors it to the accessibility live region. */
fun Context.toast(
    message: Int,
    long: Boolean = false,
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
) {
    dispatchMessage(getString(message), ToastType.NORMAL, long, liveRegionMode)
}

/** Shows normal feedback and mirrors it to the accessibility live region. */
fun Context.toast(
    message: CharSequence,
    long: Boolean = false,
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
) {
    dispatchMessage(message, ToastType.NORMAL, long, liveRegionMode)
}

/** Shows success feedback and mirrors it to the accessibility live region. */
fun Context.toastSuccess(
    message: Int,
    long: Boolean = false,
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
    accessibilityMessage: CharSequence? = null,
) {
    dispatchMessage(getString(message), ToastType.SUCCESS, long, liveRegionMode, accessibilityMessage)
}

/** Shows success feedback and mirrors it to the accessibility live region. */
fun Context.toastSuccess(
    message: CharSequence,
    long: Boolean = false,
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
    accessibilityMessage: CharSequence? = null,
) {
    dispatchMessage(message, ToastType.SUCCESS, long, liveRegionMode, accessibilityMessage)
}

/** Shows error feedback and mirrors it to the accessibility live region. */
fun Context.toastError(
    message: Int,
    long: Boolean = false,
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
) {
    dispatchMessage(getString(message), ToastType.ERROR, long, liveRegionMode)
}

/** Shows error feedback and mirrors it to the accessibility live region. */
fun Context.toastError(
    message: CharSequence,
    long: Boolean = false,
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
) {
    dispatchMessage(message, ToastType.ERROR, long, liveRegionMode)
}

/** Shows info feedback and mirrors it to the accessibility live region. */
fun Context.toastInfo(
    message: Int,
    long: Boolean = false,
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
) {
    dispatchMessage(getString(message), ToastType.INFO, long, liveRegionMode)
}

/** Shows info feedback and mirrors it to the accessibility live region. */
fun Context.toastInfo(
    message: CharSequence,
    long: Boolean = false,
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
) {
    dispatchMessage(message, ToastType.INFO, long, liveRegionMode)
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
    long: Boolean,
    liveRegionMode: AccessibilityLiveRegionMode,
    accessibilityMessage: CharSequence? = null,
) {
    val event = AppSnackbarMessage(
        message = message,
        type = type,
        liveRegionMode = liveRegionMode,
        accessibilityMessage = accessibilityMessage,
        long = long,
    )
    if (AppSnackbarManager.show(event)) {
        NotificationHelper.cancelTransientMessage(this)
    } else {
        NotificationHelper.notifyTransientMessage(this, accessibilityMessage ?: message)
    }
}
