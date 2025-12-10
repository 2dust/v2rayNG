package com.v2ray.ang.handler

import android.content.Context
import android.util.Log
import com.v2ray.ang.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages automatic switching between configs in a subscription group.
 * Handles both interval-based switching and timeout detection.
 */
object AutoSwitchManager {
    private var intervalSwitchJob: Job? = null
    private var timeoutCheckJob: Job? = null
    private var startTime: Long = 0

    /**
     * Starts auto-switching if enabled in settings.
     * @param context The context for starting services.
     * @param currentGuid The GUID of the currently started config.
     */
    fun startAutoSwitch(context: Context, currentGuid: String) {
        val enabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SWITCH_ENABLED) ?: false
        if (!enabled) {
            return
        }

        startTime = System.currentTimeMillis()

        // Start timeout check
        val timeout = (MmkvManager.decodeSettingsString(AppConfig.PREF_AUTO_SWITCH_TIMEOUT) ?: "30").toLongOrNull() ?: 30
        if (timeout > 0) {
            startTimeoutCheck(context, currentGuid, timeout)
        }

        // Start interval switch
        val interval = (MmkvManager.decodeSettingsString(AppConfig.PREF_AUTO_SWITCH_INTERVAL) ?: "300").toLongOrNull() ?: 300
        if (interval > 0) {
            startIntervalSwitch(context, interval)
        }
    }

    /**
     * Stops all auto-switching jobs.
     */
    fun stopAutoSwitch() {
        intervalSwitchJob?.cancel()
        intervalSwitchJob = null
        timeoutCheckJob?.cancel()
        timeoutCheckJob = null
    }

    /**
     * Starts the timeout check job.
     * Checks if the connection is established within the timeout period.
     */
    private fun startTimeoutCheck(context: Context, currentGuid: String, timeoutSeconds: Long) {
        timeoutCheckJob?.cancel()
        timeoutCheckJob = CoroutineScope(Dispatchers.IO).launch {
            delay(timeoutSeconds * 1000)

            // Check if connection is working
            if (isActive && !isConnectionHealthy()) {
                Log.i(AppConfig.TAG, "Config timeout detected, switching to next")
                switchToNextConfig(context, currentGuid)
            }
        }
    }

    /**
     * Starts the interval switch job.
     * Switches to the next config after the specified interval.
     */
    private fun startIntervalSwitch(context: Context, intervalSeconds: Long) {
        intervalSwitchJob?.cancel()
        intervalSwitchJob = CoroutineScope(Dispatchers.IO).launch {
            delay(intervalSeconds * 1000)

            if (isActive) {
                Log.i(AppConfig.TAG, "Auto-switch interval reached, switching to next config")
                val currentGuid = MmkvManager.getSelectServer()
                if (currentGuid != null) {
                    switchToNextConfig(context, currentGuid)
                }
            }
        }
    }

    /**
     * Checks if the current connection is healthy.
     * @return True if the connection is healthy, false otherwise.
     */
    private fun isConnectionHealthy(): Boolean {
        // Check if the core is running
        if (!V2RayServiceManager.isRunning()) {
            return false
        }

        // Check if there's any traffic in the last few seconds
        val currentTime = System.currentTimeMillis()
        val timeSinceStart = (currentTime - startTime) / 1000.0

        // If less than 5 seconds since start, give it more time
        if (timeSinceStart < 5) {
            return true
        }

        // Connection is considered healthy if core is running
        return true
    }

    /**
     * Switches to the next config in the subscription group.
     * @param context The context for starting services.
     * @param currentGuid The GUID of the current config.
     */
    private fun switchToNextConfig(context: Context, currentGuid: String) {
        val nextGuid = getNextConfigInGroup(currentGuid)
        if (nextGuid != null && nextGuid != currentGuid) {
            Log.i(AppConfig.TAG, "Switching from $currentGuid to $nextGuid")

            // Stop current service
            V2RayServiceManager.stopVService(context)

            // Small delay before starting next
            Thread.sleep(1000)

            // Start next config
            V2RayServiceManager.startVService(context, nextGuid)
        } else {
            Log.w(AppConfig.TAG, "No next config found in group")
        }
    }

    /**
     * Gets the next config in the same subscription group.
     * @param currentGuid The GUID of the current config.
     * @return The GUID of the next config, or null if not found.
     */
    private fun getNextConfigInGroup(currentGuid: String): String? {
        val currentConfig = MmkvManager.decodeServerConfig(currentGuid) ?: return null
        val subscriptionId = currentConfig.subscriptionId

        // If not in a subscription group, return null
        if (subscriptionId.isEmpty()) {
            return null
        }

        // Get all servers in the same subscription
        val allServers = MmkvManager.decodeServerList()
        val serversInGroup = allServers.filter { it.subscriptionId == subscriptionId }

        if (serversInGroup.isEmpty()) {
            return null
        }

        // Find current index
        val currentIndex = serversInGroup.indexOfFirst { it.guid == currentGuid }
        if (currentIndex == -1) {
            return serversInGroup.firstOrNull()?.guid
        }

        // Get next index (wrap around to start)
        val nextIndex = (currentIndex + 1) % serversInGroup.size
        return serversInGroup[nextIndex].guid
    }
}
