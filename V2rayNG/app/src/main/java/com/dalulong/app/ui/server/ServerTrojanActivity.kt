package com.dalulong.app.ui.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.dalulong.app.R
import com.dalulong.app.compose.FormTextField
import com.dalulong.app.dto.entities.ProfileItem
import com.dalulong.app.enums.EConfigType
import com.dalulong.app.extension.toast

class ServerTrojanActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.TROJAN

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
            configType = EConfigType.TROJAN
        }

        ServerEditorScaffold(
            title = serverConfigType.toString(),
            onSaveClick = { saveServer(uiState) }
        ) {
            item { CommonBasicFields(uiState) }
            item { TrojanProtocolFields(uiState) }
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
            toast(R.string.server_lab_id3)
            return false
        }
        if (config.security.isNullOrBlank()) {
            toast(R.string.server_lab_stream_security)
            return false
        }
        return true
    }

    @Composable
    private fun TrojanProtocolFields(state: ServerUiState) {
        FormTextField(
            stringResource(R.string.server_lab_id3),
            state.password,
            { state.password = it }
        )
    }
}

