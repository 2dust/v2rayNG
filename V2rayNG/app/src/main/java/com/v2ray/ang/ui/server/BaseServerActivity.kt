package com.v2ray.ang.ui.server

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig.REALITY
import com.v2ray.ang.AppConfig.TLS
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.compose.ConfirmDialog
import com.v2ray.ang.compose.FormDropdownField
import com.v2ray.ang.compose.FormTextField
import com.v2ray.ang.compose.SettingsSwitchItem
import com.v2ray.ang.compose.verticalScrollbar
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.NetworkType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.CertificateFingerprintManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.util.JsonUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class BaseServerActivity : BaseComponentActivity() {

    protected abstract val serverConfigType: EConfigType

    protected val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    protected val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }
    protected val subscriptionId by lazy { intent.getStringExtra("subscriptionId") }

    protected lateinit var initialConfig: ProfileItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val existingConfig = MmkvManager.decodeServerConfig(editGuid)
        initialConfig = existingConfig ?: ProfileItem.create(serverConfigType)
    }

    @Composable
    protected fun rememberFieldOptions(): FieldOptions =
        FieldOptions(
            networkOptions = stringArrayResource(R.array.networks).toList(),
            tcpHeaderOptions = stringArrayResource(R.array.header_type_tcp).toList(),
            kcpHeaderOptions = stringArrayResource(R.array.header_type_kcp_and_quic).toList(),
            grpcModeOptions = stringArrayResource(R.array.mode_type_grpc).toList(),
            xhttpModeOptions = stringArrayResource(R.array.xhttp_mode).toList(),
            streamSecurityOptions = stringArrayResource(R.array.streamsecurityxs).toList(),
            uTlsOptions = stringArrayResource(R.array.streamsecurity_utls).toList(),
            alpnOptions = stringArrayResource(R.array.streamsecurity_alpn).toList(),
            browserDialerOptions = stringArrayResource(R.array.browser_dialer_mode_value).toList()
        )

    data class FieldOptions(
        val networkOptions: List<String>,
        val tcpHeaderOptions: List<String>,
        val kcpHeaderOptions: List<String>,
        val grpcModeOptions: List<String>,
        val xhttpModeOptions: List<String>,
        val streamSecurityOptions: List<String>,
        val uTlsOptions: List<String>,
        val alpnOptions: List<String>,
        val browserDialerOptions: List<String>
    )

    @Composable
    protected fun CommonBasicFields(
        state: ServerUiState,
        showPort: Boolean = true
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FormTextField(
                stringResource(R.string.server_lab_remarks),
                state.remarks,
                { state.remarks = it }
            )
            FormTextField(
                stringResource(R.string.server_lab_address),
                state.address,
                { state.address = it }
            )
            if (showPort) {
                FormTextField(
                    stringResource(R.string.server_lab_port),
                    state.port,
                    { state.port = it },
                    keyboardType = KeyboardType.Number
                )
            }
        }
    }

    @Composable
    protected fun CommonNetworkFields(
        state: ServerUiState,
        options: FieldOptions
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FormDropdownField(
                stringResource(R.string.server_lab_network),
                state.network,
                options.networkOptions,
                { state.network = it }
            )

            val headerOptions = when (state.network) {
                NetworkType.TCP.type -> options.tcpHeaderOptions
                NetworkType.KCP.type -> options.kcpHeaderOptions
                NetworkType.GRPC.type -> options.grpcModeOptions
                NetworkType.XHTTP.type -> options.xhttpModeOptions
                else -> listOf("---")
            }
            if (headerOptions.size > 1) {
                FormDropdownField(
                    stringResource(
                        when (state.network) {
                            NetworkType.GRPC.type -> R.string.server_lab_mode_type
                            NetworkType.XHTTP.type -> R.string.server_lab_xhttp_mode
                            else -> R.string.server_lab_head_type
                        }
                    ),
                    state.headerType,
                    headerOptions,
                    { state.headerType = it }
                )
            }

            FormTextField(
                stringResource(
                    when (state.network) {
                        NetworkType.TCP.type,
                        NetworkType.HTTP_UPGRADE.type,
                        NetworkType.XHTTP.type,
                        NetworkType.H2.type -> R.string.server_lab_request_host_http

                        NetworkType.WS.type -> R.string.server_lab_request_host_ws
                        NetworkType.GRPC.type -> R.string.server_lab_request_host_grpc
                        else -> R.string.server_lab_request_host6
                    }
                ),
                state.host,
                { state.host = it }
            )

            FormTextField(
                stringResource(
                    when (state.network) {
                        NetworkType.KCP.type -> R.string.server_lab_path_kcp
                        NetworkType.WS.type -> R.string.server_lab_path_ws
                        NetworkType.HTTP_UPGRADE.type -> R.string.server_lab_path_httpupgrade
                        NetworkType.XHTTP.type -> R.string.server_lab_path_xhttp
                        NetworkType.H2.type -> R.string.server_lab_path_h2
                        NetworkType.GRPC.type -> R.string.server_lab_path_grpc
                        else -> R.string.server_lab_path
                    }
                ),
                state.path,
                { state.path = it }
            )

            if (state.network == NetworkType.XHTTP.type) {
                FormTextField(
                    stringResource(R.string.server_lab_xhttp_extra),
                    state.xhttpExtra,
                    { state.xhttpExtra = it }
                )
            }
            if (state.network == NetworkType.KCP.type) {
                FormTextField(
                    stringResource(R.string.server_lab_kcp_mtu),
                    state.kcpMtu,
                    { state.kcpMtu = it },
                    keyboardType = KeyboardType.Number
                )
                FormTextField(
                    stringResource(R.string.server_lab_kcp_tti),
                    state.kcpTti,
                    { state.kcpTti = it },
                    keyboardType = KeyboardType.Number
                )
            }
            FormTextField(
                stringResource(R.string.server_lab_final_mask),
                state.finalMask,
                { state.finalMask = it }
            )
            if (state.network == NetworkType.WS.type || state.network == NetworkType.XHTTP.type) {
                FormDropdownField(
                    stringResource(R.string.server_lab_browser_dialer),
                    state.browserDialerMode,
                    options.browserDialerOptions,
                    { state.browserDialerMode = it }
                )
            }
        }
    }

    @Composable
    protected fun CommonStreamSecurityFields(
        state: ServerUiState,
        options: FieldOptions,
        scope: CoroutineScope,
        buildProfileItem: () -> ProfileItem
    ) {
        val context = LocalContext.current
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FormDropdownField(
                stringResource(R.string.server_lab_stream_security),
                state.streamSecurity,
                options.streamSecurityOptions,
                { state.streamSecurity = it }
            )

            if (state.streamSecurity.isBlank()) {
                return@Column
            }

            FormTextField(
                stringResource(R.string.server_lab_sni),
                state.sni,
                { state.sni = it }
            )
            FormDropdownField(
                stringResource(R.string.server_lab_stream_fingerprint),
                state.fingerPrint,
                options.uTlsOptions,
                { state.fingerPrint = it }
            )

            if (state.streamSecurity == TLS) {
                SettingsSwitchItem(
                    title = stringResource(R.string.server_lab_allow_insecure),
                    checked = state.allowInsecure,
                    onCheckedChange = { state.allowInsecure = it }
                )
                FormDropdownField(
                    stringResource(R.string.server_lab_stream_alpn),
                    state.alpn,
                    options.alpnOptions,
                    { state.alpn = it }
                )
                FormTextField(
                    stringResource(R.string.server_lab_ech_config_list),
                    state.echConfigList,
                    { state.echConfigList = it }
                )
                FormTextField(
                    stringResource(R.string.server_lab_verify_peer_cert_by_name),
                    state.verifyPeerCertByName,
                    { state.verifyPeerCertByName = it }
                )
                FormTextField(
                    stringResource(R.string.server_lab_pinned_ca256),
                    state.pinnedCA256,
                    { state.pinnedCA256 = it }
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
                        val temp = buildProfileItem()
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
            } else if (state.streamSecurity == REALITY) {
                FormTextField(
                    stringResource(R.string.server_lab_public_key),
                    state.publicKeyReality,
                    { state.publicKeyReality = it }
                )
                FormTextField(
                    stringResource(R.string.server_lab_short_id),
                    state.shortId,
                    { state.shortId = it }
                )
                FormTextField(
                    stringResource(R.string.server_lab_spider_x),
                    state.spiderX,
                    { state.spiderX = it }
                )
                FormTextField(
                    stringResource(R.string.server_lab_mldsa65_verify),
                    state.mldsa65Verify,
                    { state.mldsa65Verify = it }
                )
            }
        }
    }

    protected fun validateBasicConfig(state: ServerUiState): Boolean {
        if (state.remarks.isBlank()) {
            toast(R.string.server_lab_remarks)
            return false
        }
        if (state.address.isBlank()) {
            toast(R.string.server_lab_address)
            return false
        }
        if (
            state.configType != EConfigType.HYSTERIA2 &&
            (state.port.toIntOrNull() ?: 0) <= 0
        ) {
            toast(R.string.server_lab_port)
            return false
        }
        return true
    }

    protected open fun validateProtocolConfig(config: ProfileItem): Boolean = true

    protected open fun validateCommonConfig(config: ProfileItem): Boolean {

        if (config.password.isNullOrBlank()) {
            if (config.configType == EConfigType.VMESS ||
                config.configType == EConfigType.VLESS
            ) {
                toast(R.string.server_lab_id)
                return false
            }

            if (config.configType == EConfigType.TROJAN ||
                config.configType == EConfigType.SHADOWSOCKS ||
                config.configType == EConfigType.HYSTERIA2
            ) {
                toast(R.string.server_lab_id3)
                return false
            }
        }

        if (
            config.configType == EConfigType.TROJAN &&
            config.security.isNullOrBlank()
        ) {
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
        return true
    }

    protected fun saveServer(state: ServerUiState): Boolean {
        if (!validateBasicConfig(state)) return false
        val config = state.toProfileItem(initialConfig)
        if (!validateCommonConfig(config)) return false
        if (!validateProtocolConfig(config)) return false

        config.description = AngConfigManager.generateDescription(config)
        if (config.subscriptionId.isEmpty() && !subscriptionId.isNullOrEmpty()) {
            config.subscriptionId = subscriptionId.orEmpty()
        }
        val savedGuid = MmkvManager.encodeServerConfig(editGuid, config)
        toastSuccess(R.string.toast_success)
        ProfileEditorResult.run {
            finishSaved(savedGuid, isRunning)
        }
        return true
    }

    @Composable
    protected fun ServerEditorScaffold(
        title: String,
        onSaveClick: () -> Unit,
        content: LazyListScope.() -> Unit
    ) {
        var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
        val listState = rememberLazyListState()
        Scaffold(
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
            topBar = {
                AppTopBar(
                    title = title,
                    onBackClick = { finish() },
                    actions = {
                        if (editGuid.isNotEmpty() && !isRunning) {
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(
                                    painterResource(R.drawable.ic_delete_24dp),
                                    stringResource(R.string.menu_item_del_config)
                                )
                            }
                        }
                        IconButton(onClick = onSaveClick) {
                            Icon(
                                painterResource(R.drawable.ic_fab_check),
                                stringResource(R.string.menu_item_save_config)
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
                    .verticalScrollbar(listState),
                contentPadding = PaddingValues(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
        if (showDeleteDialog) {
            ConfirmDialog(
                message = stringResource(R.string.del_config_comfirm),
                confirmText = stringResource(android.R.string.ok),
                dismissText = stringResource(android.R.string.cancel),
                onConfirm = {
                    showDeleteDialog = false
                    deleteServer(editGuid)
                },
                onDismiss = { showDeleteDialog = false }
            )
        }
    }

    private fun deleteServer(guid: String) {
        if (guid.isEmpty() || guid == MmkvManager.getSelectServer()) {
            toast(R.string.toast_action_not_allowed)
            return
        }
        MmkvManager.removeServer(guid)
        ProfileEditorResult.run {
            finishDeleted(guid)
        }
    }
}
