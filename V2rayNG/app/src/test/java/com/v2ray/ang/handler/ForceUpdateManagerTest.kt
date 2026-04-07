package com.v2ray.ang.handler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Calendar

class ForceUpdateManagerTest {

    @Test
    fun testGetDaysRemaining() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 5)
        val futureDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(calendar.time)

        val remoteVersion = ForceUpdateManager.RemoteVersion(blockingDate = futureDate)
        val daysRemaining = ForceUpdateManager.getDaysRemaining(remoteVersion)

        // It should be 4 or 5 depending on the exact time, but let's just check it's positive
        assertTrue("Days remaining should be positive", daysRemaining >= 4)
    }

    @Test
    fun testGetDaysRemainingPast() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -5)
        val pastDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(calendar.time)

        val remoteVersion = ForceUpdateManager.RemoteVersion(blockingDate = pastDate)
        val daysRemaining = ForceUpdateManager.getDaysRemaining(remoteVersion)

        assertTrue("Days remaining should be negative", daysRemaining <= -5)
    }

    @Test
    fun testGetDaysRemainingEmpty() {
        val remoteVersion = ForceUpdateManager.RemoteVersion(blockingDate = "")
        val daysRemaining = ForceUpdateManager.getDaysRemaining(remoteVersion)
        assertEquals(999, daysRemaining)
    }
}
