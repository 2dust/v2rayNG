package com.v2ray.ang.fmt

import com.google.gson.annotations.SerializedName
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils


private const val OUTBOUND_PROFILE_WIRE_CONFIG_TYPE = 13
private const val OUTBOUND_PROFILE_WIRE_CORE_TYPE_XRAY = 2
private const val OUTBOUND_PROFILE_WIRE_CONFIG_VERSION = 4

private data class OutboundProfile(
    @SerializedName("IndexId") val indexId: String = Utils.getUuid(),
    @SerializedName("ConfigType") val configType: Int = OUTBOUND_PROFILE_WIRE_CONFIG_TYPE,
    @SerializedName("CoreType") val coreType: Int = OUTBOUND_PROFILE_WIRE_CORE_TYPE_XRAY,
    @SerializedName("ConfigVersion") val configVersion: Int = OUTBOUND_PROFILE_WIRE_CONFIG_VERSION,
    @SerializedName("Remarks") val remarks: String? = null,
    @SerializedName("CustomOutboundObj") val customOutboundObj: V2rayConfig.OutboundBean,
) {
    fun isValid(): Boolean {
        return coreType == OUTBOUND_PROFILE_WIRE_CORE_TYPE_XRAY &&
            configType == OUTBOUND_PROFILE_WIRE_CONFIG_TYPE &&
            configVersion == OUTBOUND_PROFILE_WIRE_CONFIG_VERSION
    }
}

object CustomOutboundFmt : FmtBase() {
    fun parse(str: String): Pair<ProfileItem, String> {
        val config = ProfileItem.create(EConfigType.CUSTOM_OUTBOUND)

        val outbound = JsonUtil.fromJson(str, V2rayConfig.OutboundBean::class.java)

        config.remarks = outbound?.tag ?: System.currentTimeMillis().toString()
        config.server = outbound?.getServerAddress()
        config.serverPort = outbound?.getServerPort()?.toString()

        outbound?.tag = AppConfig.TAG_PROXY
        val rawOutbound = outbound?.let { JsonUtil.toJsonPretty(it) } ?: ""
        return Pair(config, rawOutbound)
    }

    fun toUri(profile: ProfileItem, raw: String): String {
        val outbound = JsonUtil.fromJson(raw, V2rayConfig.OutboundBean::class.java) ?: return ""
        val op = OutboundProfile(remarks = profile.remarks, customOutboundObj = outbound)
        val payload = Utils.encode(
            JsonUtil.toJson(
                op,
            )
        )
        return "outbound/$payload"
    }

    fun parseUri(str: String): Pair<ProfileItem, String>? {
        val strippedStr = str.replace("${AppConfig.V2RAYNFMTS}outbound/", "", ignoreCase = true)
        val op = JsonUtil.fromJson(
            Utils.decode(strippedStr), OutboundProfile::class.java
        )?.takeIf { it.isValid() }
            ?: return null
        val config = ProfileItem.create(EConfigType.CUSTOM_OUTBOUND)
        config.remarks = op.remarks ?: op.customOutboundObj.tag
        config.server = op.customOutboundObj.getServerAddress()
        config.serverPort = op.customOutboundObj.getServerPort()?.toString()
        val rawOutbound = JsonUtil.toJsonPretty(op.customOutboundObj) ?: return null
        return Pair(config, rawOutbound)
    }
}
