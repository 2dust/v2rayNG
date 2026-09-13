package com.v2ray.ang.extension

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.StringRes
import com.v2ray.ang.AngApplication
import com.v2ray.ang.dto.UserMessage
import com.v2ray.ang.handler.AppLocaleManager

// Keep the existing caller API; delivery uses snackbars, never platform toasts.
fun Context.toast(@StringRes message: Int) = toast(AppLocaleManager.localizedContext(this).getString(message))

fun Context.toast(message: CharSequence) = showMessage(UserMessage(message.toString()))

fun Context.toastSuccess(@StringRes message: Int) = toast(message)

fun Context.toastSuccess(message: CharSequence) = toast(message)

fun Context.toastError(@StringRes message: Int) = toastError(AppLocaleManager.localizedContext(this).getString(message))

fun Context.toastError(message: CharSequence) = showMessage(UserMessage(message.toString(), isError = true))

private fun Context.showMessage(message: UserMessage) {
    val appContext = applicationContext
    val deliver = Runnable {
        (appContext as AngApplication).snackbarManager.show(message)
    }
    if (Looper.myLooper() == Looper.getMainLooper()) deliver.run()
    else Handler(Looper.getMainLooper()).post(deliver)
}
