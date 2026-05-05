package com.v2ray.ang.service

import com.v2ray.ang.AngApplication
import java.io.File

object HevTunNative {
    private const val LIBRARY_NAME = "hev-socks5-tunnel"
    private const val BRIDGE_CLASS_NAME = "hev.htproxy.TProxyService"

    @Volatile
    private var initialized = false

    @Volatile
    private var loaded = false

    /**
     * Returns whether the packaged app contains the HEV native library and the
     * expected JVM bridge class. This check is safe to call during service
     * planning because it does not touch JNI at all.
     */
    fun isBundled(): Boolean {
        return hasNativeLibraryFile() && hasBridgeBindingClass()
    }

    /**
     * Loads the JNI library on demand. Call this only right before using the
     * native bridge, never during capability checks.
     */
    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            check(isBundled()) { "HEV bridge is not bundled correctly" }
            System.loadLibrary(LIBRARY_NAME)
            loaded = true
            initialized = true
        }
    }

    fun isLoaded(): Boolean = loaded

    private fun hasNativeLibraryFile(): Boolean {
        val app = runCatching { AngApplication.application }.getOrNull() ?: return false
        val nativeLibraryDir = app.applicationInfo.nativeLibraryDir ?: return false
        return File(nativeLibraryDir, System.mapLibraryName(LIBRARY_NAME)).exists()
    }

    private fun hasBridgeBindingClass(): Boolean {
        return runCatching {
            Class.forName(BRIDGE_CLASS_NAME, false, HevTunNative::class.java.classLoader)
        }.isSuccess
    }
}
