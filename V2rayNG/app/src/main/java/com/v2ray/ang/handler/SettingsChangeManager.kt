package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages global flags for actions triggered by setting changes.
 * Uses AtomicBoolean for thread-safe consume operations.
 */
object SettingsChangeManager {

    private val restartService = AtomicBoolean(false)
    private val setupGroupTab = AtomicBoolean(false)

    // Keys that affect only UI behavior and do not require core service restart.
    private val uiOnlyKeys = setOf(
        AppConfig.PREF_CONFIRM_REMOVE,
        AppConfig.PREF_DOUBLE_COLUMN_DISPLAY,
        AppConfig.PREF_GROUP_ALL_DISPLAY,
        AppConfig.PREF_LANGUAGE,
        AppConfig.PREF_UI_MODE_NIGHT,
        AppConfig.PREF_DYNAMIC_COLOR,
        AppConfig.PREF_IS_BOOTED,
        AppConfig.PREF_DELAY_TEST_URL,
        AppConfig.PREF_REAL_PING_CONCURRENCY,
        AppConfig.PREF_IP_API_URL
    )

    /** Whether the given key only affects UI and never requires a core restart. */
    fun isUiOnly(key: String): Boolean = key in uiOnlyKeys

    /**
     * Called when a setting value changes.
     * Triggers service restart if the key is not UI-only, and always refreshes UI tabs.
     */
    fun notifySettingChanged(key: String) {
        if (key !in uiOnlyKeys) {
            makeRestartService()
        }
        makeSetupGroupTab()
    }

    fun makeRestartService() {
        restartService.set(true)
    }

    /**
     * Atomically consumes the restart flag.
     * @return true if a restart was requested, false otherwise.
     */
    fun consumeRestartService(): Boolean =
        restartService.compareAndSet(true, false)

    fun makeSetupGroupTab() {
        setupGroupTab.set(true)
    }

    /**
     * Atomically consumes the setup-group-tab flag.
     * @return true if UI refresh was requested, false otherwise.
     */
    fun consumeSetupGroupTab(): Boolean =
        setupGroupTab.compareAndSet(true, false)
}
