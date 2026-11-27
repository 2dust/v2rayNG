package com.v2ray.ang.vm

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.CertificateFingerprintManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.mobile.server.ServerUiState
import com.v2ray.ang.util.JsonUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared ViewModel for all Server/Profile editing UIs under com.v2ray.ang.ui.mobile.server.
 * Encapsulates server configuration loading, validation, saving, deletion, and CA256 cert fetching.
 */
class ServerViewModel(application: Application) : BaseViewModel(application) {

    fun loadConfig(editGuid: String, serverConfigType: EConfigType): ProfileItem {
        val existingConfig = MmkvManager.decodeServerConfig(editGuid)
        return existingConfig ?: ProfileItem.create(serverConfigType)
    }

    fun validateBasicConfig(state: ServerUiState): Boolean {
        val remarksErr = state.remarks.isBlank()
        val addressErr = state.address.isBlank()
        val portErr = state.configType != EConfigType.HYSTERIA2 && (state.port.toIntOrNull() ?: 0) <= 0

        state.isRemarksError = remarksErr
        state.isAddressError = addressErr
        state.isPortError = portErr

        return !(remarksErr || addressErr || portErr)
    }

    fun validateCommonConfig(state: ServerUiState, config: ProfileItem): Boolean {
        if (config.password.isNullOrBlank()) {
            state.isPasswordError = true
            if (config.configType == EConfigType.VMESS ||
                config.configType == EConfigType.VLESS ||
                config.configType == EConfigType.TROJAN ||
                config.configType == EConfigType.SHADOWSOCKS ||
                config.configType == EConfigType.HYSTERIA2
            ) {
                return false
            }
        }

        if (config.configType == EConfigType.TROJAN && config.security.isNullOrBlank()) {
            toast(R.string.server_lab_stream_security)
            return false
        }
        if (!config.xhttpExtra.isNullOrBlank() && JsonUtil.parseString(config.xhttpExtra) == null) {
            toast(R.string.server_lab_xhttp_extra)
            return false
        }
        if (!config.finalMask.isNullOrBlank() && JsonUtil.parseString(config.finalMask) == null) {
            toast(R.string.server_lab_final_mask)
            return false
        }
        if (!config.xdriveExtra.isNullOrBlank() && JsonUtil.parseString(config.xdriveExtra) == null) {
            toast(R.string.server_lab_xdrive_extra)
            return false
        }
        return true
    }

    fun saveServer(
        editGuid: String,
        isRunning: Boolean,
        subscriptionId: String?,
        state: ServerUiState,
        initialConfig: ProfileItem,
        validateProtocolConfig: (ProfileItem) -> Boolean = { true },
        onSaved: (String, Boolean) -> Unit
    ): Boolean {
        if (!validateBasicConfig(state)) return false
        val config = state.toProfileItem(initialConfig)
        if (!validateCommonConfig(state, config)) return false
        if (!validateProtocolConfig(config)) return false

        config.description = AngConfigManager.generateDescription(config)
        if (config.subscriptionId.isEmpty() && !subscriptionId.isNullOrEmpty()) {
            config.subscriptionId = subscriptionId
        }
        val savedGuid = MmkvManager.encodeServerConfig(editGuid, config)
        toastSuccess(R.string.toast_success)
        onSaved(savedGuid, isRunning)
        return true
    }

    fun deleteServer(guid: String, onDeleted: (String) -> Unit): Boolean {
        if (guid.isEmpty() || guid == MmkvManager.getSelectServer()) {
            toast(R.string.toast_action_not_allowed)
            return false
        }
        MmkvManager.removeServer(guid)
        onDeleted(guid)
        return true
    }

    fun fetchCertificateFingerprint(temp: ProfileItem, state: ServerUiState) {
        if (state.address.isBlank()) {
            toast(R.string.server_lab_address)
            return
        }
        if (state.configType != EConfigType.HYSTERIA2 && (state.port.toIntOrNull() ?: 0) <= 0) {
            toast(R.string.server_lab_port)
            return
        }

        viewModelScope.launch {
            state.isFetchingCert = true
            try {
                val sha256 = withContext(Dispatchers.IO) {
                    CertificateFingerprintManager.fetchForManualFill(temp)
                }
                if (sha256.isNullOrBlank()) {
                    toast(R.string.toast_fetch_cert_sha256_failed)
                } else {
                    state.pinnedCA256 = sha256
                    toastSuccess(R.string.toast_fetch_cert_sha256_success)
                }
            } finally {
                state.isFetchingCert = false
            }
        }
    }
}
