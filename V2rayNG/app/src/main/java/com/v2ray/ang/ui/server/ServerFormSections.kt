package com.v2ray.ang.ui.server

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig.REALITY
import com.v2ray.ang.AppConfig.TLS
import com.v2ray.ang.R
import com.v2ray.ang.enums.BalancerStrategyType
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.NetworkType
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.StringOptions
import com.v2ray.ang.ui.compose.rememberStringOptions
import com.v2ray.ang.ui.compose.verticalScrollbar
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private object ServerDimens {
    val FieldSpacing = 8.dp
    val ContentHorizontal = 16.dp
    val ChainIndexWidth = 24.dp
    val ChainRowPadding = 4.dp
    val InlineProgress = 16.dp
    val InlineProgressStroke = 2.dp
    val InlineSpacing = 8.dp
}

@Composable
private fun ServerTextField(
    @StringRes labelRes: Int,
    field: ServerField,
    value: String,
    onAction: (ServerAction) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) = FormTextField(
    label = stringResource(labelRes),
    value = value,
    onValueChange = { onAction(ServerAction.TextChanged(field, it)) },
    keyboardType = keyboardType,
)

@Composable
private fun ServerDropdownField(
    @StringRes labelRes: Int,
    field: ServerField,
    value: String,
    options: StringOptions,
    onAction: (ServerAction) -> Unit,
    editable: Boolean = false,
) = FormDropdownField(
    label = stringResource(labelRes),
    value = value,
    options = options,
    onValueChange = { onAction(ServerAction.TextChanged(field, it)) },
    editable = editable,
)

@Immutable
private data class FieldOptions(
    val networks: StringOptions,
    val tcpHeaders: StringOptions,
    val kcpHeaders: StringOptions,
    val grpcModes: StringOptions,
    val xhttpModes: StringOptions,
    val streamSecurities: StringOptions,
    val uTls: StringOptions,
    val alpn: StringOptions,
    val browserDialer: StringOptions,
    val vmessSecurities: StringOptions,
    val ssSecurities: StringOptions,
    val flows: StringOptions,
    val policyGroupTypes: StringOptions,
)

@Composable
private fun rememberFieldOptions(): FieldOptions = FieldOptions(
    networks = rememberStringOptions(R.array.networks),
    tcpHeaders = rememberStringOptions(R.array.header_type_tcp),
    kcpHeaders = rememberStringOptions(R.array.header_type_kcp_and_quic),
    grpcModes = rememberStringOptions(R.array.mode_type_grpc),
    xhttpModes = rememberStringOptions(R.array.xhttp_mode),
    streamSecurities = rememberStringOptions(R.array.streamsecurityxs),
    uTls = rememberStringOptions(R.array.streamsecurity_utls),
    alpn = rememberStringOptions(R.array.streamsecurity_alpn),
    browserDialer = rememberStringOptions(R.array.browser_dialer_mode_value),
    vmessSecurities = rememberStringOptions(R.array.securitys),
    ssSecurities = rememberStringOptions(R.array.ss_securitys),
    flows = rememberStringOptions(R.array.flows),
    policyGroupTypes = rememberStringOptions(R.array.policy_group_type),
)

private data class ProtocolSpec(
    val showNetwork: Boolean = false,
    val showStreamSecurity: Boolean = false,
)

private fun EConfigType.spec(): ProtocolSpec = when (this) {
    EConfigType.VMESS, EConfigType.VLESS, EConfigType.TROJAN, EConfigType.SHADOWSOCKS ->
        ProtocolSpec(showNetwork = true, showStreamSecurity = true)
    else -> ProtocolSpec()
}

@Composable
internal fun ProtocolForm(
    configType: EConfigType,
    form: ServerForm,
    isFetchingCert: Boolean,
    onAction: (ServerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = rememberFieldOptions()
    val spec = remember(configType) { configType.spec() }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ServerDimens.FieldSpacing),
    ) {
        ServerTextField(R.string.server_lab_remarks, ServerField.REMARKS, form.remarks, onAction)
        ServerTextField(R.string.server_lab_address, ServerField.ADDRESS, form.address, onAction)
        ServerTextField(
            R.string.server_lab_port, ServerField.PORT, form.port, onAction, KeyboardType.Number
        )

        ProtocolFields(configType, form, isFetchingCert, options, onAction)

        if (spec.showNetwork) NetworkFields(form, options, onAction)
        if (spec.showStreamSecurity) StreamSecurityFields(form, isFetchingCert, options, onAction)
    }
}

@Composable
private fun ProtocolFields(
    configType: EConfigType,
    form: ServerForm,
    isFetchingCert: Boolean,
    options: FieldOptions,
    onAction: (ServerAction) -> Unit,
) {
    when (configType) {
        EConfigType.VMESS -> {
            ServerTextField(R.string.server_lab_id, ServerField.PASSWORD, form.password, onAction)
            ServerDropdownField(
                R.string.server_lab_security, ServerField.METHOD, form.method,
                options.vmessSecurities, onAction,
            )
        }
        EConfigType.VLESS -> {
            ServerTextField(R.string.server_lab_id, ServerField.PASSWORD, form.password, onAction)
            ServerTextField(
                R.string.server_lab_encryption, ServerField.ENCRYPTION, form.encryption, onAction
            )
            ServerDropdownField(
                R.string.server_lab_flow, ServerField.FLOW, form.flow, options.flows, onAction,
            )
        }
        EConfigType.TROJAN ->
            ServerTextField(R.string.server_lab_id3, ServerField.PASSWORD, form.password, onAction)
        EConfigType.SHADOWSOCKS -> {
            ServerTextField(R.string.server_lab_id3, ServerField.PASSWORD, form.password, onAction)
            ServerDropdownField(
                R.string.server_lab_security, ServerField.METHOD, form.method,
                options.ssSecurities, onAction,
            )
        }
        EConfigType.WIREGUARD -> {
            ServerTextField(R.string.server_lab_secret_key, ServerField.SECRET_KEY, form.secretKey, onAction)
            ServerTextField(R.string.server_lab_public_key, ServerField.PUBLIC_KEY, form.publicKey, onAction)
            ServerTextField(R.string.server_lab_preshared_key, ServerField.PRE_SHARED_KEY, form.preSharedKey, onAction)
            ServerTextField(R.string.server_lab_reserved, ServerField.RESERVED, form.reserved, onAction)
            ServerTextField(R.string.server_lab_local_address, ServerField.LOCAL_ADDRESS, form.localAddress, onAction)
            ServerTextField(R.string.server_lab_local_mtu, ServerField.MTU, form.mtu, onAction, KeyboardType.Number)
            ServerTextField(R.string.server_lab_final_mask, ServerField.FINAL_MASK, form.finalMask, onAction)
        }
        EConfigType.HYSTERIA2 -> {
            ServerTextField(R.string.server_lab_id3, ServerField.PASSWORD, form.password, onAction)
            ServerTextField(R.string.server_obfs_password, ServerField.OBFS_PASSWORD, form.obfsPassword, onAction)
            ServerTextField(R.string.server_lab_port_hop, ServerField.PORT_HOPPING, form.portHopping, onAction)
            ServerTextField(
                R.string.server_lab_port_hop_interval, ServerField.PORT_HOPPING_INTERVAL,
                form.portHoppingInterval, onAction,
            )
            ServerTextField(R.string.server_lab_bandwidth_down, ServerField.BANDWIDTH_DOWN, form.bandwidthDown, onAction)
            ServerTextField(R.string.server_lab_bandwidth_up, ServerField.BANDWIDTH_UP, form.bandwidthUp, onAction)
            ServerTextField(R.string.server_lab_final_mask, ServerField.FINAL_MASK, form.finalMask, onAction)
            SettingsSwitchItem(
                title = stringResource(R.string.server_lab_allow_insecure),
                checked = form.allowInsecure,
                onCheckedChange = { onAction(ServerAction.FlagChanged(ServerFlag.ALLOW_INSECURE, it)) },
            )
            ServerTextField(R.string.server_lab_sni, ServerField.SNI, form.sni, onAction)
            ServerTextField(R.string.server_lab_ech_config_list, ServerField.ECH_CONFIG_LIST, form.echConfigList, onAction)
            PinnedCertFields(form.pinnedCA256, isFetchingCert, onAction)
        }
        else -> {
            ServerTextField(R.string.server_lab_security4, ServerField.USERNAME, form.username, onAction)
            ServerTextField(R.string.server_lab_id4, ServerField.PASSWORD, form.password, onAction)
        }
    }
}

@Composable
private fun PinnedCertFields(
    pinnedCa256: String,
    isFetchingCert: Boolean,
    onAction: (ServerAction) -> Unit,
) {
    ServerTextField(R.string.server_lab_pinned_ca256, ServerField.PINNED_CA256, pinnedCa256, onAction)
    Button(
        onClick = { onAction(ServerAction.FetchCertificate) },
        enabled = !isFetchingCert,
        modifier = Modifier.padding(horizontal = ServerDimens.ContentHorizontal),
    ) {
        if (isFetchingCert) {
            CircularProgressIndicator(
                modifier = Modifier.size(ServerDimens.InlineProgress),
                strokeWidth = ServerDimens.InlineProgressStroke,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(ServerDimens.InlineSpacing))
        }
        Text(stringResource(R.string.pinned_ca256_action_fetch))
    }
}

@Composable
private fun NetworkFields(
    form: ServerForm,
    options: FieldOptions,
    onAction: (ServerAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ServerDimens.FieldSpacing)) {
        ServerDropdownField(
            R.string.server_lab_network, ServerField.NETWORK, form.network, options.networks, onAction
        )

        val headerOptions = when (form.network) {
            NetworkType.TCP.type -> options.tcpHeaders
            NetworkType.KCP.type -> options.kcpHeaders
            NetworkType.GRPC.type -> options.grpcModes
            NetworkType.XHTTP.type -> options.xhttpModes
            else -> null
        }
        if (headerOptions != null && headerOptions.size > 1) {
            val headerField = when (form.network) {
                NetworkType.GRPC.type -> ServerField.MODE
                NetworkType.XHTTP.type -> ServerField.XHTTP_MODE
                else -> ServerField.HEADER_TYPE
            }
            ServerDropdownField(
                labelRes = when (form.network) {
                    NetworkType.GRPC.type -> R.string.server_lab_mode_type
                    NetworkType.XHTTP.type -> R.string.server_lab_xhttp_mode
                    else -> R.string.server_lab_head_type
                },
                field = headerField,
                value = headerField.get(form),
                options = headerOptions,
                onAction = onAction,
            )
        }

        val hostField =
            if (form.network == NetworkType.GRPC.type) ServerField.AUTHORITY else ServerField.HOST
        ServerTextField(
            labelRes = when (form.network) {
                NetworkType.TCP.type,
                NetworkType.HTTP_UPGRADE.type,
                NetworkType.XHTTP.type,
                NetworkType.H2.type -> R.string.server_lab_request_host_http
                NetworkType.WS.type -> R.string.server_lab_request_host_ws
                NetworkType.GRPC.type -> R.string.server_lab_request_host_grpc
                else -> R.string.server_lab_request_host6
            },
            field = hostField,
            value = hostField.get(form),
            onAction = onAction,
        )

        if (form.network != NetworkType.KCP.type) {
            val pathField =
                if (form.network == NetworkType.GRPC.type) ServerField.SERVICE_NAME else ServerField.PATH
            ServerTextField(
                labelRes = when (form.network) {
                    NetworkType.WS.type -> R.string.server_lab_path_ws
                    NetworkType.HTTP_UPGRADE.type -> R.string.server_lab_path_httpupgrade
                    NetworkType.XHTTP.type -> R.string.server_lab_path_xhttp
                    NetworkType.H2.type -> R.string.server_lab_path_h2
                    NetworkType.GRPC.type -> R.string.server_lab_path_grpc
                    else -> R.string.server_lab_path
                },
                field = pathField,
                value = pathField.get(form),
                onAction = onAction,
            )
        }

        if (form.network == NetworkType.XHTTP.type) {
            ServerTextField(
                R.string.server_lab_xhttp_extra, ServerField.XHTTP_EXTRA, form.xhttpExtra, onAction
            )
        }
        if (form.network == NetworkType.KCP.type) {
            ServerTextField(R.string.server_lab_path_kcp, ServerField.SEED, form.seed, onAction)
            ServerTextField(R.string.server_lab_kcp_mtu, ServerField.KCP_MTU, form.kcpMtu, onAction, KeyboardType.Number)
            ServerTextField(R.string.server_lab_kcp_tti, ServerField.KCP_TTI, form.kcpTti, onAction, KeyboardType.Number)
        }
        ServerTextField(R.string.server_lab_final_mask, ServerField.FINAL_MASK, form.finalMask, onAction)
        if (form.network == NetworkType.WS.type || form.network == NetworkType.XHTTP.type) {
            ServerDropdownField(
                R.string.server_lab_browser_dialer, ServerField.BROWSER_DIALER,
                form.browserDialerMode, options.browserDialer, onAction,
            )
        }
    }
}

@Composable
private fun StreamSecurityFields(
    form: ServerForm,
    isFetchingCert: Boolean,
    options: FieldOptions,
    onAction: (ServerAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ServerDimens.FieldSpacing)) {
        ServerDropdownField(
            R.string.server_lab_stream_security, ServerField.STREAM_SECURITY,
            form.streamSecurity, options.streamSecurities, onAction,
        )
        if (form.streamSecurity.isBlank()) return@Column

        ServerTextField(R.string.server_lab_sni, ServerField.SNI, form.sni, onAction)
        ServerDropdownField(
            R.string.server_lab_stream_fingerprint, ServerField.FINGERPRINT,
            form.fingerPrint, options.uTls, onAction,
        )

        when (form.streamSecurity) {
            TLS -> {
                SettingsSwitchItem(
                    title = stringResource(R.string.server_lab_allow_insecure),
                    checked = form.allowInsecure,
                    onCheckedChange = {
                        onAction(ServerAction.FlagChanged(ServerFlag.ALLOW_INSECURE, it))
                    },
                )
                ServerDropdownField(
                    R.string.server_lab_stream_alpn, ServerField.ALPN, form.alpn, options.alpn, onAction
                )
                ServerTextField(
                    R.string.server_lab_ech_config_list, ServerField.ECH_CONFIG_LIST,
                    form.echConfigList, onAction,
                )
                ServerTextField(
                    R.string.server_lab_verify_peer_cert_by_name, ServerField.VERIFY_PEER_CERT,
                    form.verifyPeerCertByName, onAction,
                )
                PinnedCertFields(form.pinnedCA256, isFetchingCert, onAction)
            }
            REALITY -> {
                ServerTextField(
                    R.string.server_lab_public_key, ServerField.PUBLIC_KEY_REALITY,
                    form.publicKeyReality, onAction,
                )
                ServerTextField(R.string.server_lab_short_id, ServerField.SHORT_ID, form.shortId, onAction)
                ServerTextField(R.string.server_lab_spider_x, ServerField.SPIDER_X, form.spiderX, onAction)
                ServerTextField(
                    R.string.server_lab_mldsa65_verify, ServerField.MLDSA65_VERIFY,
                    form.mldsa65Verify, onAction,
                )
            }
        }
    }
}

@Composable
internal fun PolicyGroupForm(
    form: ServerForm,
    options: ServerOptions,
    onAction: (ServerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fieldOptions = rememberFieldOptions()
    val allLabel = stringResource(R.string.filter_config_all)
    val subs = options.subscriptions

    val typeLabels = fieldOptions.policyGroupTypes
    val typePairs = remember(typeLabels) {
        typeLabels.values.mapIndexed { position, label -> position.toString() to label }
    }
    val typeLabel = typePairs.firstOrNull { it.first == form.groupType }?.second
        ?: typePairs.firstOrNull()?.second.orEmpty()

    val subPairs = remember(subs, allLabel) {
        subs.map { it.id to it.name.ifBlank { allLabel } }
    }
    val subLabels = remember(subPairs) { StringOptions(subPairs.map { it.second }) }
    val subLabel = subPairs.firstOrNull { it.first == form.groupSubId }?.second
        ?: subLabels.firstOrNull().orEmpty()

    val supportsObservatory = BalancerStrategyType.from(form.groupType).supportsObservatory

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ServerDimens.FieldSpacing),
    ) {
        ServerTextField(R.string.server_lab_remarks, ServerField.REMARKS, form.remarks, onAction)

        FormDropdownField(
            label = stringResource(R.string.title_policy_group_type),
            value = typeLabel,
            options = typeLabels,
            onValueChange = { label ->
                val value = typePairs.firstOrNull { it.second == label }?.first ?: return@FormDropdownField
                onAction(ServerAction.TextChanged(ServerField.GROUP_TYPE, value))
            },
        )

        FormDropdownField(
            label = stringResource(R.string.title_policy_group_subscription_id),
            value = subLabel,
            options = subLabels,
            onValueChange = { label ->
                val id = subPairs.firstOrNull { it.second == label }?.first.orEmpty()
                onAction(ServerAction.TextChanged(ServerField.GROUP_SUB_ID, id))
            },
        )

        ServerTextField(
            R.string.title_policy_group_subscription_filter, ServerField.GROUP_FILTER,
            form.groupFilter, onAction,
        )

        if (supportsObservatory) {
            SettingsSwitchItem(
                title = stringResource(R.string.title_policy_group_test_outbounds),
                checked = form.groupTestOutbounds,
                onCheckedChange = {
                    onAction(ServerAction.FlagChanged(ServerFlag.GROUP_TEST_OUTBOUNDS, it))
                },
            )
            if (form.groupTestOutbounds) {
                val fallbackOptions = remember(options.fallbackTags) {
                    StringOptions(options.fallbackTags)
                }
                ServerDropdownField(
                    R.string.title_policy_group_fallback, ServerField.GROUP_FALLBACK_TAG,
                    form.groupFallbackTag, fallbackOptions, onAction,
                    editable = true,
                )
            }
        }
    }
}

@Composable
internal fun ProxyChainForm(
    remarks: String,
    members: List<ChainMember>,
    candidates: StringOptions,
    onAction: (ServerAction) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromId = from.key as? String ?: return@rememberReorderableLazyListState
        val toId = to.key as? String ?: return@rememberReorderableLazyListState
        onAction(ServerAction.ChainMemberMoved(fromId, toId))
    }

    LazyColumn(
        state = listState,
        modifier = modifier.verticalScrollbar(listState),
        contentPadding = contentPadding,
    ) {
        item(key = "remarks") {
            FormTextField(
                label = stringResource(R.string.server_lab_remarks),
                value = remarks,
                onValueChange = { onAction(ServerAction.TextChanged(ServerField.REMARKS, it)) },
                modifier = Modifier.padding(horizontal = ServerDimens.ContentHorizontal)
            )
        }
        item(key = "members_title") {
            Text(
                text = stringResource(R.string.server_proxy_chain_members),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(
                    start = ServerDimens.ContentHorizontal,
                    top = ServerDimens.FieldSpacing,
                    bottom = ServerDimens.FieldSpacing,
                ),
            )
        }
        itemsIndexed(items = members, key = { _, member -> member.id }) { index, member ->
            ReorderableItem(reorderState, key = member.id) { isDragging ->
                ReorderableListItem(
                    scope = this,
                    isDragging = isDragging,
                    modifier = Modifier.padding(horizontal = ServerDimens.ContentHorizontal)
                ) {
                    ChainMemberRow(
                        ordinal = index + 1,
                        member = member,
                        options = candidates,
                        isDragging = isDragging,
                        onAction = onAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChainMemberRow(
    ordinal: Int,
    member: ChainMember,
    options: StringOptions,
    isDragging: Boolean,
    onAction: (ServerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(ServerDimens.ChainRowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$ordinal",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(start = ServerDimens.ContentHorizontal)
                .width(ServerDimens.ChainIndexWidth),
        )
        FormDropdownField(
            label = stringResource(R.string.server_lab_remarks),
            placeholder = stringResource(R.string.server_proxy_chain_member_unselected),
            value = member.remarks,
            options = options,
            onValueChange = { onAction(ServerAction.ChainMemberChanged(member.id, it)) },
            editable = true,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onAction(ServerAction.ChainMemberRemoveClicked(member.id)) }) {
            Icon(
                painterResource(R.drawable.ic_delete_24dp),
                contentDescription = stringResource(R.string.action_delete),
            )
        }
    }
}
