package com.v2ray.ang.dto

import com.google.gson.Gson
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Unit tests for V2rayNShareItem, the profile JSON that v2rayN and PattN share. */
class V2rayNShareItemTest {

    @Test
    fun toProfileItem_readsTheEchOutboundThatPattNShares() {
        // PattN shares ProfileItem.EchOutbound under this name, next to EchConfigList.
        val echOutbound = """{"tag": "ech-out", "protocol": "freedom"}"""
        val item = Gson().fromJson(
            """
            {
              "ConfigType": 5, "Address": "example.com", "Port": 443, "Network": "raw", "StreamSecurity": "tls",
              "EchConfigList": "cloudflare-ech.com+https://1.1.1.1/dns-query",
              "EchOutbound": ${Gson().toJson(echOutbound)}
            }
            """,
            V2rayNShareItem::class.java,
        )

        val profile = item.toProfileItem()

        assertEquals(EConfigType.VLESS, profile.configType)
        assertEquals("cloudflare-ech.com+https://1.1.1.1/dns-query", profile.echConfigList)
        assertEquals(echOutbound, profile.echOutbound)
    }
}
