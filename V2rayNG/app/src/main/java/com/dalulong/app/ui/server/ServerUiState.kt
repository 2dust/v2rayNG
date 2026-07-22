package com.dalulong.app.ui.server

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import com.dalulong.app.AppConfig.DEFAULT_PORT
import com.dalulong.app.AppConfig.REALITY
import com.dalulong.app.AppConfig.WIREGUARD_LOCAL_ADDRESS_V4
import com.dalulong.app.AppConfig.WIREGUARD_LOCAL_MTU
import com.dalulong.app.dto.entities.ProfileItem
import com.dalulong.app.enums.EConfigType
import com.dalulong.app.enums.NetworkType
import com.dalulong.app.extension.nullIfBlank
import com.dalulong.app.util.JsonUtil

class ServerUiState(
    configType: EConfigType,
    remarks: String = "",
    address: String = "",
    port: String = DEFAULT_PORT.toString(),
    password: String = "",
    method: String = "",
    flow: String = "",
    encryption: String = "",
    username: String = "",
    secretKey: String = "",
    publicKey: String = "",
    preSharedKey: String = "",
    reserved: String = "0,0,0",
    localAddress: String = WIREGUARD_LOCAL_ADDRESS_V4,
    mtu: String = WIREGUARD_LOCAL_MTU,
    obfsPassword: String = "",
    portHopping: String = "",
    portHoppingInterval: String = "",
    bandwidthDown: String = "",
    bandwidthUp: String = "",
    network: String = NetworkType.TCP.type,
    headerType: String = "none",
    host: String = "",
    path: String = "",
    xhttpExtra: String = "",
    finalMask: String = "",
    kcpMtu: String = "",
    kcpTti: String = "",
    browserDialerMode: String = "Disable",
    streamSecurity: String = "",
    sni: String = "",
    allowInsecure: Boolean = false,
    fingerPrint: String = "",
    alpn: String = "",
    publicKeyReality: String = "",
    shortId: String = "",
    spiderX: String = "",
    mldsa65Verify: String = "",
    echConfigList: String = "",
    verifyPeerCertByName: String = "",
    pinnedCA256: String = "",
    isFetchingCert: Boolean = false
) {
    var configType by mutableStateOf(configType)
    var remarks by mutableStateOf(remarks)
    var address by mutableStateOf(address)
    var port by mutableStateOf(port)
    var password by mutableStateOf(password)
    var method by mutableStateOf(method)
    var flow by mutableStateOf(flow)
    var encryption by mutableStateOf(encryption)
    var username by mutableStateOf(username)
    var secretKey by mutableStateOf(secretKey)
    var publicKey by mutableStateOf(publicKey)
    var preSharedKey by mutableStateOf(preSharedKey)
    var reserved by mutableStateOf(reserved)
    var localAddress by mutableStateOf(localAddress)
    var mtu by mutableStateOf(mtu)
    var obfsPassword by mutableStateOf(obfsPassword)
    var portHopping by mutableStateOf(portHopping)
    var portHoppingInterval by mutableStateOf(portHoppingInterval)
    var bandwidthDown by mutableStateOf(bandwidthDown)
    var bandwidthUp by mutableStateOf(bandwidthUp)
    var network by mutableStateOf(network)
    var headerType by mutableStateOf(headerType)
    var host by mutableStateOf(host)
    var path by mutableStateOf(path)
    var xhttpExtra by mutableStateOf(xhttpExtra)
    var finalMask by mutableStateOf(finalMask)
    var kcpMtu by mutableStateOf(kcpMtu)
    var kcpTti by mutableStateOf(kcpTti)
    var browserDialerMode by mutableStateOf(browserDialerMode)
    var streamSecurity by mutableStateOf(streamSecurity)
    var sni by mutableStateOf(sni)
    var allowInsecure by mutableStateOf(allowInsecure)
    var fingerPrint by mutableStateOf(fingerPrint)
    var alpn by mutableStateOf(alpn)
    var publicKeyReality by mutableStateOf(publicKeyReality)
    var shortId by mutableStateOf(shortId)
    var spiderX by mutableStateOf(spiderX)
    var mldsa65Verify by mutableStateOf(mldsa65Verify)
    var echConfigList by mutableStateOf(echConfigList)
    var verifyPeerCertByName by mutableStateOf(verifyPeerCertByName)
    var pinnedCA256 by mutableStateOf(pinnedCA256)
    var isFetchingCert by mutableStateOf(isFetchingCert)

    fun toProfileItem(initialConfig: ProfileItem): ProfileItem {
        val isVmess = configType == EConfigType.VMESS
        val isVless = configType == EConfigType.VLESS
        val isShadowsocks = configType == EConfigType.SHADOWSOCKS
        val isSocksOrHttp = configType == EConfigType.SOCKS || configType == EConfigType.HTTP
        val isWireguard = configType == EConfigType.WIREGUARD
        val isHysteria2 = configType == EConfigType.HYSTERIA2

        return initialConfig.copy(
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
            host = host,
            path = path,
            xhttpExtra = xhttpExtra.nullIfBlank(),
            finalMask = finalMask.nullIfBlank(),
            kcpMtu = kcpMtu.toIntOrNull(),
            kcpTti = kcpTti.toIntOrNull(),
            browserDialerMode = if (network in listOf(NetworkType.WS.type, NetworkType.XHTTP.type)) {
                browserDialerMode.nullIfBlank()
            } else {
                null
            },
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
    }

    companion object {
        fun fromProfileItem(
            initialConfig: ProfileItem,
            browserDialerDefault: String
        ): ServerUiState =
            ServerUiState(
                configType = initialConfig.configType,
                remarks = initialConfig.remarks,
                address = initialConfig.server ?: "",
                port = initialConfig.serverPort ?: DEFAULT_PORT.toString(),
                password = initialConfig.password ?: "",
                method = initialConfig.method ?: "",
                flow = initialConfig.flow ?: "",
                encryption = initialConfig.method ?: "",
                username = initialConfig.username ?: "",
                secretKey = initialConfig.secretKey ?: "",
                publicKey = initialConfig.publicKey ?: "",
                preSharedKey = initialConfig.preSharedKey ?: "",
                reserved = initialConfig.reserved ?: "0,0,0",
                localAddress = initialConfig.localAddress ?: WIREGUARD_LOCAL_ADDRESS_V4,
                mtu = initialConfig.mtu?.toString() ?: WIREGUARD_LOCAL_MTU,
                obfsPassword = initialConfig.obfsPassword ?: "",
                portHopping = initialConfig.portHopping ?: "",
                portHoppingInterval = initialConfig.portHoppingInterval ?: "",
                bandwidthDown = initialConfig.bandwidthDown ?: "",
                bandwidthUp = initialConfig.bandwidthUp ?: "",
                network = initialConfig.network ?: NetworkType.TCP.type,
                headerType = initialConfig.headerType ?: "none",
                host = initialConfig.host ?: "",
                path = initialConfig.path ?: "",
                xhttpExtra = initialConfig.xhttpExtra ?: "",
                finalMask = initialConfig.finalMask ?: "",
                kcpMtu = initialConfig.kcpMtu?.toString() ?: "",
                kcpTti = initialConfig.kcpTti?.toString() ?: "",
                browserDialerMode = initialConfig.browserDialerMode ?: browserDialerDefault,
                streamSecurity = initialConfig.security ?: "",
                sni = initialConfig.sni ?: "",
                allowInsecure = initialConfig.insecure == true,
                fingerPrint = initialConfig.fingerPrint ?: "",
                alpn = initialConfig.alpn ?: "",
                publicKeyReality = initialConfig.publicKey ?: "",
                shortId = initialConfig.shortId ?: "",
                spiderX = initialConfig.spiderX ?: "",
                mldsa65Verify = initialConfig.mldsa65Verify ?: "",
                echConfigList = initialConfig.echConfigList ?: "",
                verifyPeerCertByName = initialConfig.verifyPeerCertByName ?: "",
                pinnedCA256 = initialConfig.pinnedCA256 ?: ""
            )

        fun from(
            initialConfig: ProfileItem,
            browserDialerDefault: String
        ): ServerUiState = fromProfileItem(initialConfig, browserDialerDefault)

        val Saver: Saver<ServerUiState, String> = Saver(
            save = { JsonUtil.toJson(it.toProfileItem(ProfileItem.create(it.configType))) },
            restore = { saved ->
                JsonUtil.fromJsonSafe(saved, ProfileItem::class.java)?.let {
                    fromProfileItem(it, browserDialerDefault = "Disable")
                }
            }
        )
    }
}
