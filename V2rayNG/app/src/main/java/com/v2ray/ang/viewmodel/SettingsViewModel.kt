package com.v2ray.ang.viewmodel

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.Utils

class SettingsViewModel(application: Application) : AndroidViewModel(application),
    SharedPreferences.OnSharedPreferenceChangeListener {

    fun startListenPreferenceChange() {
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onCleared() {
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .unregisterOnSharedPreferenceChangeListener(this)
        Log.i(AppConfig.ANG_PACKAGE, "Settings ViewModel is cleared")
        super.onCleared()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        Log.d(AppConfig.ANG_PACKAGE, "Observe settings changed: $key")
        when (key) {
            AppConfig.PREF_MODE,
            AppConfig.PREF_VPN_DNS,
            AppConfig.PREF_VPN_BYPASS_LAN,
            AppConfig.PREF_REMOTE_DNS,
            AppConfig.PREF_DOMESTIC_DNS,
            AppConfig.PREF_DNS_HOSTS,
            AppConfig.PREF_DELAY_TEST_URL,
            AppConfig.PREF_LOCAL_DNS_PORT,
            AppConfig.PREF_SOCKS_PORT,
            AppConfig.PREF_LOGLEVEL,
            AppConfig.PREF_LANGUAGE,
            AppConfig.PREF_UI_MODE_NIGHT,
            AppConfig.PREF_ROUTING_DOMAIN_STRATEGY,
            AppConfig.SUBSCRIPTION_AUTO_UPDATE_INTERVAL,
            AppConfig.PREF_FRAGMENT_PACKETS,
            AppConfig.PREF_FRAGMENT_LENGTH,
            AppConfig.PREF_FRAGMENT_INTERVAL,
            AppConfig.PREF_MUX_XUDP_QUIC,
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
            AppConfig.PREF_PER_APP_PROXY,
            AppConfig.PREF_BYPASS_APPS,
            AppConfig.PREF_CONFIRM_REMOVE,
            AppConfig.PREF_START_SCAN_IMMEDIATE,
            AppConfig.SUBSCRIPTION_AUTO_UPDATE,
            AppConfig.PREF_FRAGMENT_ENABLED,
            AppConfig.PREF_MUX_ENABLED,
                -> {
                MmkvManager.encodeSettings(key, sharedPreferences.getBoolean(key, false))
            }

            AppConfig.PREF_SNIFFING_ENABLED -> {
                MmkvManager.encodeSettings(key, sharedPreferences.getBoolean(key, true))
            }

            AppConfig.PREF_MUX_CONCURRENCY,
            AppConfig.PREF_MUX_XUDP_CONCURRENCY -> {
                MmkvManager.encodeSettings(key, sharedPreferences.getString(key, "8"))
            }

//            AppConfig.PREF_PER_APP_PROXY_SET -> {
//                MmkvManager.encodeSettings(key, sharedPreferences.getStringSet(key, setOf()))
//            }
        }
        if (key == AppConfig.PREF_UI_MODE_NIGHT) {
            Utils.setNightMode()
        }
    }
}
