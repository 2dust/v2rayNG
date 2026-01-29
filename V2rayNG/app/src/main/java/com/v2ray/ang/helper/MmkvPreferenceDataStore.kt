package com.v2ray.ang.helper

import androidx.preference.PreferenceDataStore
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager

/**
 * PreferenceDataStore implementation that bridges AndroidX Preference framework to MMKV storage.
 * This ensures that all Preference UI operations read/write directly from/to MMKV,
 * avoiding inconsistencies between SharedPreferences and MMKV.
 */
class MmkvPreferenceDataStore : PreferenceDataStore() {

    override fun putString(key: String, value: String?) {
        MmkvManager.encodeSettings(key, value)
        notifySettingChanged(key)
    }

    override fun getString(key: String, defaultValue: String?): String? {
        return MmkvManager.decodeSettingsString(key, defaultValue)
    }

    override fun putInt(key: String, value: Int) {
        MmkvManager.encodeSettings(key, value)
        notifySettingChanged(key)
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return MmkvManager.decodeSettingsInt(key, defaultValue)
    }

    override fun putLong(key: String, value: Long) {
        MmkvManager.encodeSettings(key, value)
        notifySettingChanged(key)
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        return MmkvManager.decodeSettingsLong(key, defaultValue)
    }

    override fun putFloat(key: String, value: Float) {
        MmkvManager.encodeSettings(key, value)
        notifySettingChanged(key)
    }

    override fun getFloat(key: String, defaultValue: Float): Float {
        return MmkvManager.decodeSettingsFloat(key, defaultValue)
    }

    override fun putBoolean(key: String, value: Boolean) {
        MmkvManager.encodeSettings(key, value)
        notifySettingChanged(key)
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return MmkvManager.decodeSettingsBool(key, defaultValue)
    }

    override fun putStringSet(key: String, values: MutableSet<String>?) {
        if (values == null) {
            MmkvManager.encodeSettings(key, null as String?)
        } else {
            MmkvManager.encodeSettings(key, values)
        }
        notifySettingChanged(key)
    }

    override fun getStringSet(key: String, defaultValues: MutableSet<String>?): MutableSet<String>? {
        return MmkvManager.decodeSettingsStringSet(key) ?: defaultValues
    }

    // Internal helper: notify other modules about setting changes
    private fun notifySettingChanged(key: String) {
        // Call SettingsManager.setNightMode if UI mode changed
        if (key == AppConfig.PREF_UI_MODE_NIGHT) {
            SettingsManager.setNightMode()
        }
        // Notify listeners that require service restart or reinit
        SettingsChangeManager.makeRestartService()
    }
}