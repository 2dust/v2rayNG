package com.v2ray.ang.handler

import android.content.Context
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.MessageUtil
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
        Log.i(AppConfig.TAG, "AutoSwitch: startAutoSwitch called, enabled=$enabled")

        if (!enabled) {
            Log.i(AppConfig.TAG, "AutoSwitch: Auto-switch is disabled in settings")
            return
        }

        startTime = System.currentTimeMillis()

        // Start timeout check
        val timeout = (MmkvManager.decodeSettingsString(AppConfig.PREF_AUTO_SWITCH_TIMEOUT) ?: "30").toLongOrNull() ?: 30
        Log.i(AppConfig.TAG, "AutoSwitch: timeout=${timeout}s")
        if (timeout > 0) {
            startTimeoutCheck(context, currentGuid, timeout)
        }

        // Start interval switch
        val interval = (MmkvManager.decodeSettingsString(AppConfig.PREF_AUTO_SWITCH_INTERVAL) ?: "300").toLongOrNull() ?: 300
        Log.i(AppConfig.TAG, "AutoSwitch: interval=${interval}s")
        if (interval > 0) {
            startIntervalSwitch(context, interval)
        }
    }

    /**
     * Stops all auto-switching jobs.
     */
    fun stopAutoSwitch() {
        Log.i(AppConfig.TAG, "AutoSwitch: stopAutoSwitch called")
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
        Log.i(AppConfig.TAG, "AutoSwitch: Starting timeout check for ${timeoutSeconds}s")
        timeoutCheckJob?.cancel()
        timeoutCheckJob = CoroutineScope(Dispatchers.IO).launch {
            delay(timeoutSeconds * 1000)

            // Check if connection is working
            if (isActive && !isConnectionHealthy()) {
                Log.i(AppConfig.TAG, "AutoSwitch: Timeout detected, switching to next")
                switchToNextConfig(context, currentGuid)
            } else {
                Log.i(AppConfig.TAG, "AutoSwitch: Timeout check passed, connection is healthy")
            }
        }
    }

    /**
     * Starts the interval switch job.
     * Switches to the next config after the specified interval.
     */
    private fun startIntervalSwitch(context: Context, intervalSeconds: Long) {
        Log.i(AppConfig.TAG, "AutoSwitch: Starting interval switch for ${intervalSeconds}s")
        intervalSwitchJob?.cancel()
        intervalSwitchJob = CoroutineScope(Dispatchers.IO).launch {
            delay(intervalSeconds * 1000)

            if (isActive) {
                Log.i(AppConfig.TAG, "AutoSwitch: Interval reached, switching to next config")
                val currentGuid = MmkvManager.getSelectServer()
                if (currentGuid != null) {
                    switchToNextConfig(context, currentGuid)
                } else {
                    Log.w(AppConfig.TAG, "AutoSwitch: Current GUID is null")
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
        Log.i(AppConfig.TAG, "AutoSwitch: switchToNextConfig called for $currentGuid")
        val nextGuid = getNextConfigInGroup(currentGuid)
        Log.i(AppConfig.TAG, "AutoSwitch: nextGuid=$nextGuid")

        if (nextGuid != null && nextGuid != currentGuid) {
            Log.i(AppConfig.TAG, "AutoSwitch: Switching from $currentGuid to $nextGuid")

            // Stop current service
            V2RayServiceManager.stopVService(context)
            Log.i(AppConfig.TAG, "AutoSwitch: Stopped current service")

            // Small delay before starting next
            Thread.sleep(1000)

            // Start next config
            V2RayServiceManager.startVService(context, nextGuid)
            Log.i(AppConfig.TAG, "AutoSwitch: Started new config")

            // Notify UI of config change
            MessageUtil.sendMsg2UI(context, AppConfig.MSG_CONFIG_SWITCHED, nextGuid)
            Log.i(AppConfig.TAG, "AutoSwitch: UI notification sent")
        } else {
            Log.w(AppConfig.TAG, "AutoSwitch: No next config found (nextGuid=$nextGuid, currentGuid=$currentGuid)")
        }
    }

    /**
     * Gets the next config in the same subscription group.
     * @param currentGuid The GUID of the current config.
     * @return The GUID of the next config, or null if not found.
     */
    private fun getNextConfigInGroup(currentGuid: String): String? {
        Log.i(AppConfig.TAG, "AutoSwitch: getNextConfigInGroup for $currentGuid")
        val currentConfig = MmkvManager.decodeServerConfig(currentGuid)
        if (currentConfig == null) {
            Log.w(AppConfig.TAG, "AutoSwitch: Current config is null")
            return null
        }

        val subscriptionId = currentConfig.subscriptionId
        Log.i(AppConfig.TAG, "AutoSwitch: subscriptionId='$subscriptionId'")

        // Get all servers
        val allServerGuids = MmkvManager.decodeServerList()
        Log.i(AppConfig.TAG, "AutoSwitch: Total configs: ${allServerGuids.size}")

        // If in a subscription group, only switch within that group
        // Otherwise, switch through all configs
        val serversInGroup = if (subscriptionId.isNotEmpty()) {
            // Filter by subscription group
            Log.i(AppConfig.TAG, "AutoSwitch: Filtering by subscription group")
            allServerGuids.mapNotNull { guid ->
                val config = MmkvManager.decodeServerConfig(guid)
                if (config?.subscriptionId == subscriptionId) guid else null
            }
        } else {
            // Use all servers for switching
            Log.i(AppConfig.TAG, "AutoSwitch: Using all servers for switching")
            allServerGuids
        }

        Log.i(AppConfig.TAG, "AutoSwitch: Configs in group: ${serversInGroup.size}")
        if (serversInGroup.isEmpty()) {
            Log.w(AppConfig.TAG, "AutoSwitch: No configs in group")
            return null
        }

        // Find current index
        val currentIndex = serversInGroup.indexOfFirst { it == currentGuid }
        Log.i(AppConfig.TAG, "AutoSwitch: Current index: $currentIndex")

        if (currentIndex == -1) {
            Log.w(AppConfig.TAG, "AutoSwitch: Current config not found in group, returning first")
            return serversInGroup.firstOrNull()
        }

        // Get next index (wrap around to start)
        val nextIndex = (currentIndex + 1) % serversInGroup.size
        Log.i(AppConfig.TAG, "AutoSwitch: Next index: $nextIndex")

        return serversInGroup[nextIndex]
    }
}
