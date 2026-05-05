package com.v2ray.ang.fmt

import com.v2ray.ang.extension.idnHost
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.Utils
import java.net.URI

object AnytlsFmt : FmtBase() {
    private val infoRemarkKeywords = listOf(
        "剩余流量",
        "距离下次重置",
        "套餐到期",
        "到期时间",
        "流量重置",
        "expire",
        "expiration",
        "remaining",
        "traffic",
        "reset",
    )

    fun parse(str: String): RawProfileImport? {
        val allowInsecure = runCatching {
            MmkvManager.decodeSettingsBool(AppConfig.PREF_ALLOW_INSECURE, false)
        }.getOrDefault(false)
        val uri = URI(Utils.fixIllegalUrl(str))
        val remarks = Utils.decodeURIComponent(uri.fragment.orEmpty()).let { it.ifEmpty { "none" } }
        if (isSubscriptionInfoRemark(remarks)) {
            return null
        }

        val queryParam = if (uri.rawQuery.isNullOrEmpty()) emptyMap() else getQueryParam(uri)
        val insecure = when {
            queryParam["insecure"] == "1" -> true
            queryParam["insecure"] == "0" -> false
            else -> allowInsecure
        }
        val alpn = queryParam["alpn"]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val fingerPrint = queryParam["fp"] ?: queryParam["client-fingerprint"]

        val outbound = linkedMapOf<String, Any?>(
            "type" to "anytls",
            "tag" to AppConfig.TAG_PROXY,
            "server" to uri.idnHost,
            "server_port" to uri.port,
            "password" to uri.userInfo,
            "tls" to linkedMapOf<String, Any?>(
                "enabled" to true,
                "server_name" to (queryParam["sni"] ?: queryParam["servername"]),
                "insecure" to insecure,
            ).apply {
                if (alpn.isNotEmpty()) {
                    this["alpn"] = alpn
                }
                fingerPrint?.let {
                    this["utls"] = linkedMapOf(
                        "enabled" to true,
                        "fingerprint" to it,
                    )
                }
            }
        )

        val rawConfig = RawProfileFmt.createRawConfig(outbound) ?: return null
        val profile = RawProfileFmt.createProfile(
            name = remarks,
            server = uri.idnHost,
            serverPort = uri.port,
            password = uri.userInfo,
            sni = queryParam["sni"] ?: queryParam["servername"],
            alpn = alpn,
            fingerPrint = fingerPrint,
            insecure = insecure,
        )
        return RawProfileImport(profile = profile, rawConfig = rawConfig)
    }

    private fun isSubscriptionInfoRemark(remarks: String): Boolean {
        val normalized = remarks.trim().lowercase()
        return infoRemarkKeywords.any { keyword -> normalized.contains(keyword) }
    }
}
