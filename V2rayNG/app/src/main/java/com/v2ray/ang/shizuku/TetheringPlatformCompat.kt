package com.v2ray.ang.shizuku

import android.annotation.SuppressLint
import android.net.TetheringManager
import android.os.Build
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/** Android 13+ tethering calls that are shared with, or hidden before, API 36. */
internal object TetheringPlatformCompat {

    fun getUpstreamInterfaceName(): String {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val process = ProcessBuilder("dumpsys", "tethering")
            .redirectErrorStream(true)
            .start()
        return try {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.firstNotNullOfOrNull(::parseUpstreamInterfaceName).orEmpty()
            }
        } finally {
            process.destroy()
        }
    }

    internal fun parseUpstreamInterfaceName(line: String): String? {
        val trimmed = line.trimStart()
        if (!trimmed.startsWith(UPSTREAM_INTERFACES_PREFIX)) return null
        val interfaces = trimmed.substringAfter(UPSTREAM_INTERFACES_PREFIX)
            .trim()
            .removePrefix("[")
            .removeSuffix("]")
        return interfaces.takeUnless { it == "null" }.orEmpty()
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

    fun getActiveTetheringTypes(service: Any): Int {
        require(Build.VERSION.SDK_INT in Build.VERSION_CODES.TIRAMISU until Build.VERSION_CODES.BAKLAVA)
        val interfaces = invokeStringList(service, "getTetheredIfaces")
            ?: error("TetheringManager.getTetheredIfaces is unavailable")
        if (interfaces.isEmpty()) return 0

        val regexesByType = mapOf(
            ShizukuTetheringService.TETHERING_TYPE_WIFI to
                compileRegexes(invokeStringList(service, "getTetherableWifiRegexs")),
            ShizukuTetheringService.TETHERING_TYPE_USB to
                compileRegexes(invokeStringList(service, "getTetherableUsbRegexs")),
            LEGACY_TETHERING_TYPE_BLUETOOTH to
                compileRegexes(invokeStringList(service, "getTetherableBluetoothRegexs")),
        )
        return interfaces.fold(0) { mask, interfaceName ->
            val type = regexesByType.entries.firstOrNull { (_, regexes) ->
                regexes.any { it.matches(interfaceName) }
            }?.key ?: inferLegacyTetheringType(interfaceName)
            if (type == null) mask else mask or tetheringTypeBit(type)
        }
    }

    fun stopTethering(service: Any, type: Int, timeoutSeconds: Long): Int {
        require(Build.VERSION.SDK_INT in Build.VERSION_CODES.TIRAMISU until Build.VERSION_CODES.BAKLAVA)
        return try {
            val method = service.javaClass.methods.firstOrNull {
                it.name == "stopTethering" &&
                    it.parameterTypes.contentEquals(arrayOf(Integer.TYPE))
            } ?: error("TetheringManager.stopTethering(int) is unavailable")
            method.invoke(service, type)

            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
            val bit = tetheringTypeBit(type)
            while (System.nanoTime() < deadline) {
                if (getActiveTetheringTypes(service) and bit == 0) {
                    return ShizukuTetheringService.RESULT_OK
                }
                Thread.sleep(LEGACY_STOP_POLL_MILLIS)
            }
            Log.e(TAG, "Timed out waiting for legacy tethering type $type to stop")
            ShizukuTetheringService.RESULT_INTERNAL_ERROR
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.e(TAG, "Interrupted while stopping legacy tethering type $type", error)
            ShizukuTetheringService.RESULT_INTERNAL_ERROR
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to stop legacy tethering type $type", error)
            ShizukuTetheringService.RESULT_INTERNAL_ERROR
        }
    }

    internal fun inferLegacyTetheringType(interfaceName: String): Int? {
        val name = interfaceName.lowercase()
        return when {
            name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("softap") ->
                ShizukuTetheringService.TETHERING_TYPE_WIFI
            name.startsWith("usb") || name.startsWith("rndis") || name.startsWith("ncm") ->
                ShizukuTetheringService.TETHERING_TYPE_USB
            name.startsWith("bt-pan") || name.startsWith("bnep") ->
                LEGACY_TETHERING_TYPE_BLUETOOTH
            else -> null
        }
    }

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

    private const val TAG = "ShizukuTethering"
    private const val UPSTREAM_INTERFACES_PREFIX = "Current upstream interface(s):"
    private const val LEGACY_TETHERING_TYPE_BLUETOOTH = 2
    private const val LEGACY_STOP_POLL_MILLIS = 100L
}

internal fun tetheringTypeBit(type: Int): Int = if (type in 0..30) 1 shl type else 0
