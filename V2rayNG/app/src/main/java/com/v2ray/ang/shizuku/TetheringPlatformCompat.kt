package com.v2ray.ang.shizuku

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TetheringManager
import android.os.Build
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Android 13+ tethering calls that are shared with, or hidden before, API 36. */
internal object TetheringPlatformCompat {

    @SuppressLint("WrongConstant") // TRANSPORT_TEST is a hidden transport type.
    fun testNetworkRequest(): NetworkRequest = NetworkRequest.Builder()
        .addTransportType(TRANSPORT_TEST)
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
        .build()

    fun observeUpstream(
        service: Any,
        connectivityManager: ConnectivityManager,
        executor: Executor,
        onChanged: () -> Unit,
    ): TetheringUpstreamMonitor {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        // This callback exists on every supported release, but its type is hidden from the public
        // SDK. Implement the runtime interface directly so fail-closed protection receives changes
        // immediately without polling dumpsys or depending on OEM-specific diagnostic text.
        val callbackClass = Class.forName(TETHERING_EVENT_CALLBACK_CLASS)
        check(callbackClass.isInterface) { "Tethering event callback is not an interface" }
        val interfaceNames = AtomicReference<String?>(null)
        val changeExecutor = Executors.newSingleThreadExecutor { command ->
            Thread(command, UPSTREAM_MONITOR_THREAD).apply { isDaemon = true }
        }
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
            service,
            callback,
            unregister,
            changeExecutor,
            interfaceNames,
        )
    }

    private fun upstreamInterfaceNames(
        connectivityManager: ConnectivityManager,
        network: Network?,
    ): String {
        val properties = network?.let(connectivityManager::getLinkProperties) ?: return ""
        return properties.interfaceName.orEmpty()
    }

    internal fun isProtectedUpstream(actual: String, expected: String): Boolean {
        if (expected.isBlank()) return false
        val interfaces = actual.split(',').map(String::trim).filter(String::isNotEmpty)
        // Tethering may expose multiple stacked upstream interfaces. Accept the state only when
        // every reported path is the owned test TUN; a mixed "testtun, physical" state can leak.
        return interfaces.isNotEmpty() && interfaces.all { it == expected }
    }

    @SuppressLint("NewApi")
    fun startTethering(
        service: Any,
        type: Int,
        executor: Executor,
        timeoutSeconds: Long,
    ): Int {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
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

    fun getTetheredInterfaces(service: Any): List<ActiveTetheringInterface> {
        require(Build.VERSION.SDK_INT in Build.VERSION_CODES.TIRAMISU until Build.VERSION_CODES.BAKLAVA)
        val interfaces = invokeStringList(service, "getTetheredIfaces")
            ?: error("TetheringManager.getTetheredIfaces is unavailable")

        val regexesByType = mapOf(
            ShizukuTetheringService.TETHERING_TYPE_WIFI to
                compileRegexes(invokeStringList(service, "getTetherableWifiRegexs")),
            ShizukuTetheringService.TETHERING_TYPE_USB to
                compileRegexes(invokeStringList(service, "getTetherableUsbRegexs")),
            LEGACY_TETHERING_TYPE_BLUETOOTH to
                compileRegexes(invokeStringList(service, "getTetherableBluetoothRegexs")),
        )
        return interfaces.map { interfaceName ->
            ActiveTetheringInterface(requireLegacyTetheringType(interfaceName, regexesByType), interfaceName)
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

    internal fun inferLegacyTetheringType(interfaceName: String): Int? {
        val name = interfaceName.lowercase()
        return when {
            name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("softap") ->
                ShizukuTetheringService.TETHERING_TYPE_WIFI
            name.startsWith("usb") || name.startsWith("rndis") ->
                ShizukuTetheringService.TETHERING_TYPE_USB
            name.startsWith("bt-pan") || name.startsWith("bnep") ->
                LEGACY_TETHERING_TYPE_BLUETOOTH
            name.startsWith("p2p") -> LEGACY_TETHERING_TYPE_WIFI_P2P
            name.startsWith("ncm") -> LEGACY_TETHERING_TYPE_NCM
            name.startsWith("eth") -> LEGACY_TETHERING_TYPE_ETHERNET
            else -> null
        }
    }

    internal fun requireLegacyTetheringType(interfaceName: String, regexesByType: Map<Int, List<Regex>>): Int =
        inferLegacyTetheringType(interfaceName)
        ?: regexesByType.entries.firstOrNull { (_, regexes) ->
            regexes.any { it.matches(interfaceName) }
        }?.key
        // Never omit an active downstream: doing so could let callers release its protected upstream.
        ?: error("Unknown active tethering interface: $interfaceName")

    private fun invokeStringList(service: Any, methodName: String): List<String>? {
        val method = service.javaClass.methods.firstOrNull {
            it.name == methodName && it.parameterCount == 0
        } ?: return null
        return when (val result = method.invoke(service)) {
            null -> null
            is Array<*> -> result.filterIsInstance<String>()
            is Collection<*> -> result.filterIsInstance<String>()
            else -> null
        }
    }

    private fun compileRegexes(patterns: List<String>?): List<Regex> = patterns.orEmpty()
        .mapNotNull { pattern -> runCatching { Regex(pattern) }.getOrNull() }

    private const val TETHERING_EVENT_CALLBACK_CLASS =
        "android.net.TetheringManager\$TetheringEventCallback"
    private const val UPSTREAM_MONITOR_THREAD = "TetheringUpstreamMonitor"
    private const val TRANSPORT_TEST = 7
    private const val LEGACY_TETHERING_TYPE_BLUETOOTH = 2
    private const val LEGACY_TETHERING_TYPE_WIFI_P2P = 3
    private const val LEGACY_TETHERING_TYPE_NCM = 4
    private const val LEGACY_TETHERING_TYPE_ETHERNET = 5
}

internal class TetheringUpstreamMonitor(
    private val service: Any,
    private val callback: Any,
    private val unregister: Method,
    private val changeExecutor: ExecutorService,
    private val interfaceNames: AtomicReference<String?>,
) : AutoCloseable {
    val currentInterfaceNames: String?
        get() = interfaceNames.get()

    override fun close() {
        runCatching { unregister.invoke(service, callback) }
        changeExecutor.shutdownNow()
    }
}

internal data class ActiveTetheringInterface(
    val type: Int,
    val name: String,
)

internal fun tetheringTypeBit(type: Int): Int = if (type in 0..30) 1 shl type else 0
