package com.v2ray.ang.handler

import android.app.ActivityManager
import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil

/**
 * Cross-process "the tunnel is up" flag.
 *
 * The core runs in the `:daemon` process while the screens run in the main process, so neither side
 * can read the other's in-memory state. The daemon mirrors the core state into the shared
 * multi-process MMKV storage, and a reader only trusts it while the daemon process is actually
 * alive, which covers a daemon that was killed while the flag still said running.
 *
 * Used to decide whether opening the app should replay the brand animation: a session that is
 * already running gets the home screen immediately.
 */
object ServiceRuntimeState {
    private const val DAEMON_SUFFIX = ":daemon"

    /** Called from the daemon process whenever the core starts or stops. */
    fun markRunning(running: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_SERVICE_RUNNING, running)
    }

    /** True only when the last written state is "running" and the daemon process is alive. */
    fun isRunning(context: Context): Boolean {
        if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_SERVICE_RUNNING, false)) return false
        return isDaemonAlive(context)
    }

    private fun isDaemonAlive(context: Context): Boolean {
        val expected = context.packageName + DAEMON_SUFFIX
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return true
        val processes = try {
            am.runningAppProcesses
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "ServiceRuntimeState: ${e.message}")
            null
        }
        // A restricted query returns nothing; trust the flag rather than a missing answer.
        if (processes.isNullOrEmpty()) return true
        return processes.any { it.processName == expected }
    }
}
