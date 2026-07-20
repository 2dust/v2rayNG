package com.v2ray.ang.shizuku

import android.annotation.SuppressLint
import android.net.TetheringManager
import android.os.Build
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Calls the TetheringManager API that exists as a hidden system API on Android 11-15.
 * The same types became public in API 36, which is why the public SDK reports them as new.
 */
internal object TetheringApi30To35 {

    // These framework types exist on Android 11-15 but were absent from the public SDK until 36.
    @SuppressLint("NewApi")
    fun startTethering(
        service: Any,
        type: Int,
        executor: Executor,
        timeoutSeconds: Long,
    ): Int {
        require(Build.VERSION.SDK_INT in Build.VERSION_CODES.R until 36)
        val manager = service as TetheringManager
        var result = ShizukuTetheringService.RESULT_INTERNAL_ERROR
        val callbackReceived = CountDownLatch(1)
        manager.startTethering(
            TetheringManager.TetheringRequest.Builder(type).build(),
            executor,
            object : TetheringManager.StartTetheringCallback {
                override fun onTetheringStarted() {
                    result = ShizukuTetheringService.RESULT_OK
                    callbackReceived.countDown()
                }

                override fun onTetheringFailed(error: Int) {
                    result = error
                    callbackReceived.countDown()
                }
            },
        )
        return if (callbackReceived.await(timeoutSeconds, TimeUnit.SECONDS)) {
            result
        } else {
            ShizukuTetheringService.RESULT_INTERNAL_ERROR
        }
    }
}
