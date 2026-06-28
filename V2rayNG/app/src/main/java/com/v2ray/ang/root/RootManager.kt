package com.v2ray.ang.root

import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Detects whether the device grants root (`su`) access.
 *
 * The result is cached after the first successful probe. Probing spawns `su` and
 * blocks, so [refresh] (a suspending call on [Dispatchers.IO]) should be used from UI
 * code; [isRootAvailable] may block and must not be called on the main thread the first time.
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

    /** Probes root off the main thread, updates the cache, and returns the result. */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val result = probe()
        cached = result
        result
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
