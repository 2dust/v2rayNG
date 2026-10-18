package com.gozar.vpn.ui.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.gozar.vpn.R
import com.gozar.vpn.enums.EConfigType
import com.gozar.vpn.ui.compose.FormTextField

class ServerHttpActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.HTTP

    @Composable
    override fun ScreenContent() {
        val scope = rememberCoroutineScope()
        val uiState = rememberSaveable(saver = ServerUiState.Saver) {
            ServerUiState.from(
                initialConfig = initialConfig
            )
        }.apply {
            configType = serverConfigType
        }

        ServerEditorScaffold(
            title = serverConfigType.toString(),
            onSaveClick = { saveServer(uiState) }
        ) {
            CommonBasicFields(uiState)
            HttpProtocolFields(uiState)

        }
    }

    @Composable
    private fun HttpProtocolFields(state: ServerUiState) {
        FormTextField(
            stringResource(R.string.server_lab_security4),
            state.username,
            { state.username = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_id4),
            state.password,
            { state.password = it }
        )
    }
}
