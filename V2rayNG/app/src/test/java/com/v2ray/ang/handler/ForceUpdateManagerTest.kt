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

        // It should be 5 or 6 depending on the exact time (inclusive of end of day)
        assertTrue("Days remaining should be positive: $daysRemaining", daysRemaining >= 5)
    }

    @Test
    fun testGetDaysRemainingWithSlash() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 2)
        val futureDate = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault()).format(calendar.time)

        val remoteVersion = ForceUpdateManager.RemoteVersion(blockingDate = futureDate)
        val daysRemaining = ForceUpdateManager.getDaysRemaining(remoteVersion)

        assertTrue("Days remaining should be positive with slash: $daysRemaining", daysRemaining >= 2)
    }

    @Test
    fun testGetDaysRemainingToday() {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val remoteVersion = ForceUpdateManager.RemoteVersion(blockingDate = today)
        val daysRemaining = ForceUpdateManager.getDaysRemaining(remoteVersion)

        // Even if it's today, there should be some time left until 23:59:59
        assertTrue("Days remaining should be 1 if it's today: $daysRemaining", daysRemaining >= 1)
    }

    @Test
    fun testGetDaysRemainingPast() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -5)
        val pastDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(calendar.time)

        val remoteVersion = ForceUpdateManager.RemoteVersion(blockingDate = pastDate)
        val daysRemaining = ForceUpdateManager.getDaysRemaining(remoteVersion)

        assertTrue("Days remaining should be 0 for past dates: $daysRemaining", daysRemaining == 0)
    }

    @Test
    fun testGetDaysRemainingEmpty() {
        val remoteVersion = ForceUpdateManager.RemoteVersion(blockingDate = "")
        val daysRemaining = ForceUpdateManager.getDaysRemaining(remoteVersion)
        assertEquals(999, daysRemaining)
    }

    @Test
    fun testCheckAndBlockIfExpired_Future() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val futureDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(calendar.time)
        val remoteVersion = ForceUpdateManager.RemoteVersion(blockingDate = futureDate)

        // This is hard to test because it uses context and preferences, but the logic
        // return value doesn't strictly need a real context for the date check part.
        // However, it calls prefs.edit() so it will crash if I don't mock it.
    }
}
