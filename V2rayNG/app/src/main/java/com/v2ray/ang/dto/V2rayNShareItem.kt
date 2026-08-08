package com.v2ray.ang.dto

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.BalancerStrategyType
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.NetworkType

@Suppress("PropertyName")
data class V2rayNShareItem(
    // val IndexId: String?,
    val ConfigType: Int?,
    // val CoreType: Int?,
    val ConfigVersion: Int?,
    val Subid: String?,
    val IsSub: Boolean?,
    val PreSocksPort: Int?,
    // val DisplayLog: Boolean?,
    val Remarks: String?,
    val Address: String?,
    val Port: Int?,
    val Password: String?,
    val Username: String?,
    val Network: String?,
    val StreamSecurity: String?,
    val AllowInsecure: String?,
    val Sni: String?,
    val Alpn: String?,
    val Fingerprint: String?,
    val CipherSuites: String?,
    val PublicKey: String?,
    val ShortId: String?,
    val SpiderX: String?,
    val Mldsa65Verify: String?,
    // val MuxEnabled: Boolean?,
    // val Cert: String?,
    val CertSha: String?,
    val EchConfigList: String?,
    val VerifyPeerCertByName: String?,
    val Finalmask: String?,
    val ProtoExtraObj: V2rayNProtocolExtraShareItem?,
    val TransportExtraObj: V2rayNTransportExtraShareItem?,
) {
    data class V2rayNProtocolExtraShareItem(
        val AlterId: Int?,
        val VmessSecurity: String?,
        val Flow: String?,
        val VlessEncryption: String?,
        val SsMethod: String?,
        val WgPublicKey: String?,
        val WgPresharedKey: String?,
        val WgInterfaceAddress: String?,
        val WgReserved: String?,
        val WgMtu: Int?,
        val SalamanderPass: String?,
        val UpMbps: Int?,
        val DownMbps: Int?,
        val Ports: String?,
        val HopInterval: String?,
        val GroupType: String?,
        val ChildItems: String?,
        val SubChildItems: String?,
        val Filter: String?,
        val MultipleLoad: Int?,
    )

    data class V2rayNTransportExtraShareItem(
        val RawHeaderType: String?,
        val Host: String?,
        val Path: String?,
        val XhttpMode: String?,
        val XhttpExtra: String?,
        val GrpcAuthority: String?,
        val GrpcServiceName: String?,
        val GrpcMode: String?,
        val KcpHeaderType: String?,
        val KcpSeed: String?,
        val KcpMtu: Int?,
    )

    fun toProfileItem(): ProfileItem {
        val configType = when (ConfigType) {
            1 -> EConfigType.VMESS
            2 -> EConfigType.CUSTOM
            3 -> EConfigType.SHADOWSOCKS
            4 -> EConfigType.SOCKS
            5 -> EConfigType.VLESS
            6 -> EConfigType.TROJAN
            7 -> EConfigType.HYSTERIA2
            // 8 -> EConfigType.TUIC
            9 -> EConfigType.WIREGUARD
            10 -> EConfigType.HTTP
            101 -> EConfigType.POLICYGROUP
            102 -> EConfigType.PROXYCHAIN
            else -> error("Unknown ConfigType: $ConfigType")
        }
        val network = if (configType == EConfigType.HYSTERIA2) "hysteria" else
            when (Network) {
                "raw" -> "tcp"
                else -> NetworkType.fromString(Network).type
            }
        val profile = ProfileItem(
            configType = configType,
            remarks = Remarks.orEmpty(),
            server = Address.orEmpty(),
            serverPort = Port.toString(),
            password = Password.orEmpty(),
            method = when (configType) {
                EConfigType.VMESS -> ProtoExtraObj?.VmessSecurity.orEmpty()
                EConfigType.SHADOWSOCKS -> ProtoExtraObj?.SsMethod.orEmpty()
                EConfigType.VLESS -> ProtoExtraObj?.VlessEncryption.orEmpty()
                else -> null
            },
            flow = ProtoExtraObj?.Flow.orEmpty(),
            username = Username.orEmpty(),
            network = network,
            headerType = when (network) {
                NetworkType.TCP.type -> TransportExtraObj?.RawHeaderType
                NetworkType.KCP.type -> TransportExtraObj?.KcpHeaderType
                else -> null
            },
            host = TransportExtraObj?.Host,
            path = TransportExtraObj?.Path,
            seed = TransportExtraObj?.KcpSeed,
            kcpMtu = TransportExtraObj?.KcpMtu,
            mode = TransportExtraObj?.GrpcMode,
            serviceName = TransportExtraObj?.GrpcServiceName,
            authority = TransportExtraObj?.GrpcAuthority,
            xhttpMode = TransportExtraObj?.XhttpMode,
            xhttpExtra = TransportExtraObj?.XhttpExtra,
            finalMask = Finalmask,
            security = StreamSecurity,
            sni = Sni,
            alpn = Alpn,
            fingerPrint = Fingerprint,
            cipherSuites = CipherSuites,
            insecure = AllowInsecure?.toBoolean(),
            echConfigList = EchConfigList,
            verifyPeerCertByName = VerifyPeerCertByName,
            pinnedCA256 = CertSha,
            publicKey = PublicKey,
            shortId = ShortId,
            spiderX = SpiderX,
            mldsa65Verify = Mldsa65Verify,
            secretKey = if (configType == EConfigType.WIREGUARD) Password else null,
            preSharedKey = ProtoExtraObj?.WgPresharedKey,
            localAddress = ProtoExtraObj?.WgInterfaceAddress,
            reserved = ProtoExtraObj?.WgReserved,
            mtu = ProtoExtraObj?.WgMtu,
            obfsPassword = ProtoExtraObj?.SalamanderPass,
            portHopping = ProtoExtraObj?.Ports,
            portHoppingInterval = ProtoExtraObj?.HopInterval,
            bandwidthDown = ProtoExtraObj?.DownMbps?.takeIf { it > 0 }?.let { "${it}Mbps" },
            bandwidthUp = ProtoExtraObj?.UpMbps?.takeIf { it > 0 }?.let { "${it}Mbps" },
            policyGroupType = when (ProtoExtraObj?.MultipleLoad) {
                2 -> BalancerStrategyType.RANDOM.policyGroupType
                3 -> BalancerStrategyType.ROUND_ROBIN.policyGroupType
                4 -> BalancerStrategyType.LEAST_LOAD.policyGroupType
                else -> BalancerStrategyType.LEAST_PING.policyGroupType
            },
            // NOTE: not safe, suggest converting and rewriting
            // policyGroupSubscriptionId = ProtoExtraObj?.SubChildItems,
            policyGroupSubscriptionId = if (ProtoExtraObj?.SubChildItems == "self") "self" else null,
            policyGroupFilter = ProtoExtraObj?.Filter,
            // NOTE: proxyChainProfiles stores remarks, not IndexId
            // proxyChainProfiles = ProtoExtraObj?.ChildItems,
        )
        // profile.description =
        return profile
    }
}
