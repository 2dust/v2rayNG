package com.v2ray.ang.extension

import android.content.Context
import com.v2ray.ang.handler.AppNotificationManager
import com.v2ray.ang.handler.AppNotificationType

private fun dispatchMessage(
    message: CharSequence,
    type: AppNotificationType
) {
    AppNotificationManager.show(message = message, type = type)
}

/**
 * Shows a toast message with the given resource ID.
 */
fun Context.toast(message: Int, long: Boolean = false) {
    dispatchMessage(getString(message), AppNotificationType.NORMAL)
}

/**
 * Shows a toast message with the given text.
 */
fun Context.toast(message: CharSequence, long: Boolean = false) {
    dispatchMessage(message, AppNotificationType.NORMAL)
}

/**
 * Shows a success toast message with the given resource ID.
 */
fun Context.toastSuccess(message: Int, long: Boolean = false) {
    dispatchMessage(getString(message), AppNotificationType.SUCCESS)
}

/**
 * Shows a success toast message with the given text.
 */
fun Context.toastSuccess(message: CharSequence, long: Boolean = false) {
    dispatchMessage(message, AppNotificationType.SUCCESS)
}

/**
 * Shows an error toast message with the given resource ID.
 */
fun Context.toastError(message: Int, long: Boolean = false) {
    dispatchMessage(getString(message), AppNotificationType.ERROR)
}

/**
 * Shows an error toast message with the given text.
 */
fun Context.toastError(message: CharSequence, long: Boolean = false) {
    dispatchMessage(message, AppNotificationType.ERROR)
}

/**
 * Shows an info toast message with the given resource ID.
 */
fun Context.toastInfo(message: Int, long: Boolean = false) {
    dispatchMessage(getString(message), AppNotificationType.INFO)
}

/**
 * Shows an info toast message with the given text.
 */
fun Context.toastInfo(message: CharSequence, long: Boolean = false) {
    dispatchMessage(message, AppNotificationType.INFO)
}

/**
 * App notification helper methods.
 */
fun Context.showNotification(
    message: CharSequence,
    type: AppNotificationType = AppNotificationType.NORMAL
) {
    dispatchMessage(message, type)
}
