package com.v2ray.ang.viewmodel

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager

class SettingsViewModel(application: Application) : AndroidViewModel(application),
    SharedPreferences.OnSharedPreferenceChangeListener {

    /**
     * Starts listening for preference changes.
     */
    fun startListenPreferenceChange() {
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .registerOnSharedPreferenceChangeListener(this)
    }

    /**
     * Called when the ViewModel is cleared.
     */
    override fun onCleared() {
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .unregisterOnSharedPreferenceChangeListener(this)
        Log.i(AppConfig.TAG, "Settings ViewModel is cleared")
        super.onCleared()
    }

    /**
     * Called when a shared preference is changed.
     * @param sharedPreferences The shared preferences.
     * @param key The key of the changed preference.
     */
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        Log.i(AppConfig.TAG, "Observe settings changed: $key")
        when (key) {
            AppConfig.PREF_MODE,
            AppConfig.PREF_VPN_DNS,
            AppConfig.PREF_VPN_BYPASS_LAN,
            AppConfig.PREF_VPN_INTERFACE_ADDRESS_CONFIG_INDEX,
            AppConfig.PREF_VPN_MTU,
            AppConfig.PREF_REMOTE_DNS,
            AppConfig.PREF_DOMESTIC_DNS,
            AppConfig.PREF_DNS_HOSTS,
            AppConfig.PREF_DELAY_TEST_URL,
            AppConfig.PREF_LOCAL_DNS_PORT,
            AppConfig.PREF_SOCKS_PORT,
            AppConfig.PREF_LOGLEVEL,
            AppConfig.PREF_OUTBOUND_DOMAIN_RESOLVE_METHOD,
            AppConfig.PREF_INTELLIGENT_SELECTION_METHOD,
            AppConfig.PREF_LANGUAGE,
            AppConfig.PREF_UI_MODE_NIGHT,
            AppConfig.PREF_ROUTING_DOMAIN_STRATEGY,
            AppConfig.SUBSCRIPTION_AUTO_UPDATE_INTERVAL,
            AppConfig.PREF_FRAGMENT_PACKETS,
            AppConfig.PREF_FRAGMENT_LENGTH,
            AppConfig.PREF_FRAGMENT_INTERVAL,
            AppConfig.PREF_MUX_XUDP_QUIC,
            AppConfig.PREF_HEV_TUNNEL_LOGLEVEL,
            AppConfig.PREF_HEV_TUNNEL_RW_TIMEOUT
                -> {
                MmkvManager.encodeSettings(key, sharedPreferences.getString(key, ""))
            }

            AppConfig.PREF_ROUTE_ONLY_ENABLED,
            AppConfig.PREF_IS_BOOTED,
            AppConfig.PREF_SPEED_ENABLED,
            AppConfig.PREF_PROXY_SHARING,
            AppConfig.PREF_LOCAL_DNS_ENABLED,
            AppConfig.PREF_FAKE_DNS_ENABLED,
            AppConfig.PREF_APPEND_HTTP_PROXY,
            AppConfig.PREF_ALLOW_INSECURE,
            AppConfig.PREF_PREFER_IPV6,
//            AppConfig.PREF_PER_APP_PROXY,
            AppConfig.PREF_BYPASS_APPS,
            AppConfig.PREF_CONFIRM_REMOVE,
            AppConfig.PREF_START_SCAN_IMMEDIATE,
            AppConfig.PREF_DOUBLE_COLUMN_DISPLAY,
            AppConfig.SUBSCRIPTION_AUTO_UPDATE,
            AppConfig.PREF_FRAGMENT_ENABLED,
            AppConfig.PREF_MUX_ENABLED
                -> {
                MmkvManager.encodeSettings(key, sharedPreferences.getBoolean(key, false))
            }

            AppConfig.PREF_SNIFFING_ENABLED,
            AppConfig.PREF_USE_HEV_TUNNEL -> {
                MmkvManager.encodeSettings(key, sharedPreferences.getBoolean(key, true))
            }

            AppConfig.PREF_MUX_CONCURRENCY,
            AppConfig.PREF_MUX_XUDP_CONCURRENCY -> {
                MmkvManager.encodeSettings(key, sharedPreferences.getString(key, "8"))
            }
        }
        if (key == AppConfig.PREF_UI_MODE_NIGHT) {
            SettingsManager.setNightMode()
        }
    }
}
