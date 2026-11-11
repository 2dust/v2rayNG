package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CloudflareAssistantTest {

    @Test
    fun announcedBlocksAreRecognised() {
        assertTrue(CloudflareAssistant.isCloudflareIpv4("104.16.0.1"))
        assertTrue(CloudflareAssistant.isCloudflareIpv4("104.27.255.255"))
        assertTrue(CloudflareAssistant.isCloudflareIpv4("172.67.1.1"))
        assertTrue(CloudflareAssistant.isCloudflareIpv4("162.159.36.1"))
        assertTrue(CloudflareAssistant.isCloudflareIpv4("188.114.99.5"))
        assertTrue(CloudflareAssistant.isCloudflareIpv4("198.41.200.1"))
        assertTrue(CloudflareAssistant.isCloudflareIpv4("131.0.72.7"))
    }

    @Test
    fun neighboursOfTheBlocksAreNotCloudflare() {
        assertFalse(CloudflareAssistant.isCloudflareIpv4("104.15.0.1"))
        assertFalse(CloudflareAssistant.isCloudflareIpv4("104.28.0.1"))
        assertFalse(CloudflareAssistant.isCloudflareIpv4("172.72.0.1"))
        assertFalse(CloudflareAssistant.isCloudflareIpv4("188.114.112.1"))
        assertFalse(CloudflareAssistant.isCloudflareIpv4("198.41.127.1"))
    }

    @Test
    fun nonIpValuesAreRejected() {
        assertFalse(CloudflareAssistant.isCloudflareIpv4(null))
        assertFalse(CloudflareAssistant.isCloudflareIpv4(""))
        assertFalse(CloudflareAssistant.isCloudflareIpv4("104.16.0.1:443"))
        assertFalse(CloudflareAssistant.isCloudflareIpv4("104.16.0"))
        assertFalse(CloudflareAssistant.isCloudflareIpv4("example.com"))
        assertFalse(CloudflareAssistant.isCloudflareIpv4("104.16.0.999"))
    }
}
