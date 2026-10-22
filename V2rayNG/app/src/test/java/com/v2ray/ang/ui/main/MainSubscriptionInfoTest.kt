package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MainSubscriptionInfoTest {
    private val now = 1_000_000_000_000L // ms

    @Test
    fun limitedTrafficComputesFraction() {
        val model = buildSubscriptionInfoUiModel(SubscriptionUsageInfo(upload = 100, download = 300, total = 1000), now)
        assertEquals(400, model.usedBytes)
        assertEquals(0.4f, model.trafficFraction!!, 0.0001f)
        assertFalse(model.trafficLow)
    }

    @Test
    fun unlimitedTrafficHasNoFraction() {
        val model = buildSubscriptionInfoUiModel(SubscriptionUsageInfo(download = 5), now)
        assertEquals(0L, model.totalBytes)
        assertNull(model.trafficFraction)
        assertFalse(model.trafficLow)
    }

    @Test
    fun overUsageIsClampedAndFlaggedLow() {
        val model = buildSubscriptionInfoUiModel(SubscriptionUsageInfo(download = 2000, total = 1000), now)
        assertEquals(1f, model.trafficFraction!!, 0.0001f)
        assertTrue(model.trafficLow)
    }

    @Test
    fun expiryIsConvertedToMillisAndFlaggedWhenPast() {
        val future = buildSubscriptionInfoUiModel(SubscriptionUsageInfo(expire = now / 1000 + 60), now)
        assertEquals(now + 60_000, future.expireMillis)
        assertFalse(future.expired)
        assertTrue(buildSubscriptionInfoUiModel(SubscriptionUsageInfo(expire = now / 1000 - 1), now).expired)
    }

    @Test
    fun zeroExpireMeansNeverExpires() {
        val model = buildSubscriptionInfoUiModel(SubscriptionUsageInfo(expire = 0), now)
        assertNull(model.expireMillis)
        assertFalse(model.expired)
    }
}
