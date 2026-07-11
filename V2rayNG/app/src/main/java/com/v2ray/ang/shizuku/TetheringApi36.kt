package com.v2ray.ang.shizuku

import android.net.TetheringInterface
import android.net.TetheringManager
import androidx.annotation.RequiresApi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/** Keeps API 36-only tethering types out of classes loaded on Android 11 through 15. */
@RequiresApi(36)
internal object TetheringApi36 {
    fun getActiveTetheringTypes(
        manager: TetheringManager,
        executor: Executor,
        timeoutSeconds: Long,
    ): Int {
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

    fun stopTethering(
        manager: TetheringManager,
        request: TetheringManager.TetheringRequest,
        executor: Executor,
        timeoutSeconds: Long,
    ): Int {
        var result = ShizukuTetheringService.RESULT_INTERNAL_ERROR
        val callbackReceived = CountDownLatch(1)
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

    private fun tetheringTypeBit(type: Int): Int = if (type in 0..30) 1 shl type else 0
}
