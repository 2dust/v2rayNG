package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow

object SettingsChangeManager {
    private val _restartService = MutableStateFlow(false)
    private val _setupGroupTab = MutableStateFlow(false)

    /**
     * Keys whose changes do NOT require a core service restart.
     * These are purely UI-related or test-related settings.
     * All other keys will trigger a restart (safe default).
     */
    private val uiOnlyKeys = setOf(
        AppConfig.PREF_CONFIRM_REMOVE,
        AppConfig.PREF_START_SCAN_IMMEDIATE,
        AppConfig.PREF_DOUBLE_COLUMN_DISPLAY,
        AppConfig.PREF_GROUP_ALL_DISPLAY,
        AppConfig.PREF_LANGUAGE,
        AppConfig.PREF_UI_MODE_NIGHT,
        AppConfig.PREF_IS_BOOTED,
        AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST,
        AppConfig.PREF_AUTO_SORT_AFTER_TEST,
    )

    /**
     * Notify other modules about a setting change.
     */
    fun notifySettingChanged(key: String) {
        // Restart core service only if the changed key is not UI-only
        if (key !in uiOnlyKeys) {
            makeRestartService()
        }
        // Always refresh group tab (cheap and safe)
        makeSetupGroupTab()
    }

    // Mark restartService as requiring a restart
    fun makeRestartService() {
        _restartService.value = true
    }

    // Read and clear the restartService flag
    fun consumeRestartService(): Boolean {
        val v = _restartService.value
        _restartService.value = false
        return v
    }

    // Mark reinitGroupTab as requiring tab reinitialization
    fun makeSetupGroupTab() {
        _setupGroupTab.value = true
    }

    // Read and clear the reinitGroupTab flag
    fun consumeSetupGroupTab(): Boolean {
        val v = _setupGroupTab.value
        _setupGroupTab.value = false
        return v
    }
}
