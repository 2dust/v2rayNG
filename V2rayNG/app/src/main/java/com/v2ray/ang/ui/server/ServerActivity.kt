package com.v2ray.ang.ui.server

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig.DEFAULT_PORT
import com.v2ray.ang.AppConfig.REALITY
import com.v2ray.ang.AppConfig.TLS
import com.v2ray.ang.AppConfig.WIREGUARD_LOCAL_ADDRESS_V4
import com.v2ray.ang.AppConfig.WIREGUARD_LOCAL_MTU
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
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.CertificateFingerprintManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.BaseComponentActivity
import com.v2ray.ang.util.JsonUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerActivity : BaseComponentActivity() {

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }
    private val createConfigType by lazy {
        EConfigType.fromInt(intent.getIntExtra("createConfigType", EConfigType.VMESS.value))
            ?: EConfigType.VMESS
    }
    private val subscriptionId by lazy { intent.getStringExtra("subscriptionId") }

    private lateinit var initialConfig: ProfileItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val existingConfig = MmkvManager.decodeServerConfig(editGuid)
        initialConfig = existingConfig ?: ProfileItem.create(createConfigType)
    }

    @Composable
    override fun ScreenContent() {
        ServerScreen(
            guid = editGuid,
            configType = initialConfig.configType,
            initialConfig = initialConfig,
            isRunning = isRunning,
            onBackClick = { finish() },
            onSave = { saveServer(it) },
            onDelete = { deleteServer(editGuid, isRunning) }
        )
    }

    private fun saveServer(config: ProfileItem): Boolean {
        if (config.remarks.isBlank()) {
            toast(R.string.server_lab_remarks)
            return false
        }

        if (config.server.isNullOrBlank()) {
            toast(R.string.server_lab_address)
            return false
        }

        if (
            config.configType != EConfigType.HYSTERIA2 &&
            (config.serverPort?.toIntOrNull() ?: 0) <= 0
        ) {
            toast(R.string.server_lab_port)
            return false
        }

        if (
            config.configType != EConfigType.SOCKS &&
            config.configType != EConfigType.HTTP &&
            config.password.isNullOrBlank()
        ) {
            val message = when (config.configType) {
                EConfigType.TROJAN,
                EConfigType.SHADOWSOCKS,
                EConfigType.HYSTERIA2 -> R.string.server_lab_id3

                else -> R.string.server_lab_id
            }
            toast(message)
            return false
        }

        if (
            config.configType == EConfigType.TROJAN &&
            config.security.isNullOrBlank()
        ) {
            toast(R.string.server_lab_stream_security)
            return false
        }

        if (
            !config.xhttpExtra.isNullOrBlank() &&
            JsonUtil.parseString(config.xhttpExtra) == null
        ) {
            toast(R.string.server_lab_xhttp_extra)
            return false
        }

        if (
            !config.finalMask.isNullOrBlank() &&
            JsonUtil.parseString(config.finalMask) == null
        ) {
            toast(R.string.server_lab_final_mask)
            return false
        }

        config.description =
            AngConfigManager.generateDescription(config)

        if (
            config.subscriptionId.isEmpty() &&
            !subscriptionId.isNullOrEmpty()
        ) {
            config.subscriptionId = subscriptionId.orEmpty()
        }

        val savedGuid = MmkvManager.encodeServerConfig(
            editGuid,
            config
        )

        toastSuccess(R.string.toast_success)

        ProfileEditorResult.run {
            finishSaved(
                guid = savedGuid,
                restartService = isRunning
            )
        }

        return true
    }

    private fun deleteServer(
        guid: String,
        isRunning: Boolean
    ) {
        if (
            guid.isEmpty() ||
            guid == MmkvManager.getSelectServer()
        ) {
            toast(R.string.toast_action_not_allowed)
            return
        }

        MmkvManager.removeServer(guid)

        ProfileEditorResult.run {
            finishDeleted(guid)
        }
    }
}

@Composable
fun ServerScreen(
    guid: String,
    configType: EConfigType,
    initialConfig: ProfileItem,
    isRunning: Boolean,
    onBackClick: () -> Unit,
    onSave: (ProfileItem) -> Boolean,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    val securityOptions = stringArrayResource(R.array.securitys).toList()
    val ssSecurityOptions = stringArrayResource(R.array.ss_securitys).toList()
    val flowOptions = stringArrayResource(R.array.flows).toList()
    val networkOptions = stringArrayResource(R.array.networks).toList()
    val tcpHeaderOptions = stringArrayResource(R.array.header_type_tcp).toList()
    val kcpHeaderOptions = stringArrayResource(R.array.header_type_kcp_and_quic).toList()
    val grpcModeOptions = stringArrayResource(R.array.mode_type_grpc).toList()
    val xhttpModeOptions = stringArrayResource(R.array.xhttp_mode).toList()
    val streamSecurityOptions = stringArrayResource(R.array.streamsecurityxs).toList()
    val uTlsOptions = stringArrayResource(R.array.streamsecurity_utls).toList()
    val alpnOptions = stringArrayResource(R.array.streamsecurity_alpn).toList()
    val browserDialerOptions = stringArrayResource(R.array.browser_dialer_mode_value).toList()

    var remarks by rememberSaveable { mutableStateOf(initialConfig.remarks) }
    var address by rememberSaveable { mutableStateOf(initialConfig.server ?: "") }
    var port by rememberSaveable { mutableStateOf(initialConfig.serverPort ?: DEFAULT_PORT.toString()) }
    var password by rememberSaveable { mutableStateOf(initialConfig.password ?: "") }
    var method by rememberSaveable { mutableStateOf(initialConfig.method ?: "") }
    var flow by rememberSaveable { mutableStateOf(initialConfig.flow ?: "") }
    var encryption by rememberSaveable { mutableStateOf(initialConfig.method ?: "") }
    var username by rememberSaveable { mutableStateOf(initialConfig.username ?: "") }
    var secretKey by rememberSaveable { mutableStateOf(initialConfig.secretKey ?: "") }
    var publicKey by rememberSaveable { mutableStateOf(initialConfig.publicKey ?: "") }
    var preSharedKey by rememberSaveable { mutableStateOf(initialConfig.preSharedKey ?: "") }
    var reserved by rememberSaveable { mutableStateOf(initialConfig.reserved ?: "0,0,0") }
    var localAddress by rememberSaveable { mutableStateOf(initialConfig.localAddress ?: WIREGUARD_LOCAL_ADDRESS_V4) }
    var mtu by rememberSaveable { mutableStateOf(initialConfig.mtu?.toString() ?: WIREGUARD_LOCAL_MTU) }
    var obfsPassword by rememberSaveable { mutableStateOf(initialConfig.obfsPassword ?: "") }
    var portHopping by rememberSaveable { mutableStateOf(initialConfig.portHopping ?: "") }
    var portHoppingInterval by rememberSaveable { mutableStateOf(initialConfig.portHoppingInterval ?: "") }
    var bandwidthDown by rememberSaveable { mutableStateOf(initialConfig.bandwidthDown ?: "") }
    var bandwidthUp by rememberSaveable { mutableStateOf(initialConfig.bandwidthUp ?: "") }
    var network by rememberSaveable { mutableStateOf(initialConfig.network ?: NetworkType.TCP.type) }
    var headerType by rememberSaveable { mutableStateOf(initialConfig.headerType ?: "none") }
    var host by rememberSaveable { mutableStateOf(initialConfig.host ?: "") }
    var path by rememberSaveable { mutableStateOf(initialConfig.path ?: "") }
    var seed by rememberSaveable { mutableStateOf(initialConfig.seed ?: "") }
    var quicSecurity by rememberSaveable { mutableStateOf(initialConfig.quicSecurity ?: "") }
    var quicKey by rememberSaveable { mutableStateOf(initialConfig.quicKey ?: "") }
    var mode by rememberSaveable { mutableStateOf(initialConfig.mode ?: "") }
    var serviceName by rememberSaveable { mutableStateOf(initialConfig.serviceName ?: "") }
    var authority by rememberSaveable { mutableStateOf(initialConfig.authority ?: "") }
    var xhttpMode by rememberSaveable { mutableStateOf(initialConfig.xhttpMode ?: "") }
    var xhttpExtra by rememberSaveable { mutableStateOf(initialConfig.xhttpExtra ?: "") }
    var finalMask by rememberSaveable { mutableStateOf(initialConfig.finalMask ?: "") }
    var kcpMtu by rememberSaveable { mutableStateOf(initialConfig.kcpMtu?.toString() ?: "") }
    var kcpTti by rememberSaveable { mutableStateOf(initialConfig.kcpTti?.toString() ?: "") }
    var browserDialerMode by rememberSaveable { mutableStateOf(initialConfig.browserDialerMode ?: browserDialerOptions.firstOrNull() ?: "Disable") }
    var streamSecurity by rememberSaveable { mutableStateOf(initialConfig.security ?: "") }
    var sni by rememberSaveable { mutableStateOf(initialConfig.sni ?: "") }
    var allowInsecure by rememberSaveable { mutableStateOf(initialConfig.insecure == true) }
    var fingerPrint by rememberSaveable { mutableStateOf(initialConfig.fingerPrint ?: "") }
    var alpn by rememberSaveable { mutableStateOf(initialConfig.alpn ?: "") }
    var publicKeyReality by rememberSaveable { mutableStateOf(initialConfig.publicKey ?: "") }
    var shortId by rememberSaveable { mutableStateOf(initialConfig.shortId ?: "") }
    var spiderX by rememberSaveable { mutableStateOf(initialConfig.spiderX ?: "") }
    var mldsa65Verify by rememberSaveable { mutableStateOf(initialConfig.mldsa65Verify ?: "") }
    var echConfigList by rememberSaveable { mutableStateOf(initialConfig.echConfigList ?: "") }
    var verifyPeerCertByName by rememberSaveable { mutableStateOf(initialConfig.verifyPeerCertByName ?: "") }
    var pinnedCA256 by rememberSaveable { mutableStateOf(initialConfig.pinnedCA256 ?: "") }

    val isVmess = configType == EConfigType.VMESS
    val isVless = configType == EConfigType.VLESS
    val isShadowsocks = configType == EConfigType.SHADOWSOCKS
    val isSocksOrHttp = configType == EConfigType.SOCKS || configType == EConfigType.HTTP
    val isTrojan = configType == EConfigType.TROJAN
    val isWireguard = configType == EConfigType.WIREGUARD
    val isHysteria2 = configType == EConfigType.HYSTERIA2

    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var isFetchingCert by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun buildProfileItem(): ProfileItem = initialConfig.copy(
        remarks = remarks,
        server = address,
        serverPort = port,
        password = password,
        method = when {
            isVmess || isShadowsocks -> method
            isVless -> encryption
            else -> null
        },
        flow = if (isVless) flow else null,
        username = if (isSocksOrHttp) username else null,
        secretKey = if (isWireguard) secretKey else null,
        publicKey = when {
            isWireguard -> publicKey
            streamSecurity == REALITY -> publicKeyReality
            else -> null
        },
        preSharedKey = if (isWireguard) preSharedKey else null,
        reserved = if (isWireguard) reserved else null,
        localAddress = if (isWireguard) localAddress else null,
        mtu = if (isWireguard) mtu.toIntOrNull() else null,
        obfsPassword = if (isHysteria2) obfsPassword else null,
        portHopping = if (isHysteria2) portHopping else null,
        portHoppingInterval = if (isHysteria2) portHoppingInterval else null,
        bandwidthDown = if (isHysteria2) bandwidthDown else null,
        bandwidthUp = if (isHysteria2) bandwidthUp else null,
        network = network,
        headerType = headerType,
        host = host,
        path = path,
        seed = seed,
        quicSecurity = quicSecurity,
        quicKey = quicKey,
        mode = mode,
        serviceName = serviceName,
        authority = authority,
        xhttpMode = xhttpMode,
        xhttpExtra = xhttpExtra.nullIfBlank(),
        finalMask = finalMask.nullIfBlank(),
        kcpMtu = kcpMtu.toIntOrNull(),
        kcpTti = kcpTti.toIntOrNull(),
        browserDialerMode = if (network in listOf(NetworkType.WS.type, NetworkType.XHTTP.type)) browserDialerMode.nullIfBlank() else null,
        security = streamSecurity,
        sni = sni,
        insecure = allowInsecure,
        fingerPrint = fingerPrint,
        alpn = alpn,
        shortId = shortId,
        spiderX = spiderX,
        mldsa65Verify = mldsa65Verify,
        echConfigList = echConfigList,
        verifyPeerCertByName = verifyPeerCertByName,
        pinnedCA256 = pinnedCA256
    )

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            AppTopBar(
                title = configType.toString(),
                onBackClick = onBackClick,
                actions = {
                    if (guid.isNotEmpty() && !isRunning) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(painterResource(R.drawable.ic_delete_24dp), stringResource(R.string.menu_item_del_config))
                        }
                    }
                    IconButton(onClick = {
                        onSave(buildProfileItem())
                    }) {
                        Icon(painterResource(R.drawable.ic_fab_check), stringResource(R.string.menu_item_save_config))
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { FormTextField(stringResource(R.string.server_lab_remarks), remarks, { remarks = it }) }
            item { FormTextField(stringResource(R.string.server_lab_address), address, { address = it }) }
            if (configType != EConfigType.HYSTERIA2) item { FormTextField(stringResource(R.string.server_lab_port), port, { port = it }, keyboardType = KeyboardType.Number) }
            when {
                isVmess || isTrojan || isShadowsocks || isHysteria2 -> item {
                    FormTextField(stringResource(when { isTrojan||isShadowsocks||isHysteria2 -> R.string.server_lab_id3 else -> R.string.server_lab_id }), password, { password = it })
                }
                isVless -> {
                    item { FormTextField(stringResource(R.string.server_lab_id), password, { password = it }) }
                    item { FormTextField(stringResource(R.string.server_lab_encryption), encryption, { encryption = it }) }
                    item { FormDropdownField(stringResource(R.string.server_lab_flow), flow, flowOptions, { flow = it }) }
                }
                isSocksOrHttp -> {
                    item { FormTextField(stringResource(R.string.server_lab_security4), username, { username = it }) }
                    item { FormTextField(stringResource(R.string.server_lab_id4), password, { password = it }) }
                }
            }
            if (isVmess) item { FormDropdownField(stringResource(R.string.server_lab_security), method, securityOptions, { method = it }) }
            if (isShadowsocks) item { FormDropdownField(stringResource(R.string.server_lab_security), method, ssSecurityOptions, { method = it }) }
            if (isWireguard) {
                item { FormTextField(stringResource(R.string.server_lab_secret_key), secretKey, { secretKey = it }) }
                item { FormTextField(stringResource(R.string.server_lab_public_key), publicKey, { publicKey = it }) }
                item { FormTextField(stringResource(R.string.server_lab_preshared_key), preSharedKey, { preSharedKey = it }) }
                item { FormTextField(stringResource(R.string.server_lab_reserved), reserved, { reserved = it }) }
                item { FormTextField(stringResource(R.string.server_lab_local_address), localAddress, { localAddress = it }) }
                item { FormTextField(stringResource(R.string.server_lab_local_mtu), mtu, { mtu = it }, keyboardType = KeyboardType.Number) }
            }
            if (isHysteria2) {
                item { FormTextField(stringResource(R.string.server_obfs_password), obfsPassword, { obfsPassword = it }) }
                item { FormTextField(stringResource(R.string.server_lab_port_hop), portHopping, { portHopping = it }) }
                item { FormTextField(stringResource(R.string.server_lab_port_hop_interval), portHoppingInterval, { portHoppingInterval = it }) }
                item { FormTextField(stringResource(R.string.server_lab_bandwidth_down), bandwidthDown, { bandwidthDown = it }) }
                item { FormTextField(stringResource(R.string.server_lab_bandwidth_up), bandwidthUp, { bandwidthUp = it }) }
            }
            item { FormDropdownField(stringResource(R.string.server_lab_network), network, networkOptions, { network = it }) }
            val headerOptions = when (network) {
                NetworkType.TCP.type -> tcpHeaderOptions
                NetworkType.KCP.type -> kcpHeaderOptions
                NetworkType.GRPC.type -> grpcModeOptions
                NetworkType.XHTTP.type -> xhttpModeOptions
                else -> listOf("---")
            }
            if (headerOptions.size > 1) {
                item {
                    FormDropdownField(
                        stringResource(when (network) { NetworkType.GRPC.type -> R.string.server_lab_mode_type; NetworkType.XHTTP.type -> R.string.server_lab_xhttp_mode; else -> R.string.server_lab_head_type }),
                        headerType, headerOptions, { headerType = it }
                    )
                }
            }
            item { FormTextField(stringResource(when (network) {
                NetworkType.TCP.type, NetworkType.HTTP_UPGRADE.type, NetworkType.XHTTP.type, NetworkType.H2.type -> R.string.server_lab_request_host_http
                NetworkType.WS.type -> R.string.server_lab_request_host_ws
                NetworkType.GRPC.type -> R.string.server_lab_request_host_grpc
                else -> R.string.server_lab_request_host6
            }), host, { host = it }) }
            item { FormTextField(stringResource(when (network) {
                NetworkType.KCP.type -> R.string.server_lab_path_kcp
                NetworkType.WS.type -> R.string.server_lab_path_ws
                NetworkType.HTTP_UPGRADE.type -> R.string.server_lab_path_httpupgrade
                NetworkType.XHTTP.type -> R.string.server_lab_path_xhttp
                NetworkType.H2.type -> R.string.server_lab_path_h2
                NetworkType.GRPC.type -> R.string.server_lab_path_grpc
                else -> R.string.server_lab_path
            }), path, { path = it }) }
            if (network == NetworkType.XHTTP.type) {
                item { FormTextField(stringResource(R.string.server_lab_xhttp_extra), xhttpExtra, { xhttpExtra = it }) }
            }
            if (network == NetworkType.KCP.type) {
                item { FormTextField(stringResource(R.string.server_lab_kcp_mtu), kcpMtu, { kcpMtu = it }, keyboardType = KeyboardType.Number) }
                item { FormTextField(stringResource(R.string.server_lab_kcp_tti), kcpTti, { kcpTti = it }, keyboardType = KeyboardType.Number) }
            }
            item { FormTextField(stringResource(R.string.server_lab_final_mask), finalMask, { finalMask = it }) }
            if (network == NetworkType.WS.type || network == NetworkType.XHTTP.type) {
                item { FormDropdownField(stringResource(R.string.server_lab_browser_dialer), browserDialerMode, browserDialerOptions, { browserDialerMode = it }) }
            }
            item { FormDropdownField(stringResource(R.string.server_lab_stream_security), streamSecurity, streamSecurityOptions, { streamSecurity = it }) }
            if (streamSecurity.isNotBlank()) {
                item { FormTextField(stringResource(R.string.server_lab_sni), sni, { sni = it }) }
                item { FormDropdownField(stringResource(R.string.server_lab_stream_fingerprint), fingerPrint, uTlsOptions, { fingerPrint = it }) }
                if (streamSecurity == TLS) {
                    item { SettingsSwitchItem(title = stringResource(R.string.server_lab_allow_insecure), checked = allowInsecure, onCheckedChange = { allowInsecure = it }) }
                    item { FormDropdownField(stringResource(R.string.server_lab_stream_alpn), alpn, alpnOptions, { alpn = it }) }
                    item { FormTextField(stringResource(R.string.server_lab_ech_config_list), echConfigList, { echConfigList = it }) }
                    item { FormTextField(stringResource(R.string.server_lab_verify_peer_cert_by_name), verifyPeerCertByName, { verifyPeerCertByName = it }) }
                    item { FormTextField(stringResource(R.string.server_lab_pinned_ca256), pinnedCA256, { pinnedCA256 = it }) }
                    item {
                        Button(
                            onClick = {
                                if (address.isBlank()) { context.toast(R.string.server_lab_address); return@Button }
                                if (configType != EConfigType.HYSTERIA2 && (port.toIntOrNull() ?: 0) <= 0) { context.toast(R.string.server_lab_port); return@Button }
                                val temp = buildProfileItem()
                                scope.launch {
                                    isFetchingCert = true
                                    try {
                                        val sha256 = withContext(Dispatchers.IO) { CertificateFingerprintManager.fetchForManualFill(temp) }
                                        if (sha256.isNullOrBlank()) context.toast(R.string.toast_fetch_cert_sha256_failed) else {
                                            pinnedCA256 = sha256
                                            context.toastSuccess(R.string.toast_fetch_cert_sha256_success)
                                        }
                                    } finally { isFetchingCert = false }
                                }
                            },
                            enabled = !isFetchingCert,
                            modifier = Modifier.padding(start = 16.dp)
                        ) { Text(stringResource(R.string.pinned_ca256_action_fetch)) }
                    }
                } else if (streamSecurity == REALITY) {
                    item { FormTextField(stringResource(R.string.server_lab_public_key), publicKeyReality, { publicKeyReality = it }) }
                    item { FormTextField(stringResource(R.string.server_lab_short_id), shortId, { shortId = it }) }
                    item { FormTextField(stringResource(R.string.server_lab_spider_x), spiderX, { spiderX = it }) }
                    item { FormTextField(stringResource(R.string.server_lab_mldsa65_verify), mldsa65Verify, { mldsa65Verify = it }) }
                }
            }
        }
    }
    if (showDeleteDialog) {
        ConfirmDialog(
            message = stringResource(R.string.del_config_comfirm),
            confirmText = stringResource(android.R.string.ok),
            dismissText = stringResource(android.R.string.cancel),
            onConfirm = { showDeleteDialog = false; onDelete() },
            onDismiss = { showDeleteDialog = false }
        )
    }
}
