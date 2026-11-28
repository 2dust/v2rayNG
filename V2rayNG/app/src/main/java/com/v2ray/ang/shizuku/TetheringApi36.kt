package com.v2ray.ang.shizuku

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.TetheringInterface
import android.net.TetheringManager
import android.os.Build
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Keeps API 36-only tethering types out of classes loaded on Android 13 through 15. */
@RequiresApi(Build.VERSION_CODES.BAKLAVA)
internal object TetheringApi36 {
    fun getManager(context: Context): TetheringManager =
        requireNotNull(context.getSystemService(TetheringManager::class.java)) {
            "TetheringManager is unavailable"
        }

    fun observeUpstream(
        service: Any,
        connectivityManager: ConnectivityManager,
        executor: Executor,
        onChanged: () -> Unit,
    ): TetheringUpstreamMonitor {
        val manager = service as TetheringManager
        val interfaceNames = AtomicReference<String?>(null)
        val interfaces = AtomicReference<List<ActiveTetheringInterface>?>(null)
        val interfacesReceived = CountDownLatch(1)
        val changeExecutor = newTetheringChangeExecutor()
        val callback = UpstreamCallback(connectivityManager, interfaceNames, interfaces, interfacesReceived, changeExecutor, onChanged)
        try {
            manager.registerTetheringEventCallback(executor, callback)
        } catch (error: Throwable) {
            changeExecutor.shutdownNow()
            throw error
        }
        return TetheringUpstreamMonitor(interfaceNames, interfaces, interfacesReceived) {
            runCatching { manager.unregisterTetheringEventCallback(callback) }
            changeExecutor.shutdownNow()
        }
    }

    fun startTethering(
        service: Any,
        type: Int,
        executor: Executor,
        timeoutSeconds: Long,
    ): Int {
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

    fun stopTethering(
        service: Any,
        type: Int,
        executor: Executor,
        timeoutSeconds: Long,
    ): Int {
        var result = ShizukuTetheringService.RESULT_INTERNAL_ERROR
        val callbackReceived = CountDownLatch(1)
        requestStopTethering(service, type, executor) {
            result = it
            callbackReceived.countDown()
        }
        return if (callbackReceived.await(timeoutSeconds, TimeUnit.SECONDS)) result else ShizukuTetheringService.RESULT_INTERNAL_ERROR
    }

    /** Dispatch without waiting, so emergency shutdown can stop every downstream immediately. */
    fun requestStopTethering(service: Any, type: Int, executor: Executor, onResult: (Int) -> Unit) {
        val manager = service as TetheringManager
        val request = TetheringManager.TetheringRequest.Builder(type).build()
        manager.stopTethering(
            request,
            executor,
            object : TetheringManager.StopTetheringCallback {
                override fun onStopTetheringSucceeded() {
                    onResult(ShizukuTetheringService.RESULT_OK)
                }

                override fun onStopTetheringFailed(error: Int) {
                    onResult(error)
                }
            },
        )
    }

    private class UpstreamCallback(
        private val connectivityManager: ConnectivityManager,
        private val interfaceNames: AtomicReference<String?>,
        private val tetheredInterfaces: AtomicReference<List<ActiveTetheringInterface>?>,
        private val interfacesReceived: CountDownLatch,
        private val changeExecutor: ExecutorService,
        private val onChanged: () -> Unit,
    ) : TetheringManager.TetheringEventCallback {
        override fun onTetheredInterfacesChanged(interfaces: Set<TetheringInterface>) {
            tetheredInterfaces.set(interfaces.map { ActiveTetheringInterface(it.type, it.`interface`) })
            interfacesReceived.countDown()
            notifyChanged()
        }

        /**
         * API 36 made callback registration public, but kept this callback member @SystemApi.
         * The framework invokes this same binary signature on the typed callback, so no reflection
         * is needed. Replace it with a normal `override` only after the compile SDK exposes
         * `onUpstreamChanged(Network?)`; remove it entirely only if upstream identity is no longer
         * part of the fail-closed safety check.
         */
        @Keep
        @Suppress("unused")
        fun onUpstreamChanged(network: Network?) {
            interfaceNames.set(upstreamInterfaceNames(connectivityManager, network))
            notifyChanged()
        }

        private fun notifyChanged() {
            runCatching { changeExecutor.execute(onChanged) }
        }
    }
}
