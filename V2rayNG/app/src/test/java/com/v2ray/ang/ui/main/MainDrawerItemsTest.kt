package com.v2ray.ang.ui.main

import com.v2ray.ang.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MainDrawerItemsTest {

    @Test
    fun everythingEnabledListsEveryDestinationInDisplayOrder() {
        assertEquals(
            MainDestination.entries,
            mainDrawerItems(updateCheckEnabled = true, promotionEnabled = true),
        )
    }

    @Test
    fun updateCheckDisabledOmitsOnlyCheckUpdate() {
        assertEquals(
            MainDestination.entries - MainDestination.CheckUpdate,
            mainDrawerItems(updateCheckEnabled = false, promotionEnabled = true),
        )
    }

    @Test
    fun promotionDisabledOmitsOnlyPromotion() {
        assertEquals(
            MainDestination.entries - MainDestination.Promotion,
            mainDrawerItems(updateCheckEnabled = true, promotionEnabled = false),
        )
    }

    @Test
    fun bothDisabledOmitsBoth() {
        assertEquals(
            MainDestination.entries - MainDestination.CheckUpdate - MainDestination.Promotion,
            mainDrawerItems(updateCheckEnabled = false, promotionEnabled = false),
        )
    }

    @Test
    fun primaryItemsLeadInEveryConfiguration() {
        val primary = listOf(
            MainDestination.Subscriptions,
            MainDestination.PerAppProxy,
            MainDestination.Routing,
            MainDestination.UserAssets,
            MainDestination.Settings,
        )
        for (updateCheck in listOf(true, false)) {
            for (promotion in listOf(true, false)) {
                assertEquals(primary, mainDrawerItems(updateCheck, promotion).take(primary.size))
            }
        }
    }

    // Runs once per flavor: the F-Droid build offers neither an in-app update
    // check nor the promotion page, and the Play Store build keeps both.
    @Test
    fun optionalEntriesAreOffExactlyInTheFdroidBuild() {
        val fdroid = BuildConfig.DISTRIBUTION == "F-Droid"
        assertEquals(!fdroid, BuildConfig.UPDATE_CHECK_ENABLED)
        assertEquals(!fdroid, BuildConfig.PROMOTION_ENABLED)
        if (fdroid) {
            val items = mainDrawerItems(BuildConfig.UPDATE_CHECK_ENABLED, BuildConfig.PROMOTION_ENABLED)
            assertFalse(MainDestination.CheckUpdate in items)
            assertFalse(MainDestination.Promotion in items)
        }
    }
}
