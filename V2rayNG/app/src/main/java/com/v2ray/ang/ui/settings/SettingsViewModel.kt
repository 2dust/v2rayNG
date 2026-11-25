package com.v2ray.ang.ui.settings

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.root.RootManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : BaseViewModel(application) {

    private val _systemVpnSettingsAvailable = MutableStateFlow(false)
    val systemVpnSettingsAvailable = _systemVpnSettingsAvailable.asStateFlow()

    private val _hysteria2HopInterval = MutableStateFlow<String?>(null)
    val hysteria2HopInterval = _hysteria2HopInterval.asStateFlow()
    private val hopIntervalWrites = Mutex()

    internal suspend fun loadHysteria2HopInterval(
        read: () -> String? = { MmkvManager.decodeSettingsString(AppConfig.PREF_HYSTERIA2_HOP_INTERVAL) }
    ): Unit = hopIntervalWrites.withLock {
        if (_hysteria2HopInterval.value == null) {
            _hysteria2HopInterval.value = withContext(Dispatchers.IO) { read() }.orEmpty().trim().ifEmpty { "30" }
        }
    }

    fun updateHysteria2HopInterval(value: String) {
        viewModelScope.launch {
            if (!saveHysteria2HopInterval(value)) {
                LogUtil.e(AppConfig.TAG, "Settings: could not save the Hysteria2 hop interval")
                toastError(R.string.toast_failure)
            }
        }
    }

    internal suspend fun saveHysteria2HopInterval(
        value: String,
        write: (String) -> Boolean = { MmkvManager.encodeSettings(AppConfig.PREF_HYSTERIA2_HOP_INTERVAL, it) }
    ): Boolean = hopIntervalWrites.withLock {
        val interval = value.trim().ifEmpty { "30" }
        if (!withContext(Dispatchers.IO) { write(interval) }) return@withLock false
        _hysteria2HopInterval.value = interval
        true
    }

    suspend fun refreshSystemVpnSettingsAvailability() {
        _systemVpnSettingsAvailable.value = withContext(Dispatchers.IO) {
            // Android exposes the VPN page, not a direct link to the Always-on VPN switch.
            Intent(Settings.ACTION_VPN_SETTINGS).resolveActivity(getApplication<Application>().packageManager) != null
        }
    }

    /**
     * Checks for root access and requests it if necessary.
     * Updates [isLoading] during the process.
     */
    fun checkAndRequestRoot(onSuccess: () -> Unit) {
        launchLoading {
            val hasRoot = withContext(Dispatchers.IO) {
                RootManager.refresh()
            }
            if (hasRoot) {
                onSuccess()
            } else {
                toastError(R.string.toast_root_required)
            }
        }
    }

    /**
     * Validates if the given string is a valid observatory duration.
     * Shows error toast if invalid.
     * @return The trimmed value if valid, null otherwise.
     */
    fun validateObservatoryDuration(value: String): String? {
        val duration = value.trim()
        return if (AppConfig.OBSERVATORY_DURATION_PATTERN.matches(duration)) {
            duration
        } else {
            toastError(R.string.toast_invalid_observatory_duration)
            null
        }
    }

    /**
     * Validates if the given string is a valid observatory sampling value.
     * Shows error toast if invalid.
     * @return The value if valid, null otherwise.
     */
    fun validateObservatorySampling(value: String): String? {
        val sampling = value.trim().toIntOrNull()?.takeIf { it > 0 }
        return if (sampling != null) {
            sampling.toString()
        } else {
            toastError(R.string.toast_invalid_observatory_sampling)
            null
        }
    }
}
