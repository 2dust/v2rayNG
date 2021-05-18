package com.v2ray.ang.viewmodel

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.content.SharedPreferences
import android.support.v7.preference.PreferenceManager
import android.util.Log
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
            AppConfig.PREF_ROUTING_DOMAIN_STRATEGY,
            AppConfig.PREF_ROUTING_MODE,
            AppConfig.PREF_V2RAY_ROUTING_AGENT,
            AppConfig.PREF_V2RAY_ROUTING_BLOCKED,
            AppConfig.PREF_V2RAY_ROUTING_DIRECT, -> {
                settingsStorage?.encode(key, sharedPreferences.getString(key, ""))
            }
            AppConfig.PREF_SPEED_ENABLED,
            AppConfig.PREF_PROXY_SHARING,
            AppConfig.PREF_LOCAL_DNS_ENABLED,
            AppConfig.PREF_FAKE_DNS_ENABLED,
            AppConfig.PREF_FORWARD_IPV6,
            AppConfig.PREF_PER_APP_PROXY,
            AppConfig.PREF_BYPASS_APPS, -> {
                settingsStorage?.encode(key, sharedPreferences.getBoolean(key, false))
            }
            AppConfig.PREF_SNIFFING_ENABLED, -> {
                settingsStorage?.encode(key, sharedPreferences.getBoolean(key, true))
            }
            AppConfig.PREF_PER_APP_PROXY_SET -> {
                settingsStorage?.encode(key, sharedPreferences.getStringSet(key, setOf()))
            }
        }
    }
}
