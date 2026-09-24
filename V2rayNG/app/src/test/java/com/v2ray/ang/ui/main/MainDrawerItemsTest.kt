package com.v2ray.ang.ui.main

import com.v2ray.ang.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MainDrawerItemsTest {

    @Test
    fun updateCheckEnabledListsEveryDestinationInDisplayOrder() {
        assertEquals(MainDestination.entries, mainDrawerItems(updateCheckEnabled = true))
    }

    @Test
    fun updateCheckDisabledOmitsOnlyCheckUpdate() {
        assertEquals(
            MainDestination.entries - MainDestination.CheckUpdate,
            mainDrawerItems(updateCheckEnabled = false),
        )
    }

    @Test
    fun primaryItemsLeadInBothConfigurations() {
        val primary = listOf(
            MainDestination.Subscriptions,
            MainDestination.PerAppProxy,
            MainDestination.Routing,
            MainDestination.UserAssets,
            MainDestination.Settings,
        )
        for (enabled in listOf(true, false)) {
            assertEquals(primary, mainDrawerItems(enabled).take(primary.size))
        }
    }

    // Runs once per flavor: the F-Droid build must not offer an in-app update
    // check, and the Play Store build keeps it.
    @Test
    fun updateCheckIsDisabledExactlyInTheFdroidBuild() {
        assertEquals(BuildConfig.DISTRIBUTION != "F-Droid", BuildConfig.UPDATE_CHECK_ENABLED)
        if (BuildConfig.DISTRIBUTION == "F-Droid") {
            assertFalse(MainDestination.CheckUpdate in mainDrawerItems(BuildConfig.UPDATE_CHECK_ENABLED))
        }
    }
}
