package com.v2ray.ang.ui.subscription

import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionAccessibilityTest {

    @Test
    fun namedSubscriptionsKeepTheirName() {
        assertEquals(" Lab ", subscriptionAccessibilityName(" Lab ", "https://example.com/sub", "Unnamed."))
    }

    @Test
    fun emptyAndWhitespaceNamesUseTheHost() {
        listOf("", " ", "\t\n", "\u00a0").forEach { name ->
            assertEquals("Unnamed. example.com", subscriptionAccessibilityName(name, "https://example.com/sub", "Unnamed."))
        }
    }

    @Test
    fun fallbackDoesNotExposeCredentialsPathQueryOrFragment() {
        val url = "https://user:password@example.com:8443/private/token?secret=value#fragment"
        assertEquals("Unnamed. example.com", subscriptionAccessibilityName("", url, "Unnamed."))
    }

    @Test
    fun hostDoesNotRequireATrailingSlash() {
        listOf("https://example.com", "http://example.com?token=secret", "https://example.com#fragment").forEach { url ->
            assertEquals("Unnamed. example.com", subscriptionAccessibilityName("", url, "Unnamed."))
        }
    }

    @Test
    fun missingOrInvalidUrlsKeepTheUnnamedLabel() {
        listOf("", " ", "not a url", "https://", "file:///private/token").forEach { url ->
            assertEquals("Unnamed.", subscriptionAccessibilityName("", url, "Unnamed."))
        }
    }

    @Test
    fun ipAddressesCanIdentifyUnnamedSubscriptions() {
        assertEquals("Unnamed. 192.0.2.1", subscriptionAccessibilityName("", "http://192.0.2.1/sub", "Unnamed."))
        assertEquals("Unnamed. 2001:db8::1", subscriptionAccessibilityName("", "https://[2001:db8::1]/sub", "Unnamed."))
    }

    @Test
    fun fallbackUsesTheLocalizedUnnamedLabel() {
        assertEquals("Без названия. example.com", subscriptionAccessibilityName("", "https://example.com/sub", "Без названия."))
        assertEquals("Без названия.", subscriptionAccessibilityName("", "", "Без названия."))
    }
}
