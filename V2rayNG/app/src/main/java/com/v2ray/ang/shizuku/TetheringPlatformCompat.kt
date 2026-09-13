package com.v2ray.ang.shizuku

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import androidx.annotation.ChecksSdkIntAtLeast
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** TetheringManager compatibility for Android 13 through 15. */
internal object TetheringPlatformCompat {

    // Remove WrongConstant only when TRANSPORT_TEST enters the public SDK, or when the protected
    // upstream stops being an Android test network and this request is deleted with it.
    @SuppressLint("WrongConstant")
    fun testNetworkRequest(): NetworkRequest = NetworkRequest.Builder()
        .addTransportType(TRANSPORT_TEST)
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
        .build()

    fun observeUpstreamLegacy(
        service: Any,
        connectivityManager: ConnectivityManager,
        executor: Executor,
        onChanged: () -> Unit,
    ): TetheringUpstreamMonitor {
        require(Build.VERSION.SDK_INT in Build.VERSION_CODES.TIRAMISU until Build.VERSION_CODES.BAKLAVA)
        // API 33-35 hide this callback and the downstream-state APIs used below. Delete this
        // reflection branch only when the feature's minimum supported API becomes 36; API 36 made
        // the required TetheringManager types public, but that does not help older installations.
        val callbackClass = Class.forName(TETHERING_EVENT_CALLBACK_CLASS)
        check(callbackClass.isInterface) { "Tethering event callback is not an interface" }
        val interfaceClass = Class.forName("android.net.TetheringInterface")
        val getType = interfaceClass.getMethod("getType")
        val getInterface = interfaceClass.getMethod("getInterface")
        val interfaceNames = AtomicReference<String?>(null)
        val interfaces = AtomicReference<List<ActiveTetheringInterface>?>(null)
        val interfacesReceived = CountDownLatch(1)
        val changeExecutor = newTetheringChangeExecutor()
        val callback = Proxy.newProxyInstance(
            TetheringPlatformCompat::class.java.classLoader,
            arrayOf(callbackClass),
        ) { proxy, method, arguments ->
            when (method.name) {
                "onUpstreamChanged" -> {
                    val network = arguments?.firstOrNull() as? Network
                    interfaceNames.set(upstreamInterfaceNames(connectivityManager, network))
                    runCatching { changeExecutor.execute(onChanged) }
                    null
                }
                "onTetheredInterfacesChanged" -> {
                    // API 33 already supplies Set<TetheringInterface>; only its SDK visibility
                    // changes in API 36. Read that event, not getTetheredIfaces(): the manager's
                    // separate internal callback may not have updated its cache yet.
                    interfaces.set(runCatching {
                        val downstreams = arguments?.firstOrNull() as? Set<*>
                            ?: error("Tethering callback did not supply interface identities")
                        downstreams.map { downstream ->
                            ActiveTetheringInterface(getType.invoke(downstream) as Int, getInterface.invoke(downstream) as String)
                        }
                    }
                        .onFailure { Log.e("TetheringPlatformCompat", "Unable to identify active downstreams", it) }
                        .getOrNull())
                    interfacesReceived.countDown()
                    runCatching { changeExecutor.execute(onChanged) }
                    null
                }
                "equals" -> proxy === arguments?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "v2rayNG tethering upstream callback"
                else -> null
            }
        }
        val register = service.javaClass.methods.firstOrNull {
            it.name == "registerTetheringEventCallback" &&
                it.parameterTypes.contentEquals(arrayOf(Executor::class.java, callbackClass))
        } ?: error("TetheringManager.registerTetheringEventCallback is unavailable")
        val unregister = service.javaClass.methods.firstOrNull {
            it.name == "unregisterTetheringEventCallback" &&
                it.parameterTypes.contentEquals(arrayOf(callbackClass))
        } ?: error("TetheringManager.unregisterTetheringEventCallback is unavailable")
        try {
            register.invoke(service, executor, callback)
        } catch (error: Throwable) {
            changeExecutor.shutdownNow()
            throw error
        }
        return TetheringUpstreamMonitor(
            interfaceNames,
            interfaces,
            interfacesReceived,
        ) {
            runCatching { unregister.invoke(service, callback) }
            changeExecutor.shutdownNow()
        }
    }

    internal fun isProtectedUpstream(actual: String, expected: String): Boolean {
        if (expected.isBlank()) return false
        // The callback reports the selected network's LinkProperties.interfaceName, not a list.
        return actual == expected
    }

    fun startTethering(
        service: Any,
        type: Int,
        executor: Executor,
        timeoutSeconds: Long,
    ): Int {
        require(Build.VERSION.SDK_INT in Build.VERSION_CODES.TIRAMISU until Build.VERSION_CODES.BAKLAVA)
        // API 33-35 contain this callback API but keep all three participating types out of the
        // public SDK. Reflection prevents those hidden types from leaking into the stable app
        // contract. Delete this branch when the feature's minimum supported API becomes 36.
        val requestClass = Class.forName(TETHERING_REQUEST_CLASS)
        val requestBuilderClass = Class.forName(TETHERING_REQUEST_BUILDER_CLASS)
        val callbackClass = Class.forName(START_TETHERING_CALLBACK_CLASS)
        check(callbackClass.isInterface) { "Start tethering callback is not an interface" }
        val request = requestBuilderClass.getConstructor(Integer.TYPE).newInstance(type).let { builder ->
            requestBuilderClass.getMethod("build").invoke(builder)
        }
        var result = ShizukuTetheringService.RESULT_INTERNAL_ERROR
        val callbackReceived = CountDownLatch(1)
        val callback = Proxy.newProxyInstance(
            TetheringPlatformCompat::class.java.classLoader,
            arrayOf(callbackClass),
        ) { proxy, method, arguments ->
            when (method.name) {
                "onTetheringStarted" -> {
                    result = ShizukuTetheringService.RESULT_OK
                    callbackReceived.countDown()
                    null
                }
                "onTetheringFailed" -> {
                    result = (arguments?.firstOrNull() as? Number)?.toInt()
                        ?: ShizukuTetheringService.RESULT_INTERNAL_ERROR
                    callbackReceived.countDown()
                    null
                }
                "equals" -> proxy === arguments?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "v2rayNG start tethering callback"
                else -> null
            }
        }
        val start = service.javaClass.methods.firstOrNull {
            it.name == "startTethering" && it.parameterTypes.contentEquals(
                arrayOf(requestClass, Executor::class.java, callbackClass),
            )
        } ?: error("TetheringManager.startTethering is unavailable")
        start.invoke(service, request, executor, callback)
        return if (callbackReceived.await(timeoutSeconds, TimeUnit.SECONDS)) {
            result
        } else {
            ShizukuTetheringService.RESULT_INTERNAL_ERROR
        }
    }

    // The service performs the shared postcondition check after this legacy API returns, so
    // Android 13-15 follows the same single bounded wait as newer releases.
    fun stopTethering(service: Any, type: Int): Int {
        require(Build.VERSION.SDK_INT in Build.VERSION_CODES.TIRAMISU until Build.VERSION_CODES.BAKLAVA)
        val method = service.javaClass.methods.firstOrNull {
            it.name == "stopTethering" &&
                it.parameterTypes.contentEquals(arrayOf(Integer.TYPE))
        } ?: error("TetheringManager.stopTethering(int) is unavailable")
        method.invoke(service, type)
        return ShizukuTetheringService.RESULT_OK
    }

    private const val TETHERING_EVENT_CALLBACK_CLASS =
        "android.net.TetheringManager\$TetheringEventCallback"
    private const val TETHERING_REQUEST_CLASS = "android.net.TetheringManager\$TetheringRequest"
    private const val TETHERING_REQUEST_BUILDER_CLASS =
        "android.net.TetheringManager\$TetheringRequest\$Builder"
    private const val START_TETHERING_CALLBACK_CLASS =
        "android.net.TetheringManager\$StartTetheringCallback"
    private const val TRANSPORT_TEST = 7
}

internal class TetheringUpstreamMonitor(
    private val interfaceNames: AtomicReference<String?>,
    private val interfaces: AtomicReference<List<ActiveTetheringInterface>?>,
    private val interfacesReceived: CountDownLatch,
    private val closeAction: () -> Unit,
) : AutoCloseable {
    val currentInterfaceNames: String?
        get() = interfaceNames.get()

    val currentInterfaces: List<ActiveTetheringInterface>?
        get() = interfaces.get()

    fun awaitInterfaces(timeoutSeconds: Long): List<ActiveTetheringInterface> {
        check(interfacesReceived.await(timeoutSeconds, TimeUnit.SECONDS)) { "Timed out reading tethered interfaces" }
        return checkNotNull(currentInterfaces) { "Unable to identify active tethering interfaces" }
    }

    override fun close() = closeAction()
}

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.BAKLAVA)
internal fun usesPublicTetheringApi(): Boolean = isPublicTetheringApiLevel(Build.VERSION.SDK_INT)

internal fun isPublicTetheringApiLevel(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.BAKLAVA

internal fun upstreamInterfaceNames(connectivityManager: ConnectivityManager, network: Network?): String {
    val properties = network?.let(connectivityManager::getLinkProperties) ?: return ""
    return properties.interfaceName.orEmpty()
}

internal fun newTetheringChangeExecutor(): ExecutorService =
    Executors.newSingleThreadExecutor { command ->
        Thread(command, "TetheringUpstreamMonitor").apply { isDaemon = true }
    }

internal data class ActiveTetheringInterface(
    val type: Int,
    val name: String,
)

internal fun tetheringTypeBit(type: Int): Int = if (type in 0..30) 1 shl type else 0
