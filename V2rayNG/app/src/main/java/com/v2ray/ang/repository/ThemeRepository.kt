package com.v2ray.ang.repository

import android.os.Build
import com.v2ray.ang.AppConfig
import com.v2ray.ang.enums.AppThemeMode
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThemeRepository {

    private const val DEFAULT_DYNAMIC_COLOR = true

    val isDynamicColorSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private val _themeMode = MutableStateFlow(readThemeMode())
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(readDynamicColorEnabled())
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    fun getThemeMode(): AppThemeMode = _themeMode.value

    fun setThemeMode(mode: AppThemeMode) {
        if (_themeMode.value == mode) return
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode.value)
        _themeMode.value = mode
    }

    fun isDynamicColorEnabled(): Boolean = _dynamicColorEnabled.value

    fun setDynamicColorEnabled(enabled: Boolean) {
        val resolved = enabled && isDynamicColorSupported
        if (_dynamicColorEnabled.value == resolved) return
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, resolved)
        _dynamicColorEnabled.value = resolved
    }

    fun refresh() {
        _themeMode.value = readThemeMode()
        _dynamicColorEnabled.value = readDynamicColorEnabled()
    }

    private fun readThemeMode(): AppThemeMode = runCatching {
        AppThemeMode.from(
            MmkvManager.decodeSettingsString(
                AppConfig.PREF_UI_MODE_NIGHT,
                AppThemeMode.System.value
            )
        )
    }.getOrDefault(AppThemeMode.System)

    private fun readDynamicColorEnabled(): Boolean {
        if (!isDynamicColorSupported) return false
        return runCatching {
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, DEFAULT_DYNAMIC_COLOR)
        }.getOrDefault(DEFAULT_DYNAMIC_COLOR)
    }
}
