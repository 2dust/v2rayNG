package com.v2ray.ang.viewmodel

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.MmkvManager

class SettingsViewModel(application: Application) : AndroidViewModel(application), SharedPreferences.OnSharedPreferenceChangeListener {

    private val settingsStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SETTING, MMKV.MULTI_PROCESS_MODE) }

    fun startListenPreferenceChange() {
        PreferenceManager.getDefaultSharedPreferences(getApplication()).registerOnSharedPreferenceChangeListener(this)
    }

    override fun onCleared() {
        PreferenceManager.getDefaultSharedPreferences(getApplication()).unregisterOnSharedPreferenceChangeListener(this)
        Log.i(AppConfig.ANG_PACKAGE, "Settings ViewModel is cleared")
        super.onCleared()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        Log.d(AppConfig.ANG_PACKAGE, "Observe settings changed: $key")
        when(key) {
            AppConfig.PREF_MODE,
            AppConfig.PREF_VPN_DNS,
            AppConfig.PREF_REMOTE_DNS,
            AppConfig.PREF_DOMESTIC_DNS,
            AppConfig.PREF_LOCAL_DNS_PORT,
            AppConfig.PREF_SOCKS_PORT,
            AppConfig.PREF_HTTP_PORT,
            AppConfig.PREF_LOGLEVEL,
            AppConfig.PREF_LANGUAGE,
            AppConfig.PREF_ROUTING_DOMAIN_STRATEGY,
            AppConfig.PREF_ROUTING_MODE,
            AppConfig.PREF_V2RAY_ROUTING_AGENT,
            AppConfig.PREF_V2RAY_ROUTING_BLOCKED,
            AppConfig.PREF_V2RAY_ROUTING_DIRECT,
            AppConfig.SUBSCRIPTION_AUTO_UPDATE_INTERVAL,
            AppConfig.PREF_MUX_XUDP_QUIC, -> {
                settingsStorage?.encode(key, sharedPreferences.getString(key, ""))
            }
            AppConfig.PREF_SPEED_ENABLED,
            AppConfig.PREF_PROXY_SHARING,
            AppConfig.PREF_LOCAL_DNS_ENABLED,
            AppConfig.PREF_FAKE_DNS_ENABLED,
            AppConfig.PREF_ALLOW_INSECURE,
            AppConfig.PREF_PREFER_IPV6,
            AppConfig.PREF_PER_APP_PROXY,
            AppConfig.PREF_BYPASS_APPS,
            AppConfig.PREF_CONFIRM_REMOVE,
            AppConfig.PREF_START_SCAN_IMMEDIATE,
            AppConfig.SUBSCRIPTION_AUTO_UPDATE,
            AppConfig.PREF_MUX_ENABLED, -> {
                settingsStorage?.encode(key, sharedPreferences.getBoolean(key, false))
            }
            AppConfig.PREF_SNIFFING_ENABLED -> {
                settingsStorage?.encode(key, sharedPreferences.getBoolean(key, true))
            }
            AppConfig.PREF_MUX_CONCURRENCY,
            AppConfig.PREF_MUX_XUDP_CONCURRENCY -> {
                settingsStorage?.encode(key, sharedPreferences.getString(key, "8")?.toIntOrNull() ?: 8)
            }
            AppConfig.PREF_PER_APP_PROXY_SET -> {
                settingsStorage?.encode(key, sharedPreferences.getStringSet(key, setOf()))
            }
        }
    }
}
