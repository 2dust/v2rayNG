package com.v2ray.ang.core

import com.v2ray.ang.enums.NetworkType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoreOutboundBuilderTest {
    @Test
    fun transportsWithBuiltInMultiplexingRejectAdditionalMux() {
        listOf(NetworkType.GRPC, NetworkType.H2, NetworkType.HTTP, NetworkType.XHTTP).forEach {
            assertFalse(CoreOutboundBuilder.supportsAdditionalMux(it.type), it.type)
        }
    }

    @Test
    fun otherTransportsKeepTheirExistingMuxSupport() {
        listOf(NetworkType.TCP, NetworkType.KCP, NetworkType.WS, NetworkType.HTTP_UPGRADE, NetworkType.HYSTERIA).forEach {
            assertTrue(CoreOutboundBuilder.supportsAdditionalMux(it.type), it.type)
        }
    }

    @Test
    fun missingOrUnknownTransportKeepsExistingMuxDecision() {
        listOf(null, "", "future-transport").forEach {
            assertTrue(CoreOutboundBuilder.supportsAdditionalMux(it))
        }
    }
}
