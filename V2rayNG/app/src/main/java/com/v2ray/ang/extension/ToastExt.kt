package com.v2ray.ang.extension

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.StringRes
import com.v2ray.ang.AngApplication
import com.v2ray.ang.dto.UserMessage
import com.v2ray.ang.handler.AppLocaleManager

private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

// Keep the existing caller API; delivery uses snackbars, never platform toasts.
// The long option extends transient feedback; lengthy errors still require dismissal.
fun Context.toast(@StringRes message: Int, long: Boolean = false) =
    toast(AppLocaleManager.localizedContext(this).getString(message), long)

fun Context.toast(message: CharSequence, long: Boolean = false) =
    dispatchMessage(UserMessage(message.toString(), long = long))

fun Context.toastSuccess(@StringRes message: Int, long: Boolean = false) =
    toastSuccess(AppLocaleManager.localizedContext(this).getString(message), long)

fun Context.toastSuccess(message: CharSequence, long: Boolean = false) =
    dispatchMessage(UserMessage(message.toString(), type = UserMessage.Type.SUCCESS, long = long))

fun Context.toastError(@StringRes message: Int, long: Boolean = false) =
    toastError(AppLocaleManager.localizedContext(this).getString(message), long)

fun Context.toastError(message: CharSequence, long: Boolean = false) =
    dispatchMessage(UserMessage(message.toString(), type = UserMessage.Type.ERROR, long = long))

fun Context.toastInfo(@StringRes message: Int, long: Boolean = false) =
    toastInfo(AppLocaleManager.localizedContext(this).getString(message), long)

fun Context.toastInfo(message: CharSequence, long: Boolean = false) =
    dispatchMessage(UserMessage(message.toString(), type = UserMessage.Type.INFO, long = long))

private fun Context.dispatchMessage(message: UserMessage) {
    val appContext = applicationContext
    val deliver = Runnable {
        (appContext as AngApplication).snackbarManager.show(message)
    }
    if (Looper.myLooper() == Looper.getMainLooper()) deliver.run()
    else mainHandler.post(deliver)
}
