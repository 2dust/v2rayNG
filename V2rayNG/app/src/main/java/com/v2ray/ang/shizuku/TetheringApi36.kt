package com.v2ray.ang.shizuku

import android.net.TetheringInterface
import android.net.TetheringManager
import android.os.Build
import androidx.annotation.RequiresApi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/** Keeps API 36-only tethering types out of classes loaded on Android 13 through 15. */
@RequiresApi(Build.VERSION_CODES.BAKLAVA)
internal object TetheringApi36 {
    fun getActiveTetheringTypes(
        service: Any,
        executor: Executor,
        timeoutSeconds: Long,
    ): Int {
        val manager = service as TetheringManager
        var types = ShizukuTetheringService.TETHERING_TYPES_UNKNOWN
        val callbackReceived = CountDownLatch(1)
        val callback = object : TetheringManager.TetheringEventCallback {
            override fun onTetheredInterfacesChanged(interfaces: Set<TetheringInterface>) {
                types = interfaces.fold(0) { mask, item ->
                    mask or tetheringTypeBit(item.type)
                }
                callbackReceived.countDown()
            }
        }

        return try {
            manager.registerTetheringEventCallback(executor, callback)
            if (callbackReceived.await(timeoutSeconds, TimeUnit.SECONDS)) {
                types
            } else {
                ShizukuTetheringService.TETHERING_TYPES_UNKNOWN
            }
        } finally {
            runCatching { manager.unregisterTetheringEventCallback(callback) }
        }
    }

    fun setTetheringEnabled(
        service: Any,
        type: Int,
        enabled: Boolean,
        executor: Executor,
        timeoutSeconds: Long,
    ): Int {
        val manager = service as TetheringManager
        if (enabled) {
            return TetheringPlatformCompat.startTethering(
                service,
                type,
                executor,
                timeoutSeconds,
            )
        }
        var result = ShizukuTetheringService.RESULT_INTERNAL_ERROR
        val callbackReceived = CountDownLatch(1)
        val request = TetheringManager.TetheringRequest.Builder(type).build()
        manager.stopTethering(
            request,
            executor,
            object : TetheringManager.StopTetheringCallback {
                override fun onStopTetheringSucceeded() {
                    result = ShizukuTetheringService.RESULT_OK
                    callbackReceived.countDown()
                }

                override fun onStopTetheringFailed(error: Int) {
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
