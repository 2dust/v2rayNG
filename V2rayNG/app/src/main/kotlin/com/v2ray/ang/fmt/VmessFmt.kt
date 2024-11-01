package com.v2ray.ang.fmt

import android.text.TextUtils
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.dto.V2rayConfig.OutboundBean
import com.v2ray.ang.dto.VmessQRCode
import com.v2ray.ang.extension.idnHost
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils
import java.net.URI
import kotlin.text.orEmpty

object VmessFmt : FmtBase() {
    fun parse(str: String): ProfileItem? {
        if (str.indexOf('?') > 0 && str.indexOf('&') > 0) {
            return parseVmessStd(str)
        }

        var allowInsecure = MmkvManager.decodeSettingsBool(AppConfig.PREF_ALLOW_INSECURE, false)
        val config = ProfileItem.create(EConfigType.VMESS)

        var result = str.replace(EConfigType.VMESS.protocolScheme, "")
        result = Utils.decode(result)
        if (TextUtils.isEmpty(result)) {
            Log.d(AppConfig.ANG_PACKAGE, "R.string.toast_decoding_failed")
            return null
        }
        val vmessQRCode = JsonUtil.fromJson(result, VmessQRCode::class.java)
        // Although VmessQRCode fields are non null, looks like Gson may still create null fields
        if (TextUtils.isEmpty(vmessQRCode.add)
            || TextUtils.isEmpty(vmessQRCode.port)
            || TextUtils.isEmpty(vmessQRCode.id)
            || TextUtils.isEmpty(vmessQRCode.net)
        ) {
            Log.d(AppConfig.ANG_PACKAGE, "R.string.toast_incorrect_protocol")
            return null
        }

        config.remarks = vmessQRCode.ps
        config.server = vmessQRCode.add
        config.serverPort = vmessQRCode.port
        config.password = vmessQRCode.id
        config.method = if (TextUtils.isEmpty(vmessQRCode.scy)) AppConfig.DEFAULT_SECURITY else vmessQRCode.scy

        config.network = vmessQRCode.net ?: "tcp"
        config.headerType = vmessQRCode.type
        config.host = vmessQRCode.host
        config.path = vmessQRCode.path

        when (config.network) {
            "kcp" -> {
                config.seed = vmessQRCode.path
            }

            "quic" -> {
                config.quicSecurity = vmessQRCode.host
                config.quicKey = vmessQRCode.path
            }

            "grpc" -> {
                config.mode = vmessQRCode.type
                config.serviceName = vmessQRCode.path
                config.authority = vmessQRCode.host
            }
        }
        config.security = vmessQRCode.tls
        config.insecure = allowInsecure
        config.sni = vmessQRCode.sni
        config.fingerPrint = vmessQRCode.fp
        config.alpn = vmessQRCode.alpn

        return config
    }

    fun toUri(config: ProfileItem): String {
        val vmessQRCode = VmessQRCode()

        vmessQRCode.v = "2"
        vmessQRCode.ps = config.remarks
        vmessQRCode.add = config.server.orEmpty()
        vmessQRCode.port = config.serverPort.orEmpty()
        vmessQRCode.id = config.password.orEmpty()
        vmessQRCode.scy = config.method.orEmpty()
        vmessQRCode.aid = "0"

        vmessQRCode.net = config.network.orEmpty()
        vmessQRCode.type = config.headerType.orEmpty()
        when (config.network) {
            "kcp" -> {
                vmessQRCode.path = config.seed.orEmpty()
            }

            "quic" -> {
                vmessQRCode.host = config.quicSecurity.orEmpty()
                vmessQRCode.path = config.quicKey.orEmpty()
            }

            "grpc" -> {
                vmessQRCode.type = config.mode.orEmpty()
                vmessQRCode.path = config.serviceName.orEmpty()
                vmessQRCode.host = config.authority.orEmpty()
            }
        }
        config.host.let { if (it.isNotNullEmpty()) vmessQRCode.host = it.orEmpty() }
        config.path.let { if (it.isNotNullEmpty()) vmessQRCode.path = it.orEmpty() }

        vmessQRCode.tls = config.security.orEmpty()
        vmessQRCode.sni = config.sni.orEmpty()
        vmessQRCode.fp = config.fingerPrint.orEmpty()
        vmessQRCode.alpn = config.alpn.orEmpty()

        val json = JsonUtil.toJson(vmessQRCode)
        return Utils.encode(json)
    }

    fun parseVmessStd(str: String): ProfileItem? {
        var allowInsecure = MmkvManager.decodeSettingsBool(AppConfig.PREF_ALLOW_INSECURE, false)
        val config = ProfileItem.create(EConfigType.VMESS)

        val uri = URI(Utils.fixIllegalUrl(str))
        if (uri.rawQuery.isNullOrEmpty()) return null
        val queryParam = getQueryParam(uri)

        config.remarks = Utils.urlDecode(uri.fragment.orEmpty())
        config.server = uri.idnHost
        config.serverPort = uri.port.toString()
        config.password = uri.userInfo
        config.method = AppConfig.DEFAULT_SECURITY

        config.network = queryParam["type"] ?: "tcp"
        config.headerType = queryParam["headerType"]
        config.host = queryParam["host"]
        config.path = queryParam["path"]

        config.seed = queryParam["seed"]
        config.quicSecurity = queryParam["quicSecurity"]
        config.quicKey = queryParam["key"]
        config.mode = queryParam["mode"]
        config.serviceName = queryParam["serviceName"]
        config.authority = queryParam["authority"]

        config.security = queryParam["security"]
        config.insecure = if ((queryParam["allowInsecure"].orEmpty()) == "1") true else allowInsecure
        config.sni = queryParam["sni"]
        config.fingerPrint = queryParam["fp"]
        config.alpn = queryParam["alpn"]

        return config
    }


    fun toOutbound(profileItem: ProfileItem): OutboundBean? {
        val outboundBean = OutboundBean.create(EConfigType.VMESS)

        outboundBean?.settings?.vnext?.get(0)?.let { vnext ->
            vnext.address = profileItem.server.orEmpty()
            vnext.port = profileItem.serverPort.orEmpty().toInt()
            vnext.users[0].id = profileItem.password.orEmpty()
            vnext.users[0].security = profileItem.method
        }

        outboundBean?.streamSettings?.populateTransportSettings(
            profileItem.network.orEmpty(),
            profileItem.headerType,
            profileItem.host,
            profileItem.path,
            profileItem.seed,
            profileItem.quicSecurity,
            profileItem.quicKey,
            profileItem.mode,
            profileItem.serviceName,
            profileItem.authority,
        )

        outboundBean?.streamSettings?.populateTlsSettings(
            profileItem.security.orEmpty(),
            profileItem.insecure == true,
            profileItem.sni,
            profileItem.fingerPrint,
            profileItem.alpn,
            null,
            null,
            null
        )

        return outboundBean
    }

}