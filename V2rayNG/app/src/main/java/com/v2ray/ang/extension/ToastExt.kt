package com.v2ray.ang.extension

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.appcompat.widget.AppCompatTextView
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
) {
    val text = getString(message)
    dispatchMessage(text, ToastType.SUCCESS, liveRegionMode)
}

/**
 * Shows a toast message with the given text.
 *
 * @param message The text of the message to show.
 */
fun Context.toastSuccess(
    message: CharSequence,
    liveRegionMode: AccessibilityLiveRegionMode? = null,
) {
    dispatchMessage(message, ToastType.SUCCESS, liveRegionMode)
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

private inline fun runOnMain(crossinline block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        block()
    } else {
        Handler(Looper.getMainLooper()).post { block() }
    }
}

private fun Context.dispatchMessage(
    message: CharSequence,
    type: ToastType,
    liveRegionMode: AccessibilityLiveRegionMode?,
    long: Boolean = false,
) {
    val handledBySnackbar = AppSnackbarManager.show(
        message = message,
        type = type,
        long = long,
        liveRegionMode = liveRegionMode,
    )
    if (!handledBySnackbar) {
        runOnMain { showAccessibilitySilentToast(message, long) }
    }
}

/**
 * Android text Toasts emit their own accessibility event and can move TalkBack focus. The custom
 * fallback remains visible while exposing no accessibility node; foreground activities announce
 * opted-in messages through [AppSnackbarManager]. Remove this compatibility path when every
 * message source is guaranteed to have an active Compose Snackbar host.
 */
@Suppress("DEPRECATION")
private fun Context.showAccessibilitySilentToast(message: CharSequence, long: Boolean) {
    val density = resources.displayMetrics.density
    val horizontalPadding = (16 * density).toInt()
    val verticalPadding = (12 * density).toInt()
    val bottomOffset = (100 * density).toInt()
    val background = GradientDrawable().apply {
        setColor(Color.argb(235, 48, 48, 48))
        cornerRadius = 24 * density
    }
    val content = object : AppCompatTextView(this) {
        override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean = true
    }.apply {
        text = message
        setTextColor(Color.WHITE)
        textSize = 14f
        gravity = Gravity.CENTER
        maxLines = 8
        setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        this.background = background
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    Toast(this).apply {
        duration = if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        view = content
        setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, bottomOffset)
    }.show()
}
