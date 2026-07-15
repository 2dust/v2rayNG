package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayNShareItem
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils

object V2rayNFmt : FmtBase() {
    fun parse(str: String): ProfileItem? {
        try {
            val jsonBase64Payload = str.substringAfterLast('/')
            val jsonPayload = Utils.decode(jsonBase64Payload)
            val v2rayNShareItem = JsonUtil.fromJson(jsonPayload, V2rayNShareItem::class.java)
            return v2rayNShareItem?.toProfileItem()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse V2rayN format", e)
        }
        return null
    }
}