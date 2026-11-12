package com.v2ray.ang.ui.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.google.gson.JsonObject
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.PreferenceGroupHeader
import com.v2ray.ang.util.JsonUtil

class ServerMasqueActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.MASQUE

    @Composable
    override fun ScreenContent() {
        val options = rememberFieldOptions()
        val scope = rememberCoroutineScope()

        val uiState = rememberSaveable(saver = ServerUiState.Saver) {
            ServerUiState.from(initialConfig = initialConfig).apply {
                if (alpn.isBlank()) {
                    alpn = ALPN_H3
                }
            }
        }.apply {
            configType = EConfigType.MASQUE
            streamSecurity = AppConfig.TLS
        }

        ServerEditorScaffold(
            title = serverConfigType.toString(),
            onSaveClick = { saveServer(uiState) }
        ) {
            CommonBasicFields(uiState)
            MasqueProtocolFields(uiState)
            PreferenceGroupHeader(title = stringResource(R.string.server_lab_stream_security))
            CommonStreamSecurityFields(
                state = uiState,
                options = options.copy(
                    alpnOptions = listOf(ALPN_H2, ALPN_H3)
                ),
                scope = scope,
                buildProfileItem = {
                    uiState.toProfileItem(initialConfig).apply {
                        security = AppConfig.TLS
                        insecure = false
                        alpn = uiState.alpn.ifBlank { ALPN_H3 }
                    }
                },
                showSecuritySelector = false,
                showFingerprint = uiState.alpn == ALPN_H2,
                showAllowInsecure = false,
                showEchConfigList = false
            )
        }
    }

    override fun validateProtocolConfig(config: ProfileItem): Boolean {
        if (!config.headers.isNullOrBlank()) {
            val headers = try {
                JsonUtil.fromJson(config.headers.orEmpty(), JsonObject::class.java)
            } catch (_: Exception) {
                null
            }
            if (headers == null || headers.entrySet().any { !it.value.isJsonPrimitive || !it.value.asJsonPrimitive.isString }) {
                toast(R.string.sub_setting_request_headers)
                return false
            }
        }
        config.security = AppConfig.TLS
        config.insecure = false
        config.alpn = config.alpn
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: ALPN_H3
        return config.alpn == ALPN_H2 || config.alpn == ALPN_H3
    }

    @Composable
    private fun MasqueProtocolFields(state: ServerUiState) {
        FormTextField(
            stringResource(R.string.server_lab_request_host_masque),
            state.host,
            { state.host = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_path_masque),
            state.path,
            { state.path = it }
        )
        FormTextField(
            stringResource(R.string.sub_setting_request_headers),
            state.headers,
            { state.headers = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_remote_dns),
            state.remoteDNS,
            { state.remoteDNS = it },
            supportingText = stringResource(
                R.string.server_lab_remote_dns_hint,
                AppConfig.MASQUE_LOCAL_REMOTE_DNS
            )
        )
        PreferenceGroupHeader(
            title = stringResource(
                R.string.server_lab_masque_warp_settings
            )
        )
        // WARP settings
        FormTextField(
            stringResource(R.string.server_lab_local_address),
            state.localAddress,
            { state.localAddress = it },
            supportingText = stringResource(
                R.string.server_lab_local_address_hint,
                "${AppConfig.MASQUE_LOCAL_ADDRESS_V4},${AppConfig.MASQUE_LOCAL_ADDRESS_V6}"
            )
        )
        FormTextField(
            stringResource(R.string.server_lab_masque_warp_private_key),
            state.secretKey,
            { state.secretKey = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_masque_warp_public_key),
            state.publicKey,
            { state.publicKey = it }
        )
        // finalmask
        FormTextField(
            stringResource(R.string.server_lab_final_mask),
            state.finalMask,
            { state.finalMask = it }
        )
    }

    private companion object {
        const val ALPN_H2 = "h2"
        const val ALPN_H3 = "h3"
    }
}
