package com.v2ray.ang.core

import com.v2ray.ang.dto.V2rayConfig.OutboundBean.StreamSettingsBean
import com.v2ray.ang.enums.NetworkType
import com.v2ray.ang.fmt.Hysteria2Fmt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CoreOutboundBuilderHysteria2Test {
    @Test
    fun appliesDefaultsAndOverridesOnlyWhenHoppingIsConfigured() {
        val ports = "20000-20100"
        val profile = Hysteria2Fmt.parse("hysteria2://test@example.org:443/?mport=$ports")
        profile.network = NetworkType.HYSTERIA.type

        fun interval(nodeValue: String?, setting: String?): String? {
            profile.portHoppingInterval = nodeValue
            val stream = StreamSettingsBean()
            CoreOutboundBuilder.populateTransportSettings(stream, profile) { setting }
            val mask = stream.finalmask as StreamSettingsBean.FinalMaskBean
            return mask.udp.orEmpty().singleOrNull { it.type == "udphop" }?.settings?.interval
        }

        assertEquals("30", interval(null, null))
        assertEquals("45", interval(null, "45"))
        assertEquals("30-60", interval(" ", "30-60"))
        assertEquals("20-40", interval("20-40", "30-60"))
        assertEquals("30", interval(null, "invalid"))
        profile.portHopping = null
        assertEquals(null, interval(null, "30-60"))
    }
}
