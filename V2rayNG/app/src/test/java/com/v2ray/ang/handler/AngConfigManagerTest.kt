package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for AngConfigManager.applySubscriptionOverrides.
 */
class AngConfigManagerTest {

    private fun createProfile(): ProfileItem =
        ProfileItem.create(EConfigType.VLESS).apply {
            server = "188.114.97.3"
            serverPort = "443"
        }

    @Test
    fun test_applySubscriptionOverrides_replacesAddressAndPort() {
        val profile = createProfile()

        AngConfigManager.applySubscriptionOverrides(
            profile,
            SubscriptionItem(overrideAddress = "example.com", overridePort = 8443)
        )

        assertEquals("example.com", profile.server)
        assertEquals("8443", profile.serverPort)
    }

    @Test
    fun test_applySubscriptionOverrides_replacesOnlyTheConfiguredValue() {
        val addressOnly = createProfile()
        AngConfigManager.applySubscriptionOverrides(addressOnly, SubscriptionItem(overrideAddress = "example.com"))
        assertEquals("example.com", addressOnly.server)
        assertEquals("443", addressOnly.serverPort)

        val portOnly = createProfile()
        AngConfigManager.applySubscriptionOverrides(portOnly, SubscriptionItem(overridePort = 8443))
        assertEquals("188.114.97.3", portOnly.server)
        assertEquals("8443", portOnly.serverPort)
    }

    @Test
    fun test_applySubscriptionOverrides_keepsProfileWhenNothingIsConfigured() {
        val subItems = listOf(
            null,
            SubscriptionItem(),
            SubscriptionItem(overrideAddress = "   ", overridePort = 0),
            SubscriptionItem(overridePort = 70000),
        )

        for (subItem in subItems) {
            val profile = createProfile()

            AngConfigManager.applySubscriptionOverrides(profile, subItem)

            assertEquals("188.114.97.3", profile.server)
            assertEquals("443", profile.serverPort)
        }
    }

    @Test
    fun test_applySubscriptionOverrides_trimsAddress() {
        val profile = createProfile()

        AngConfigManager.applySubscriptionOverrides(profile, SubscriptionItem(overrideAddress = " example.com "))

        assertEquals("example.com", profile.server)
    }
}
