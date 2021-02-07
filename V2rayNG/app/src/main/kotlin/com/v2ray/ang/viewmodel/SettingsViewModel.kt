package com.v2ray.ang.viewmodel

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.content.SharedPreferences
import android.support.v7.preference.PreferenceManager
import android.util.Log
import com.v2ray.ang.AppConfig
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application), SharedPreferences.OnSharedPreferenceChangeListener {
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
            AppConfig.PREF_SNIFFING_ENABLED,
            AppConfig.PREF_PROXY_SHARING,
            AppConfig.PREF_LOCAL_DNS_ENABLED,
            AppConfig.PREF_REMOTE_DNS,
            AppConfig.PREF_DOMESTIC_DNS,
            AppConfig.PREF_ROUTING_DOMAIN_STRATEGY,
            AppConfig.PREF_ROUTING_MODE,
            AppConfig.PREF_V2RAY_ROUTING_AGENT,
            AppConfig.PREF_V2RAY_ROUTING_BLOCKED,
            AppConfig.PREF_V2RAY_ROUTING_DIRECT -> {
                GlobalScope.launch {
                }
            }
        }
    }
}
