package com.v2ray.ang.util.fmt

import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ServerConfig
import com.v2ray.ang.extension.idnHost
import com.v2ray.ang.util.Utils
import java.net.URI

object ShadowsocksFmt {
    fun parseShadowsocks(str: String): ServerConfig? {
        val config = ServerConfig.create(EConfigType.SHADOWSOCKS)
        if (!tryResolveResolveSip002(str, config)) {
            var result = str.replace(EConfigType.SHADOWSOCKS.protocolScheme, "")
            val indexSplit = result.indexOf("#")
            if (indexSplit > 0) {
                try {
                    config.remarks =
                        Utils.urlDecode(result.substring(indexSplit + 1, result.length))
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                result = result.substring(0, indexSplit)
            }

            //part decode
            val indexS = result.indexOf("@")
            result = if (indexS > 0) {
                Utils.decode(result.substring(0, indexS)) + result.substring(
                    indexS,
                    result.length
                )
            } else {
                Utils.decode(result)
            }

            val legacyPattern = "^(.+?):(.*)@(.+?):(\\d+?)/?$".toRegex()
            val match = legacyPattern.matchEntire(result)
                ?: return null

            config.outboundBean?.settings?.servers?.get(0)?.let { server ->
                server.address = match.groupValues[3].removeSurrounding("[", "]")
                server.port = match.groupValues[4].toInt()
                server.password = match.groupValues[2]
                server.method = match.groupValues[1].lowercase()
            }
        }
        return config
    }

    fun toUri(config: ServerConfig): String {
        val outbound = config.getProxyOutbound() ?: return ""
        val remark = "#" + Utils.urlEncode(config.remarks)
        val pw =
            Utils.encode("${outbound.getSecurityEncryption()}:${outbound.getPassword()}")
        val url = String.format(
            "%s@%s:%s",
            pw,
            Utils.getIpv6Address(outbound.getServerAddress()),
            outbound.getServerPort()
        )
        return url + remark
    }

    private fun tryResolveResolveSip002(str: String, config: ServerConfig): Boolean {
        try {
            val uri = URI(Utils.fixIllegalUrl(str))
            config.remarks = Utils.urlDecode(uri.fragment ?: "")

            val method: String
            val password: String
            if (uri.userInfo.contains(":")) {
                val arrUserInfo = uri.userInfo.split(":").map { it.trim() }
                if (arrUserInfo.count() != 2) {
                    return false
                }
                method = arrUserInfo[0]
                password = Utils.urlDecode(arrUserInfo[1])
            } else {
                val base64Decode = Utils.decode(uri.userInfo)
                val arrUserInfo = base64Decode.split(":").map { it.trim() }
                if (arrUserInfo.count() < 2) {
                    return false
                }
                method = arrUserInfo[0]
                password = base64Decode.substringAfter(":")
            }

            val query = Utils.urlDecode(uri.query ?: "")
            if (query != "") {
                val queryPairs = HashMap<String, String>()
                val pairs = query.split(";")
                Log.d(AppConfig.ANG_PACKAGE, pairs.toString())
                for (pair in pairs) {
                    val idx = pair.indexOf("=")
                    if (idx == -1) {
                        queryPairs[Utils.urlDecode(pair)] = ""
                    } else {
                        queryPairs[Utils.urlDecode(pair.substring(0, idx))] =
                            Utils.urlDecode(pair.substring(idx + 1))
                    }
                }
                Log.d(AppConfig.ANG_PACKAGE, queryPairs.toString())
                var sni: String? = ""
                if (queryPairs["plugin"] == "obfs-local" && queryPairs["obfs"] == "http") {
                    sni = config.outboundBean?.streamSettings?.populateTransportSettings(
                        "tcp",
                        "http",
                        queryPairs["obfs-host"],
                        queryPairs["path"],
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                    )
                } else if (queryPairs["plugin"] == "v2ray-plugin") {
                    var network = "ws"
                    if (queryPairs["mode"] == "quic") {
                        network = "quic"
                    }
                    sni = config.outboundBean?.streamSettings?.populateTransportSettings(
                        network,
                        null,
                        queryPairs["host"],
                        queryPairs["path"],
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                    )
                }
                if ("tls" in queryPairs) {
                    config.outboundBean?.streamSettings?.populateTlsSettings(
                        "tls", false, sni ?: "", null, null, null, null, null
                    )
                }

            }

            config.outboundBean?.settings?.servers?.get(0)?.let { server ->
                server.address = uri.idnHost
                server.port = uri.port
                server.password = password
                server.method = method
            }
            return true
        } catch (e: Exception) {
            Log.d(AppConfig.ANG_PACKAGE, e.toString())
            return false
        }
    }

}