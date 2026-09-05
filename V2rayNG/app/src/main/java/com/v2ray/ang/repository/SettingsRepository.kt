package com.v2ray.ang.repository

import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.VPN
import com.v2ray.ang.enums.AppThemeMode
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.root.RootManager

enum class BoolPref(val key: String, val default: Boolean) {
    SPEED_ENABLED(AppConfig.PREF_SPEED_ENABLED, false),
    CONFIRM_REMOVE(AppConfig.PREF_CONFIRM_REMOVE, false),
    DOUBLE_COLUMN_DISPLAY(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, false),
    GROUP_ALL_DISPLAY(AppConfig.PREF_GROUP_ALL_DISPLAY, false),

    IPV6_ENABLED(AppConfig.PREF_IPV6_ENABLED, false),
    PREFER_IPV6(AppConfig.PREF_PREFER_IPV6, false),
    LOCAL_DNS(AppConfig.PREF_LOCAL_DNS_ENABLED, false),
    FAKE_DNS(AppConfig.PREF_FAKE_DNS_ENABLED, false),
    APPEND_HTTP_PROXY(AppConfig.PREF_APPEND_HTTP_PROXY, false),
    USE_HEV_TUNNEL(AppConfig.PREF_USE_HEV_TUNNEL, true),

    SNIFFING_ENABLED(AppConfig.PREF_SNIFFING_ENABLED, true),
    ROUTE_ONLY(AppConfig.PREF_ROUTE_ONLY_ENABLED, false),
    ENABLE_LOCAL_PROXY(AppConfig.PREF_ENABLE_LOCAL_PROXY, true),
    PROXY_SHARING(AppConfig.PREF_PROXY_SHARING, false),
    DYNAMIC_SOCKS_PORT(AppConfig.PREF_DYNAMIC_SOCKS_PORT, false),
    SOCKS_ENABLE_UDP(AppConfig.PREF_SOCKS_ENABLE_UDP, false),

    MUX_ENABLED(AppConfig.PREF_MUX_ENABLED, false),
    FRAGMENT_ENABLED(AppConfig.PREF_FRAGMENT_ENABLED, false),

    IS_BOOTED(AppConfig.PREF_IS_BOOTED, false),
    ROOT_MODE_ENABLE(AppConfig.PREF_ROOT_MODE_ENABLE, false),
    ROOT_LAN_SHARING(AppConfig.PREF_ROOT_LAN_SHARING, false),

    DYNAMIC_COLOR(AppConfig.PREF_DYNAMIC_COLOR, true);

    /** Single source for "does this need a core restart", shared with legacy consumers. */
    val uiOnly: Boolean get() = SettingsChangeManager.isUiOnly(key)
}

/** String-valued preferences (plain text, number text or single-choice list). */
enum class StringPref(val key: String, val default: String) {
    LANGUAGE(AppConfig.PREF_LANGUAGE, "auto"),
    UI_MODE_NIGHT(AppConfig.PREF_UI_MODE_NIGHT, "0"),

    VPN_DNS(AppConfig.PREF_VPN_DNS, ""),
    VPN_BYPASS_LAN(AppConfig.PREF_VPN_BYPASS_LAN, "0"),
    VPN_INTERFACE_ADDRESS(AppConfig.PREF_VPN_INTERFACE_ADDRESS_CONFIG_INDEX, "0"),
    VPN_MTU(AppConfig.PREF_VPN_MTU, ""),
    HEV_LOGLEVEL(AppConfig.PREF_HEV_TUNNEL_LOGLEVEL, "warning"),
    HEV_RW_TIMEOUT(AppConfig.PREF_HEV_TUNNEL_RW_TIMEOUT, ""),

    SOCKS_PORT(AppConfig.PREF_SOCKS_PORT, ""),
    SOCKS_USERNAME(AppConfig.PREF_SOCKS_USERNAME, ""),
    SOCKS_PASSWORD(AppConfig.PREF_SOCKS_PASSWORD, ""),
    REMOTE_DNS(AppConfig.PREF_REMOTE_DNS, ""),
    DOMESTIC_DNS(AppConfig.PREF_DOMESTIC_DNS, ""),
    DNS_HOSTS(AppConfig.PREF_DNS_HOSTS, ""),
    CORE_LOGLEVEL(AppConfig.PREF_LOGLEVEL, "warning"),
    OUTBOUND_RESOLVE(AppConfig.PREF_OUTBOUND_DOMAIN_RESOLVE_METHOD, "0"),

    MUX_CONCURRENCY(AppConfig.PREF_MUX_CONCURRENCY, "8"),
    MUX_XUDP_CONCURRENCY(AppConfig.PREF_MUX_XUDP_CONCURRENCY, "8"),
    MUX_XUDP_QUIC(AppConfig.PREF_MUX_XUDP_QUIC, "reject"),

    FRAGMENT_PACKETS(AppConfig.PREF_FRAGMENT_PACKETS, "tlshello"),
    FRAGMENT_LENGTH(AppConfig.PREF_FRAGMENT_LENGTH, "50-100"),
    FRAGMENT_INTERVAL(AppConfig.PREF_FRAGMENT_INTERVAL, "10-20"),
    FRAGMENT_MAXSPLIT(AppConfig.PREF_FRAGMENT_MAXSPLIT, "10"),

    OBS_LEAST_PING_INTERVAL(
        AppConfig.PREF_OBSERVATORY_LEAST_PING_INTERVAL, AppConfig.OBSERVATORY_LEAST_PING_INTERVAL
    ),
    OBS_LEAST_LOAD_INTERVAL(
        AppConfig.PREF_OBSERVATORY_LEAST_LOAD_INTERVAL, AppConfig.OBSERVATORY_LEAST_LOAD_INTERVAL
    ),
    OBS_LEAST_LOAD_METHOD(
        AppConfig.PREF_OBSERVATORY_LEAST_LOAD_METHOD, AppConfig.OBSERVATORY_LEAST_LOAD_METHOD
    ),
    OBS_LEAST_LOAD_SAMPLING(
        AppConfig.PREF_OBSERVATORY_LEAST_LOAD_SAMPLING, AppConfig.OBSERVATORY_LEAST_LOAD_SAMPLING
    ),
    OBS_LEAST_LOAD_TIMEOUT(
        AppConfig.PREF_OBSERVATORY_LEAST_LOAD_TIMEOUT, AppConfig.OBSERVATORY_LEAST_LOAD_TIMEOUT
    ),

    DELAY_TEST_URL(AppConfig.PREF_DELAY_TEST_URL, ""),
    REAL_PING_CONCURRENCY(AppConfig.PREF_REAL_PING_CONCURRENCY, "16"),
    IP_API_URL(AppConfig.PREF_IP_API_URL, ""),

    MODE(AppConfig.PREF_MODE, VPN);

    val uiOnly: Boolean get() = SettingsChangeManager.isUiOnly(key)
}

/** One-shot snapshot of every persisted preference, read in a single IO hop. */
data class SettingsPrefs(
    val bools: Map<BoolPref, Boolean> = emptyMap(),
    val strings: Map<StringPref, String> = emptyMap(),
    val dynamicColorSupported: Boolean = false,
)

open class SettingsRepository : BaseRepository() {

    open suspend fun load(): SettingsPrefs = withIO {
        val bools = BoolPref.entries.associateWithTo(LinkedHashMap()) {
            MmkvManager.decodeSettingsBool(it.key, it.default)
        }
        val strings = StringPref.entries.associateWith {
            MmkvManager.decodeSettingsString(it.key, it.default) ?: it.default
        }
        bools[BoolPref.DYNAMIC_COLOR] = ThemeRepository.isDynamicColorEnabled()
        if (strings[StringPref.MODE] == VPN && bools[BoolPref.USE_HEV_TUNNEL] == true
            && bools[BoolPref.ENABLE_LOCAL_PROXY] != true) {
            MmkvManager.encodeSettings(AppConfig.PREF_ENABLE_LOCAL_PROXY, true)
            bools[BoolPref.ENABLE_LOCAL_PROXY] = true
        }
        SettingsPrefs(bools, strings, ThemeRepository.isDynamicColorSupported)
    }

    open suspend fun setBool(pref: BoolPref, value: Boolean) = withIO {
        if (pref == BoolPref.DYNAMIC_COLOR) {
            ThemeRepository.setDynamicColorEnabled(value)
        } else {
            MmkvManager.encodeSettings(pref.key, value)
        }
        SettingsChangeManager.notifySettingChanged(pref.key)
    }

    open suspend fun setString(pref: StringPref, value: String) = withIO {
        if (pref == StringPref.UI_MODE_NIGHT) {
            ThemeRepository.setThemeMode(AppThemeMode.from(value))
        } else {
            MmkvManager.encodeSettings(pref.key, value)
        }
        SettingsChangeManager.notifySettingChanged(pref.key)
    }

    /** Re-probes after a denial, so a second tap can still raise the su prompt. */
    open suspend fun ensureRoot(): Boolean = withIO { RootManager.cachedRoot() || RootManager.refresh() }
}
