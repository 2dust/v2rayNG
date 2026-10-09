package com.v2ray.ang.ui.settings

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.AetherCoreManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.root.RootManager
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : BaseViewModel(application) {

    private val _systemVpnSettingsAvailable = MutableStateFlow(false)
    val systemVpnSettingsAvailable = _systemVpnSettingsAvailable.asStateFlow()

    /** Matixray: the Aether listen port as the setting holds it, blank for the default. */
    private val _aetherListenPort = MutableStateFlow("")
    val aetherListenPort: StateFlow<String> = _aetherListenPort.asStateFlow()

    init {
        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) {
                MmkvManager.decodeSettingsString(AppConfig.PREF_AETHER_LISTEN_PORT).orEmpty()
            }
            // A port set in the meantime is newer than the one read.
            _aetherListenPort.compareAndSet("", stored)
        }
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
     * Matixray: stores [value] as the Aether listen port when [validateAetherListenPort] takes it, and
     * asks for the restart a changed setting needs, as the settings the screen stores itself do.
     */
    fun setAetherListenPort(value: String) {
        val port = validateAetherListenPort(value) ?: return
        if (port == _aetherListenPort.value) return
        _aetherListenPort.value = port
        viewModelScope.launch(Dispatchers.IO) {
            // The value of now, should a later change have come before this one is written.
            MmkvManager.encodeSettings(AppConfig.PREF_AETHER_LISTEN_PORT, _aetherListenPort.value)
            SettingsChangeManager.notifySettingChanged(AppConfig.PREF_AETHER_LISTEN_PORT)
        }
    }

    /**
     * Matixray: validates [value] as the Aether listen port: a port an app can listen on, which neither
     * it nor the three ports after it, the core's as well, shares with the local proxy.
     * Shows error toast if invalid.
     * @return The trimmed value if valid, null otherwise.
     */
    private fun validateAetherListenPort(value: String): String? {
        val port = value.trim()
        return when (AetherCoreManager.listenPortProblem(port, SettingsManager.getLocalProxyPorts())) {
            null -> port
            AetherCoreManager.ListenPortProblem.NOT_A_PORT -> {
                toastError(R.string.toast_invalid_aether_listen_port)
                null
            }

            AetherCoreManager.ListenPortProblem.LOCAL_PROXY -> {
                toastError(R.string.toast_aether_listen_port_local_proxy)
                null
            }
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
