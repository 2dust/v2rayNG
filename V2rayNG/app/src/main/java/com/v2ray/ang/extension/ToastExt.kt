package com.v2ray.ang.extension

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.v2ray.ang.compose.AppSnackbarManager
import com.v2ray.ang.compose.ToastType

/**
 * Shows a toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 */
fun Context.toast(message: Int) {
    val text = getString(message)
    dispatchMessage(text, ToastType.NORMAL) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Shows a toast message with the given text.
 *
 * @param message The text of the message to show.
 */
fun Context.toast(message: CharSequence) {
    dispatchMessage(message, ToastType.NORMAL) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Shows a toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 */
fun Context.toastSuccess(message: Int) {
    val text = getString(message)
    dispatchMessage(text, ToastType.SUCCESS) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Shows a toast message with the given text.
 *
 * @param message The text of the message to show.
 */
fun Context.toastSuccess(message: CharSequence) {
    dispatchMessage(message, ToastType.SUCCESS) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Shows a toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 */
fun Context.toastError(message: Int) {
    val text = getString(message)
    dispatchMessage(text, ToastType.ERROR) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Shows a toast message with the given text.
 *
 * @param message The text of the message to show.
 */
fun Context.toastError(message: CharSequence) {
    dispatchMessage(message, ToastType.ERROR) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Shows an info toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 */
fun Context.toastInfo(message: Int) {
    val text = getString(message)
    dispatchMessage(text, ToastType.INFO) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Shows an info toast message with the given text.
 *
 * @param message The text of the message to show.
 */
fun Context.toastInfo(message: CharSequence) {
    dispatchMessage(message, ToastType.INFO) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

private inline fun runOnMain(crossinline block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        block()
    } else {
        Handler(Looper.getMainLooper()).post { block() }
    }
}

private inline fun dispatchMessage(
    message: CharSequence,
    type: ToastType,
    long: Boolean = false,
    crossinline fallback: () -> Unit
) {
    val handledBySnackbar = AppSnackbarManager.show(
        message = message,
        type = type,
        long = long
    )
    if (!handledBySnackbar) {
        runOnMain { fallback() }
    }
}
