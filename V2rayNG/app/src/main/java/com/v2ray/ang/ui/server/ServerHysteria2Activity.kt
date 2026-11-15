package com.v2ray.ang.ui.server

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.CertificateFingerprintManager
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerHysteria2Activity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.HYSTERIA2

    @Composable
    override fun ScreenContent() {
        val uiState = rememberSaveable(saver = ServerUiState.Saver) {
            ServerUiState.from(
                initialConfig = initialConfig
            )
        }.apply {
            configType = EConfigType.HYSTERIA2
        }

        ServerEditorScaffold(
            title = serverConfigType.toString(),
            onSaveClick = { saveServer(uiState) }
        ) {
            CommonBasicFields(uiState)
            Hysteria2ProtocolFields(uiState)

        }
    }

    override fun validateProtocolConfig(config: ProfileItem): Boolean {
        if (config.password.isNullOrBlank()) {
            return false
        }
        if (config.security.isNullOrBlank()) {
            config.security = AppConfig.TLS
        }
        return true
    }

    @Composable
    private fun Hysteria2ProtocolFields(state: ServerUiState) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        FormTextField(
            label = stringResource(R.string.server_lab_id3),
            value = state.password,
            onValueChange = { state.password = it },
            isError = state.isPasswordError
        )
        val obfsOptions = listOf("none", "salamander", "gecko")
        val selectedObfs = when {
            state.obfs.equals("gecko", ignoreCase = true) -> "gecko"
            state.obfs.equals("salamander", ignoreCase = true) -> "salamander"
            state.obfsPassword.isNotBlank() && !state.obfs.equals("none", ignoreCase = true) -> "salamander"
            else -> "none"
        }
        FormDropdownField(
            label = stringResource(R.string.server_obfs_type),
            value = selectedObfs,
            options = obfsOptions,
            onValueChange = {
                state.obfs = if (it == "none") "" else it
                if (it == "none") {
                    state.obfsPassword = ""
                    state.minPacketSize = ""
                    state.maxPacketSize = ""
                }
            }
        )
        if (selectedObfs != "none") {
            FormTextField(
                stringResource(R.string.server_obfs_password),
                state.obfsPassword,
                { state.obfsPassword = it }
            )
            if (selectedObfs == "gecko") {
                FormTextField(
                    stringResource(R.string.server_lab_min_packet_size),
                    state.minPacketSize,
                    { state.minPacketSize = it }
                )
                FormTextField(
                    stringResource(R.string.server_lab_max_packet_size),
                    state.maxPacketSize,
                    { state.maxPacketSize = it }
                )
            }
        }
        FormTextField(
            stringResource(R.string.server_lab_port_hop),
            state.portHopping,
            { state.portHopping = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_port_hop_interval),
            state.portHoppingInterval,
            { state.portHoppingInterval = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_bandwidth_down),
            state.bandwidthDown,
            { state.bandwidthDown = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_bandwidth_up),
            state.bandwidthUp,
            { state.bandwidthUp = it }
        )

        SettingsSwitchItem(
            title = stringResource(R.string.server_lab_allow_insecure),
            checked = state.allowInsecure,
            onCheckedChange = { state.allowInsecure = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_sni),
            state.sni,
            { state.sni = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_ech_config_list),
            state.echConfigList,
            { state.echConfigList = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_pinned_ca256),
            state.pinnedCA256,
            { state.pinnedCA256 = it }
        )

        FormTextField(
            stringResource(R.string.server_lab_final_mask),
            state.finalMask,
            { state.finalMask = it }
        )

        Button(
            onClick = {
                if (state.address.isBlank()) {
                    context.toast(R.string.server_lab_address)
                    return@Button
                }
                if (
                    state.configType != EConfigType.HYSTERIA2 &&
                    (state.port.toIntOrNull() ?: 0) <= 0
                ) {
                    context.toast(R.string.server_lab_port)
                    return@Button
                }
                val temp = state.toProfileItem(initialConfig)
                scope.launch {
                    state.isFetchingCert = true
                    try {
                        val sha256 = withContext(Dispatchers.IO) {
                            CertificateFingerprintManager.fetchForManualFill(temp)
                        }
                        if (sha256.isNullOrBlank()) {
                            context.toast(R.string.toast_fetch_cert_sha256_failed)
                        } else {
                            state.pinnedCA256 = sha256
                            context.toastSuccess(R.string.toast_fetch_cert_sha256_success)
                        }
                    } finally {
                        state.isFetchingCert = false
                    }
                }
            },
            enabled = !state.isFetchingCert,
            modifier = Modifier.padding(start = 16.dp)
        ) {
            Text(stringResource(R.string.pinned_ca256_action_fetch))
        }
    }
}


