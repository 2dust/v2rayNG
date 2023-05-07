package com.v2ray.ang

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.v2ray.ang.extension.toast


class ExceptionHandler(private val context: Context) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // Display a Toast message with the error message
        context.toast( "An error occurred: ${throwable.message}", Toast.LENGTH_SHORT)

        // Log the exception for future reference
        Log.e("MyApp", "An error occurred", throwable)

        // Call the default exception handler to terminate the app
        Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(thread, throwable)
    }
}
