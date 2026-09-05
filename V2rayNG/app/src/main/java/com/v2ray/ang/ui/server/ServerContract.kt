package com.v2ray.ang.ui.server

import androidx.compose.runtime.Immutable
import com.v2ray.ang.AppConfig.DEFAULT_PORT
import com.v2ray.ang.AppConfig.REALITY
import com.v2ray.ang.AppConfig.TLS
import com.v2ray.ang.AppConfig.WIREGUARD_LOCAL_ADDRESS_V4
import com.v2ray.ang.AppConfig.WIREGUARD_LOCAL_MTU
import com.v2ray.ang.dto.SubscriptionOption
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.BalancerStrategyType
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.NetworkType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.ui.base.BaseAction
import com.v2ray.ang.ui.base.BaseEvent
import com.v2ray.ang.ui.base.BaseUiState
import java.util.UUID

@Immutable
data class ChainMember(val id: String = UUID.randomUUID().toString(), val remarks: String = "")

@Immutable
data class ServerOptions(
    val subscriptions: List<SubscriptionOption> = emptyList(),
    val profileRemarks: List<String> = emptyList(),
    val fallbackTags: List<String> = emptyList(),
)

@Immutable
data class ServerForm(
    val remarks: String = "",
    val address: String = "",
    val port: String = DEFAULT_PORT.toString(),
    val password: String = "",
    val method: String = "",
    val flow: String = "",
    val encryption: String = "",
    val username: String = "",
    val secretKey: String = "",
    val publicKey: String = "",
    val preSharedKey: String = "",
    val reserved: String = "0,0,0",
    val localAddress: String = WIREGUARD_LOCAL_ADDRESS_V4,
    val mtu: String = WIREGUARD_LOCAL_MTU,
    val obfsPassword: String = "",
    val portHopping: String = "",
    val portHoppingInterval: String = "",
    val bandwidthDown: String = "",
    val bandwidthUp: String = "",
    val network: String = NetworkType.TCP.type,
    val headerType: String = "none",
    val host: String = "",
    val path: String = "",
    val xhttpExtra: String = "",
    val finalMask: String = "",
    val kcpMtu: String = "",
    val kcpTti: String = "",
    val browserDialerMode: String = "",
    val streamSecurity: String = "",
    val sni: String = "",
    val allowInsecure: Boolean = false,
    val fingerPrint: String = "",
    val alpn: String = "",
    val publicKeyReality: String = "",
    val shortId: String = "",
    val spiderX: String = "",
    val mldsa65Verify: String = "",
    val echConfigList: String = "",
    val verifyPeerCertByName: String = "",
    val pinnedCA256: String = "",
    val groupType: String = "0",
    val groupSubId: String = "",
    val groupFilter: String = "",
    val groupTestOutbounds: Boolean = true,
    val groupFallbackTag: String = "",
    val chainMembers: List<ChainMember> = listOf(ChainMember(), ChainMember()),
    val mode: String = "",
    val xhttpMode: String = "",
    val serviceName: String = "",
    val authority: String = "",
    val seed: String = "",
) {

    fun toProfileItem(initial: ProfileItem, configType: EConfigType): ProfileItem {
        val isVmess = configType == EConfigType.VMESS
        val isVless = configType == EConfigType.VLESS
        val isShadowsocks = configType == EConfigType.SHADOWSOCKS
        val isSocksOrHttp = configType == EConfigType.SOCKS || configType == EConfigType.HTTP
        val isWireguard = configType == EConfigType.WIREGUARD
        val isHysteria2 = configType == EConfigType.HYSTERIA2

        return initial.copy(
            configType = configType,
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
            mode = mode.nullIfBlank(),
            xhttpMode = xhttpMode.nullIfBlank(),
            serviceName = serviceName.nullIfBlank(),
            authority = authority.nullIfBlank(),
            host = host,
            path = path,
            xhttpExtra = xhttpExtra.nullIfBlank(),
            finalMask = finalMask.nullIfBlank(),
            seed = seed.nullIfBlank(),
            kcpMtu = kcpMtu.toIntOrNull(),
            kcpTti = kcpTti.toIntOrNull(),
            browserDialerMode =
                if (network in listOf(NetworkType.WS.type, NetworkType.XHTTP.type))
                    browserDialerMode.nullIfBlank() else null,
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
            pinnedCA256 = pinnedCA256,
        )
    }

    companion object {
        fun from(profile: ProfileItem): ServerForm {
            val members = profile.proxyChainProfiles
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.map { ChainMember(remarks = it) }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(ChainMember(), ChainMember())

            val groupType = profile.policyGroupType?.toIntOrNull()?.toString() ?: "0"
            return ServerForm(
                remarks = profile.remarks,
                address = profile.server.orEmpty(),
                port = profile.serverPort ?: DEFAULT_PORT.toString(),
                password = profile.password.orEmpty(),
                method = profile.method.orEmpty(),
                flow = profile.flow.orEmpty(),
                encryption = profile.method.orEmpty(),
                username = profile.username.orEmpty(),
                secretKey = profile.secretKey.orEmpty(),
                publicKey = profile.publicKey.orEmpty(),
                preSharedKey = profile.preSharedKey.orEmpty(),
                reserved = profile.reserved ?: "0,0,0",
                localAddress = profile.localAddress ?: WIREGUARD_LOCAL_ADDRESS_V4,
                mtu = profile.mtu?.toString() ?: WIREGUARD_LOCAL_MTU,
                obfsPassword = profile.obfsPassword.orEmpty(),
                portHopping = profile.portHopping.orEmpty(),
                portHoppingInterval = profile.portHoppingInterval.orEmpty(),
                bandwidthDown = profile.bandwidthDown.orEmpty(),
                bandwidthUp = profile.bandwidthUp.orEmpty(),
                network = profile.network ?: NetworkType.TCP.type,
                headerType = profile.headerType ?: "none",
                mode = profile.mode.orEmpty(),
                xhttpMode = profile.xhttpMode.orEmpty(),
                serviceName = profile.serviceName.orEmpty(),
                authority = profile.authority.orEmpty(),
                host = profile.host.orEmpty(),
                path = profile.path.orEmpty(),
                xhttpExtra = profile.xhttpExtra.orEmpty(),
                finalMask = profile.finalMask.orEmpty(),
                seed = profile.seed.orEmpty(),
                kcpMtu = profile.kcpMtu?.toString().orEmpty(),
                kcpTti = profile.kcpTti?.toString().orEmpty(),
                browserDialerMode = profile.browserDialerMode.orEmpty(),
                streamSecurity = profile.security.orEmpty(),
                sni = profile.sni.orEmpty(),
                allowInsecure = profile.insecure == true,
                fingerPrint = profile.fingerPrint.orEmpty(),
                alpn = profile.alpn.orEmpty(),
                publicKeyReality = profile.publicKey.orEmpty(),
                shortId = profile.shortId.orEmpty(),
                spiderX = profile.spiderX.orEmpty(),
                mldsa65Verify = profile.mldsa65Verify.orEmpty(),
                echConfigList = profile.echConfigList.orEmpty(),
                verifyPeerCertByName = profile.verifyPeerCertByName.orEmpty(),
                pinnedCA256 = profile.pinnedCA256.orEmpty(),
                groupType = groupType,
                groupSubId = profile.policyGroupSubscriptionId.orEmpty(),
                groupFilter = profile.policyGroupFilter.orEmpty(),
                groupTestOutbounds = profile.policyGroupTestOutbounds != false ||
                        !BalancerStrategyType.from(groupType).supportsObservatory,
                groupFallbackTag = profile.policyGroupFallbackTag.orEmpty(),
                chainMembers = members,
            )
        }
    }
}

enum class ServerField(
    val get: (ServerForm) -> String,
    val set: (ServerForm, String) -> ServerForm,
) {
    REMARKS({ it.remarks }, { f, v -> f.copy(remarks = v) }),
    ADDRESS({ it.address }, { f, v -> f.copy(address = v) }),
    PORT({ it.port }, { f, v -> f.copy(port = v) }),
    PASSWORD({ it.password }, { f, v -> f.copy(password = v) }),
    METHOD({ it.method }, { f, v -> f.copy(method = v) }),
    FLOW({ it.flow }, { f, v -> f.copy(flow = v) }),
    ENCRYPTION({ it.encryption }, { f, v -> f.copy(encryption = v) }),
    USERNAME({ it.username }, { f, v -> f.copy(username = v) }),
    SECRET_KEY({ it.secretKey }, { f, v -> f.copy(secretKey = v) }),
    PUBLIC_KEY({ it.publicKey }, { f, v -> f.copy(publicKey = v) }),
    PRE_SHARED_KEY({ it.preSharedKey }, { f, v -> f.copy(preSharedKey = v) }),
    RESERVED({ it.reserved }, { f, v -> f.copy(reserved = v) }),
    LOCAL_ADDRESS({ it.localAddress }, { f, v -> f.copy(localAddress = v) }),
    MTU({ it.mtu }, { f, v -> f.copy(mtu = v) }),
    OBFS_PASSWORD({ it.obfsPassword }, { f, v -> f.copy(obfsPassword = v) }),
    PORT_HOPPING({ it.portHopping }, { f, v -> f.copy(portHopping = v) }),
    PORT_HOPPING_INTERVAL({ it.portHoppingInterval }, { f, v -> f.copy(portHoppingInterval = v) }),
    BANDWIDTH_DOWN({ it.bandwidthDown }, { f, v -> f.copy(bandwidthDown = v) }),
    BANDWIDTH_UP({ it.bandwidthUp }, { f, v -> f.copy(bandwidthUp = v) }),
    NETWORK({ it.network }, { f, v -> f.copy(network = v) }),
    HEADER_TYPE({ it.headerType }, { f, v -> f.copy(headerType = v) }),
    HOST({ it.host }, { f, v -> f.copy(host = v) }),
    PATH({ it.path }, { f, v -> f.copy(path = v) }),
    XHTTP_EXTRA({ it.xhttpExtra }, { f, v -> f.copy(xhttpExtra = v) }),
    FINAL_MASK({ it.finalMask }, { f, v -> f.copy(finalMask = v) }),
    KCP_MTU({ it.kcpMtu }, { f, v -> f.copy(kcpMtu = v) }),
    KCP_TTI({ it.kcpTti }, { f, v -> f.copy(kcpTti = v) }),
    BROWSER_DIALER({ it.browserDialerMode }, { f, v -> f.copy(browserDialerMode = v) }),
    STREAM_SECURITY({ it.streamSecurity }, { f, v -> f.copy(streamSecurity = v) }),
    SNI({ it.sni }, { f, v -> f.copy(sni = v) }),
    FINGERPRINT({ it.fingerPrint }, { f, v -> f.copy(fingerPrint = v) }),
    ALPN({ it.alpn }, { f, v -> f.copy(alpn = v) }),
    PUBLIC_KEY_REALITY({ it.publicKeyReality }, { f, v -> f.copy(publicKeyReality = v) }),
    SHORT_ID({ it.shortId }, { f, v -> f.copy(shortId = v) }),
    SPIDER_X({ it.spiderX }, { f, v -> f.copy(spiderX = v) }),
    MLDSA65_VERIFY({ it.mldsa65Verify }, { f, v -> f.copy(mldsa65Verify = v) }),
    ECH_CONFIG_LIST({ it.echConfigList }, { f, v -> f.copy(echConfigList = v) }),
    VERIFY_PEER_CERT({ it.verifyPeerCertByName }, { f, v -> f.copy(verifyPeerCertByName = v) }),
    PINNED_CA256({ it.pinnedCA256 }, { f, v -> f.copy(pinnedCA256 = v) }),
    GROUP_TYPE({ it.groupType }, { f, v -> f.copy(groupType = v) }),
    GROUP_SUB_ID({ it.groupSubId }, { f, v -> f.copy(groupSubId = v) }),
    GROUP_FILTER({ it.groupFilter }, { f, v -> f.copy(groupFilter = v) }),
    GROUP_FALLBACK_TAG({ it.groupFallbackTag }, { f, v -> f.copy(groupFallbackTag = v) }),
    MODE({ it.mode }, { f, v -> f.copy(mode = v) }),
    XHTTP_MODE({ it.xhttpMode }, { f, v -> f.copy(xhttpMode = v) }),
    SERVICE_NAME({ it.serviceName }, { f, v -> f.copy(serviceName = v) }),
    AUTHORITY({ it.authority }, { f, v -> f.copy(authority = v) }),
    SEED({ it.seed }, { f, v -> f.copy(seed = v) }),
}

enum class ServerFlag(
    val get: (ServerForm) -> Boolean,
    val set: (ServerForm, Boolean) -> ServerForm,
) {
    ALLOW_INSECURE({ it.allowInsecure }, { f, v -> f.copy(allowInsecure = v) }),
    GROUP_TEST_OUTBOUNDS({ it.groupTestOutbounds }, { f, v -> f.copy(groupTestOutbounds = v) }),
}

@Immutable
sealed interface ServerDialog {
    data object DeleteProfile : ServerDialog
    data class RemoveChainMember(val id: String) : ServerDialog
}

sealed interface ServerEvent : BaseEvent.Platform {
    data object ConfirmDeleteProfile : ServerEvent
    data class ConfirmRemoveChainMember(val id: String) : ServerEvent
}

@Immutable
data class ServerHeader(
    val configType: EConfigType = EConfigType.VMESS,
    val canDelete: Boolean = false,
)

@Immutable
data class ServerUiState(
    val configType: EConfigType = EConfigType.VMESS,
    val guid: String = "",
    val isRunning: Boolean = false,
    val form: ServerForm = ServerForm(),
    val options: ServerOptions = ServerOptions(),
    val rawContent: String = "",
    val isFetchingCert: Boolean = false,
) : BaseUiState {

    val isEdit: Boolean get() = guid.isNotEmpty()

    val canDelete: Boolean get() = isEdit && !isRunning

    val supportsObservatory: Boolean
        get() = BalancerStrategyType.from(form.groupType).supportsObservatory

    val canFetchCert: Boolean
        get() = configType == EConfigType.HYSTERIA2 || form.streamSecurity == TLS

    val header: ServerHeader
        get() = ServerHeader(configType, canDelete)
}

sealed interface ServerAction : BaseAction {
    data class TextChanged(val field: ServerField, val value: String) : ServerAction
    data class FlagChanged(val flag: ServerFlag, val value: Boolean) : ServerAction
    data class RawContentChanged(val value: String) : ServerAction

    data object Save : ServerAction
    data object Back : ServerAction
    data object DeleteClicked : ServerAction
    data object ConfirmDeleteProfile : ServerAction
    data class ConfirmRemoveChainMember(val id: String) : ServerAction
    data object FetchCertificate : ServerAction

    data object AddChainMember : ServerAction
    data class ChainMemberChanged(val id: String, val value: String) : ServerAction
    data class ChainMemberRemoveClicked(val id: String) : ServerAction
    data class ChainMemberMoved(val fromId: String, val toId: String) : ServerAction
}
