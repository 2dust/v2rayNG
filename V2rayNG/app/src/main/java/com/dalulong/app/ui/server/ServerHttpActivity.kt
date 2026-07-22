package com.dalulong.app.ui.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.dalulong.app.R
import com.dalulong.app.compose.FormTextField
import com.dalulong.app.enums.EConfigType

class ServerHttpActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.HTTP

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
            configType = serverConfigType
        }

        ServerEditorScaffold(
            title = serverConfigType.toString(),
            onSaveClick = { saveServer(uiState) }
        ) {
            item { CommonBasicFields(uiState) }
            item { HttpProtocolFields(uiState) }

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
