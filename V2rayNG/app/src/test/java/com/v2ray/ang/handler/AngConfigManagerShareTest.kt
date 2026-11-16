package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.NetworkType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AngConfigManagerShareTest {

    @Test
    fun xdriveProfilesHaveNoShareLink() {
        val profile = ProfileItem.create(EConfigType.VLESS).apply { network = NetworkType.XDRIVE.type }

        assertFalse(AngConfigManager.hasShareLinkFormat(profile))
    }

    @Test
    fun otherTransportsKeepTheirShareLink() {
        listOf(NetworkType.TCP, NetworkType.WS, NetworkType.XHTTP, NetworkType.GRPC).forEach { type ->
            val profile = ProfileItem.create(EConfigType.VLESS).apply { network = type.type }
            assertTrue(AngConfigManager.hasShareLinkFormat(profile), type.type)
        }
        assertTrue(AngConfigManager.hasShareLinkFormat(ProfileItem.create(EConfigType.VMESS)))
    }
}
