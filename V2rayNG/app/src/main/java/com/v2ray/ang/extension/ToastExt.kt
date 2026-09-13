package com.v2ray.ang.extension

import android.content.Context
import com.v2ray.ang.helper.NotificationHelper
import com.v2ray.ang.ui.compose.AppSnackbarManager
import com.v2ray.ang.ui.compose.ToastType

/**
 * Shows a toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 */
fun Context.toast(message: Int) {
    val text = getString(message)
    dispatchMessage(text, ToastType.NORMAL)
}

/**
 * Shows a toast message with the given text.
 *
 * @param message The text of the message to show.
 */
fun Context.toast(message: CharSequence) {
    dispatchMessage(message, ToastType.NORMAL)
}

/**
 * Shows a toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 */
fun Context.toastSuccess(message: Int) {
    val text = getString(message)
    dispatchMessage(text, ToastType.SUCCESS)
}

/**
 * Shows a toast message with the given text.
 *
 * @param message The text of the message to show.
 */
fun Context.toastSuccess(message: CharSequence) {
    dispatchMessage(message, ToastType.SUCCESS)
}

/**
 * Shows a toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 */
fun Context.toastError(message: Int) {
    val text = getString(message)
    dispatchMessage(text, ToastType.ERROR)
}

/**
 * Shows a toast message with the given text.
 *
 * @param message The text of the message to show.
 */
fun Context.toastError(message: CharSequence) {
    dispatchMessage(message, ToastType.ERROR)
}

/**
 * Shows an info toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 */
fun Context.toastInfo(message: Int) {
    val text = getString(message)
    dispatchMessage(text, ToastType.INFO)
}

/**
 * Shows an info toast message with the given text.
 *
 * @param message The text of the message to show.
 */
fun Context.toastInfo(message: CharSequence) {
    dispatchMessage(message, ToastType.INFO)
}

private fun Context.dispatchMessage(
    message: CharSequence,
    type: ToastType,
) {
    if (AppSnackbarManager.show(message, type)) {
        NotificationHelper.cancelTransientMessage(this)
    } else {
        NotificationHelper.notifyTransientMessage(this, message)
    }
}
