package com.v2ray.ang.ui.perappproxy

import com.v2ray.ang.R
import org.junit.Assert.assertEquals
import org.junit.Test

class PerAppAccessibilityTest {

    @Test
    fun disabledPerAppRoutingOverridesSelectionAndMode() {
        listOf(false, true).forEach { bypassApps ->
            listOf(false, true).forEach { checked ->
                assertEquals(
                    R.string.acc_per_app_routing_disabled,
                    perAppRoutingDescriptionRes(false, bypassApps, checked),
                )
            }
        }
    }

    @Test
    fun proxyModeRoutesOnlyCheckedAppsThroughTheApp() {
        assertEquals(
            R.string.acc_app_routed_directly,
            perAppRoutingDescriptionRes(true, bypassApps = false, checked = false),
        )
        assertEquals(
            R.string.acc_app_routed_through,
            perAppRoutingDescriptionRes(true, bypassApps = false, checked = true),
        )
    }

    @Test
    fun bypassModeRoutesOnlyUncheckedAppsThroughTheApp() {
        assertEquals(
            R.string.acc_app_routed_through,
            perAppRoutingDescriptionRes(true, bypassApps = true, checked = false),
        )
        assertEquals(
            R.string.acc_app_routed_directly,
            perAppRoutingDescriptionRes(true, bypassApps = true, checked = true),
        )
    }
}
