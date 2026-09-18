package com.v2ray.ang.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.JsonUtil

/** Converts supported v2rayNG profiles into a minimal sing-box configuration. */
object SingBoxConfigConverter {
    fun convert(profile: ProfileItem, rawCustomJson: String? = null): String {
        if (profile.configType == EConfigType.CUSTOM && !rawCustomJson.isNullOrBlank()) {
            return normalizeCustomConfig(rawCustomJson)
        }

        val outbound = when (profile.configType) {
            EConfigType.HYSTERIA2 -> hysteria2(profile)
            EConfigType.WIREGUARD -> wireguard(profile)
            else -> error("sing-box backend does not yet map ${profile.configType}")
        }

        return JsonUtil.toJson(JsonObject().apply {
            add("log", JsonObject().apply { addProperty("level", "warn") })
            add("dns", JsonObject().apply {
                add("servers", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("tag", "local")
                        addProperty("address", "local")
                    })
                })
            })
            add("inbounds", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("type", "tun")
                    addProperty("tag", "tun-in")
                    addProperty("interface_name", "v2rayng-tun")
                    addProperty("address", "172.19.0.1/30")
                    addProperty("auto_route", false)
                })
            })
            add("outbounds", JsonArray().apply {
                add(outbound)
                add(JsonObject().apply {
                    addProperty("type", "direct")
                    addProperty("tag", "direct")
                })
            })
            add("route", JsonObject().apply {
                addProperty("auto_detect_interface", true)
                addProperty("final", "proxy")
            })
        })
    }

    private fun hysteria2(profile: ProfileItem) = JsonObject().apply {
        addProperty("type", "hysteria2")
        addProperty("tag", "proxy")
        addProperty("server", profile.server.orEmpty())
        addProperty("server_port", profile.serverPort?.toIntOrNull() ?: 443)
        addProperty("password", profile.password.orEmpty())
        profile.sni?.takeIf { it.isNotBlank() }?.let { addProperty("tls", JsonObject().apply {
            addProperty("enabled", true)
            addProperty("server_name", it)
            addProperty("insecure", profile.insecure == true)
        }) }
    }

    private fun wireguard(profile: ProfileItem) = JsonObject().apply {
        addProperty("type", "wireguard")
        addProperty("tag", "proxy")
        addProperty("server", profile.server.orEmpty())
        addProperty("server_port", profile.serverPort?.toIntOrNull() ?: 51820)
        addProperty("private_key", profile.secretKey.orEmpty())
        add("local_address", JsonArray().apply {
            profile.localAddress.orEmpty().split(',').map(String::trim)
                .filter(String::isNotEmpty).forEach(::add)
        })
        add("peers", JsonArray().apply {
            add(JsonObject().apply {
                addProperty("public_key", profile.publicKey.orEmpty())
                profile.preSharedKey?.takeIf { it.isNotBlank() }?.let { addProperty("pre_shared_key", it) }
                addProperty("server", profile.server.orEmpty())
                addProperty("server_port", profile.serverPort?.toIntOrNull() ?: 51820)
            })
        })
    }

    private fun normalizeCustomConfig(raw: String): String {
        val root = JsonUtil.parseString(raw)
            ?: error("Invalid custom JSON")
        return root.toString()
    }
}
