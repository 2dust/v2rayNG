package com.v2ray.ang.util.fmt

import android.text.TextUtils
import android.util.Log
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ServerConfig
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.dto.VmessQRCode
import com.v2ray.ang.extension.idnHost
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.Utils
import java.net.URI

object VmessFmt {
    private val settingsStorage by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_SETTING, MMKV.MULTI_PROCESS_MODE
        )
    }

    fun parseVmess(str: String): ServerConfig? {
        val allowInsecure = settingsStorage?.decodeBool(AppConfig.PREF_ALLOW_INSECURE) ?: false
        val config = ServerConfig.create(EConfigType.VMESS)
        val streamSetting = config.outboundBean?.streamSettings ?: return null

        if (!tryParseNewVmess(str, config, allowInsecure)) {
            if (str.indexOf("?") > 0) {
                if (!tryResolveVmess4Kitsunebi(str, config)) {
                    Log.d(AppConfig.ANG_PACKAGE, "R.string.toast_incorrect_protocol")
                    return null
                }
            } else {
                var result = str.replace(EConfigType.VMESS.protocolScheme, "")
                result = Utils.decode(result)
                if (TextUtils.isEmpty(result)) {
                    Log.d(AppConfig.ANG_PACKAGE, "R.string.toast_decoding_failed")
                    return null
                }
                val vmessQRCode = Gson().fromJson(result, VmessQRCode::class.java)
                // Although VmessQRCode fields are non null, looks like Gson may still create null fields
                if (TextUtils.isEmpty(vmessQRCode.add) || TextUtils.isEmpty(vmessQRCode.port) || TextUtils.isEmpty(
                        vmessQRCode.id
                    ) || TextUtils.isEmpty(vmessQRCode.net)
                ) {
                    Log.d(AppConfig.ANG_PACKAGE, "R.string.toast_incorrect_protocol")
                    return null
                }

                config.remarks = vmessQRCode.ps
                config.outboundBean?.settings?.vnext?.get(0)?.let { vnext ->
                    vnext.address = vmessQRCode.add
                    vnext.port = Utils.parseInt(vmessQRCode.port)
                    vnext.users[0].id = vmessQRCode.id
                    vnext.users[0].security =
                        if (TextUtils.isEmpty(vmessQRCode.scy)) V2rayConfig.DEFAULT_SECURITY else vmessQRCode.scy
                    vnext.users[0].alterId = Utils.parseInt(vmessQRCode.aid)
                }
                val sni = streamSetting.populateTransportSettings(
                    vmessQRCode.net,
                    vmessQRCode.type,
                    vmessQRCode.host,
                    vmessQRCode.path,
                    vmessQRCode.path,
                    vmessQRCode.host,
                    vmessQRCode.path,
                    vmessQRCode.type,
                    vmessQRCode.path,
                    vmessQRCode.host
                )

                val fingerprint = vmessQRCode.fp ?: streamSetting.tlsSettings?.fingerprint
                streamSetting.populateTlsSettings(
                    vmessQRCode.tls,
                    allowInsecure,
                    if (TextUtils.isEmpty(vmessQRCode.sni)) sni else vmessQRCode.sni,
                    fingerprint,
                    vmessQRCode.alpn,
                    null,
                    null,
                    null
                )
            }
        }
        return config
    }

    fun toUri(config: ServerConfig): String {
        val outbound = config.getProxyOutbound() ?: return ""
        val streamSetting = outbound.streamSettings ?: V2rayConfig.OutboundBean.StreamSettingsBean()

        val vmessQRCode = VmessQRCode()
        vmessQRCode.v = "2"
        vmessQRCode.ps = config.remarks
        vmessQRCode.add = outbound.getServerAddress().orEmpty()
        vmessQRCode.port = outbound.getServerPort().toString()
        vmessQRCode.id = outbound.getPassword().orEmpty()
        vmessQRCode.aid = outbound.settings?.vnext?.get(0)?.users?.get(0)?.alterId.toString()
        vmessQRCode.scy = outbound.settings?.vnext?.get(0)?.users?.get(0)?.security.toString()
        vmessQRCode.net = streamSetting.network
        vmessQRCode.tls = streamSetting.security
        vmessQRCode.sni = streamSetting.tlsSettings?.serverName.orEmpty()
        vmessQRCode.alpn =
            Utils.removeWhiteSpace(streamSetting.tlsSettings?.alpn?.joinToString()).orEmpty()
        vmessQRCode.fp = streamSetting.tlsSettings?.fingerprint.orEmpty()
        outbound.getTransportSettingDetails()?.let { transportDetails ->
            vmessQRCode.type = transportDetails[0]
            vmessQRCode.host = transportDetails[1]
            vmessQRCode.path = transportDetails[2]
        }
        val json = Gson().toJson(vmessQRCode)
        return Utils.encode(json)
    }

    private fun tryParseNewVmess(
        uriString: String, config: ServerConfig, allowInsecure: Boolean
    ): Boolean {
        return runCatching {
            val uri = URI(Utils.fixIllegalUrl(uriString))
            check(uri.scheme == "vmess")
            val (_, protocol, tlsStr, uuid, alterId) = Regex("(tcp|http|ws|kcp|quic|grpc)(\\+tls)?:([0-9a-z]{8}-[0-9a-z]{4}-[0-9a-z]{4}-[0-9a-z]{4}-[0-9a-z]{12})").matchEntire(
                    uri.userInfo
                )?.groupValues ?: error("parse user info fail.")
            val tls = tlsStr.isNotBlank()
            val queryParam = uri.rawQuery.split("&")
                .associate { it.split("=").let { (k, v) -> k to Utils.urlDecode(v) } }

            val streamSetting = config.outboundBean?.streamSettings ?: return false
            config.remarks = Utils.urlDecode(uri.fragment ?: "")
            config.outboundBean.settings?.vnext?.get(0)?.let { vnext ->
                vnext.address = uri.idnHost
                vnext.port = uri.port
                vnext.users[0].id = uuid
                vnext.users[0].security = V2rayConfig.DEFAULT_SECURITY
                vnext.users[0].alterId = alterId.toInt()
            }
            var fingerprint = streamSetting.tlsSettings?.fingerprint
            val sni = streamSetting.populateTransportSettings(protocol,
                queryParam["type"],
                queryParam["host"]?.split("|")?.get(0) ?: "",
                queryParam["path"]?.takeIf { it.trim() != "/" } ?: "",
                queryParam["seed"],
                queryParam["security"],
                queryParam["key"],
                queryParam["mode"],
                queryParam["serviceName"],
                queryParam["authority"])
            streamSetting.populateTlsSettings(
                if (tls) V2rayConfig.TLS else "",
                allowInsecure,
                sni,
                fingerprint,
                null,
                null,
                null,
                null
            )
            true
        }.getOrElse { false }
    }

    private fun tryResolveVmess4Kitsunebi(server: String, config: ServerConfig): Boolean {

        var result = server.replace(EConfigType.VMESS.protocolScheme, "")
        val indexSplit = result.indexOf("?")
        if (indexSplit > 0) {
            result = result.substring(0, indexSplit)
        }
        result = Utils.decode(result)

        val arr1 = result.split('@')
        if (arr1.count() != 2) {
            return false
        }
        val arr21 = arr1[0].split(':')
        val arr22 = arr1[1].split(':')
        if (arr21.count() != 2) {
            return false
        }

        config.remarks = "Alien"
        config.outboundBean?.settings?.vnext?.get(0)?.let { vnext ->
            vnext.address = arr22[0]
            vnext.port = Utils.parseInt(arr22[1])
            vnext.users[0].id = arr21[1]
            vnext.users[0].security = arr21[0]
            vnext.users[0].alterId = 0
        }
        return true
    }
}