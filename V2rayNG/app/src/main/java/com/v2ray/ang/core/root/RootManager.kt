package com.v2ray.ang.core.root

import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import java.util.concurrent.TimeUnit

/**
 * Detects whether the device grants root (`su`) access.
 *
 * The result is cached after the first successful probe. Probing spawns `su` and
 * blocks, so [refreshAsync] should be used from UI code; [isRootAvailable] may block
 * and must not be called on the main thread the first time.
 */
object RootManager {

    @Volatile
    private var cached: Boolean? = null

    /** Last known result without probing. Defaults to false when never probed. */
    fun cachedRoot(): Boolean = cached ?: false

    /**
     * Returns whether root is available, probing once if unknown.
     * May block while `su` is spawned; avoid calling on the main thread before a probe.
     */
    fun isRootAvailable(forceRefresh: Boolean = false): Boolean {
        if (!forceRefresh) cached?.let { return it }
        val result = probe()
        cached = result
        return result
    }

    /** Probes root on a background thread and updates the cache, then runs [onResult] if given. */
    fun refreshAsync(onResult: ((Boolean) -> Unit)? = null) {
        Thread {
            val result = probe()
            cached = result
            onResult?.invoke(result)
        }.apply { isDaemon = true }.start()
    }

    private fun probe(): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", "id -u")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val finished = process.waitFor(10, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                LogUtil.w(AppConfig.TAG, "RootManager: su probe timed out")
                return false
            }
            val isRoot = process.exitValue() == 0 && output.lineSequence().lastOrNull()?.trim() == "0"
            LogUtil.i(AppConfig.TAG, "RootManager: root available = $isRoot")
            isRoot
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "RootManager: no root access (${e.message})")
            false
        }
    }
}
