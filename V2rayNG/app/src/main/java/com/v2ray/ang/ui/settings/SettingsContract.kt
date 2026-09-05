package com.v2ray.ang.ui.settings

import androidx.compose.runtime.Immutable
import com.v2ray.ang.AppConfig.VPN
import com.v2ray.ang.repository.BoolPref
import com.v2ray.ang.repository.StringPref
import com.v2ray.ang.ui.base.BaseAction
import com.v2ray.ang.ui.base.BaseEvent
import com.v2ray.ang.ui.base.BaseUiState

@Immutable
data class SettingsUiState(
    val bools: Map<BoolPref, Boolean> = emptyMap(),
    val strings: Map<StringPref, String> = emptyMap(),
    /** Any preference changed, so the caller is notified on exit. */
    val changed: Boolean = false,
    /** At least one changed preference is not UI-only, so the core must restart. */
    val restartService: Boolean = false,
    /** Dynamic color support and current state (read from ThemeRepository) */
    val dynamicColorSupported: Boolean = false,
) : BaseUiState {

    operator fun get(pref: BoolPref): Boolean = bools[pref] ?: pref.default
    operator fun get(pref: StringPref): String = strings[pref] ?: pref.default

    /** Derived instead of a loading field: renders nothing rather than a frame of defaults. */
    val loaded: Boolean get() = bools.isNotEmpty()

    val isVpn: Boolean get() = get(StringPref.MODE) == VPN
    val hevTunnel: Boolean get() = isVpn && get(BoolPref.USE_HEV_TUNNEL)

    /** hev-tunnel cannot work without the local proxy, so the switch is locked on. */
    val localProxyForced: Boolean get() = hevTunnel
    val localProxy: Boolean get() = get(BoolPref.ENABLE_LOCAL_PROXY) || localProxyForced
    val xudpQuicEnabled: Boolean
        get() = get(BoolPref.MUX_ENABLED) &&
            (get(StringPref.MUX_XUDP_CONCURRENCY).toIntOrNull() ?: 8) >= 0
}

sealed interface SettingsAction : BaseAction {
    data object Back : SettingsAction
    data class BoolChanged(val pref: BoolPref, val value: Boolean) : SettingsAction
    data class TextChanged(val pref: StringPref, val value: String) : SettingsAction
    data object ModeHelpClicked : SettingsAction
}

sealed interface SettingsEvent : BaseEvent.Platform {
    /** AppCompat switches the per-app locale on the main thread and recreates the Activity. */
    data class ApplyLanguage(val code: String) : SettingsEvent
}
