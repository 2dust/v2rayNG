package com.v2ray.ang.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R
import com.v2ray.ang.compose.FormDropdownField
import com.v2ray.ang.compose.FormTextField
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.toast

class ServerVmessActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.VMESS

    @Composable
    override fun ScreenContent() {
        val options = rememberFieldOptions()
        val scope = rememberCoroutineScope()
        val uiState = rememberSaveable(saver = ServerUiState.Saver) {
            ServerUiState.from(
                initialConfig = initialConfig,
                browserDialerDefault = options.browserDialerOptions.firstOrNull() ?: "Disable"
            )
        }.apply {
            configType = EConfigType.VMESS
        }
        val securityOptions = stringArrayResource(R.array.securitys).toList()

        ServerEditorScaffold(
            title = serverConfigType.toString(),
            onSaveClick = { saveServer(uiState) }
        ) {
            item { CommonBasicFields(uiState) }
            item { VmessProtocolFields(uiState, securityOptions) }
            item { CommonNetworkFields(uiState, options) }
            item {
                CommonStreamSecurityFields(
                    state = uiState,
                    options = options,
                    scope = scope,
                    buildProfileItem = { uiState.toProfileItem(initialConfig) }
                )
            }
        }
    }

    override fun validateProtocolConfig(config: ProfileItem): Boolean {
        if (config.password.isNullOrBlank()) {
            toast(R.string.server_lab_id)
            return false
        }
        return true
    }

    @Composable
    private fun VmessProtocolFields(
        state: ServerUiState,
        methodOptions: List<String>
    ) {
        FormTextField(
            stringResource(R.string.server_lab_id),
            state.password,
            { state.password = it }
        )
        FormDropdownField(
            stringResource(R.string.server_lab_security),
            state.method,
            methodOptions,
            { state.method = it }
        )
    }
}
