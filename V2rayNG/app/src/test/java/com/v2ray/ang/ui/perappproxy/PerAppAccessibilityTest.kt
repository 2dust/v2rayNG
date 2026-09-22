package com.v2ray.ang.ui.perappproxy

import com.v2ray.ang.R
import com.v2ray.ang.dto.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PerAppAccessibilityTest {
    private val self = AppInfo("v2rayNG", "self", false, 0, 10000)
    private val browser = AppInfo("Browser", "browser", false, 0, 10001)
    private val sharedBrowser = AppInfo("Shared browser UID", "browser.shared", false, 0, 10001)
    private val other = AppInfo("Other app", "other", false, 0, 10002)
    private val sharedSelf = AppInfo("Shared self UID", "self.shared", false, 0, 10000)
    private val installed = listOf(self, browser, sharedBrowser, other, sharedSelf)
    private val direct = R.string.acc_app_routed_directly
    private val through = R.string.acc_app_routed_through
    private val disabled = R.string.acc_per_app_routing_disabled

    private fun routing(
        mode: PerAppRoutingMode,
        selection: Set<String> = setOf(browser.packageName),
        bypass: Boolean = false,
        enabled: Boolean = true,
    ) = PerAppRouting(mode, enabled, bypass, selection, installed, self.packageName)

    private fun assertRoutes(routing: PerAppRouting, vararg expected: Pair<AppInfo, Int>) {
        expected.forEach { (app, description) ->
            assertEquals(app.packageName, description, routing.descriptionRes(app))
        }
    }

    @Test
    fun disabledRoutingOverridesEveryModeAndSelection() {
        for (mode in PerAppRoutingMode.entries) {
            for (bypass in listOf(false, true)) {
                for (selection in listOf(emptySet(), setOf(browser.packageName), installed.map { it.packageName }.toSet())) {
                    val routing = routing(mode, selection, bypass, enabled = false)
                    installed.forEach { assertEquals(disabled, routing.descriptionRes(it)) }
                }
            }
        }
    }

    @Test
    fun proxyOnlyDoesNotClaimPerAppRoutingEvenWhenTheSwitchIsOn() {
        for (bypass in listOf(false, true)) {
            for (selection in listOf(emptySet(), setOf(browser.packageName))) {
                val routing = routing(PerAppRoutingMode.PROXY_ONLY, selection, bypass)
                installed.forEach { assertEquals(disabled, routing.descriptionRes(it)) }
            }
        }
    }

    @Test
    fun allowlistUsesSelectedUidsRatherThanCheckboxState() {
        for (mode in listOf(PerAppRoutingMode.VPN, PerAppRoutingMode.ROOT)) {
            assertRoutes(routing(mode), browser to through, sharedBrowser to through, other to direct, self to direct)
        }
    }

    @Test
    fun bypassExcludesSelectedUidsAndSelfEvenWhenSelfIsUnchecked() {
        for (mode in listOf(PerAppRoutingMode.VPN, PerAppRoutingMode.ROOT)) {
            assertRoutes(routing(mode, bypass = true),
                browser to direct, sharedBrowser to direct, other to through, self to direct, sharedSelf to direct)
        }
    }

    @Test
    fun emptyVpnSelectionIncludesOtherAppsInBothModes() {
        for (bypass in listOf(false, true)) {
            assertRoutes(routing(PerAppRoutingMode.VPN, emptySet(), bypass),
                browser to through, other to through, self to direct, sharedSelf to direct)
        }
    }

    @Test
    fun emptyRootSelectionDoesNotUseTheVpnFallback() {
        assertRoutes(routing(PerAppRoutingMode.ROOT, emptySet()), browser to direct, other to direct, self to direct)
        assertRoutes(routing(PerAppRoutingMode.ROOT, emptySet(), bypass = true),
            browser to through, other to through, self to direct)
    }

    @Test
    fun vpnAllowlistWithOnlySelfOrRemovedPackagesMakesNoAllowedApplicationCalls() {
        for (selection in listOf(setOf(self.packageName), setOf("removed"), setOf(self.packageName, "removed"))) {
            assertRoutes(routing(PerAppRoutingMode.VPN, selection), browser to through, other to through, self to through)
        }
    }

    @Test
    fun rootNeverIncludesItsOwnUidAndDoesNotIncludeRemovedPackages() {
        for (selection in listOf(setOf(self.packageName), setOf("removed"), setOf(self.packageName, "removed"))) {
            assertRoutes(routing(PerAppRoutingMode.ROOT, selection),
                browser to direct, other to direct, self to direct, sharedSelf to direct)
        }
    }

    @Test
    fun remainingInstalledSelectionPreventsVpnCatchAllFallback() {
        assertRoutes(routing(PerAppRoutingMode.VPN, setOf(self.packageName, browser.packageName, "removed")),
            browser to through, sharedBrowser to through, other to direct, self to direct)
    }

    @Test
    fun bypassWithOnlySelfOrRemovedPackagesStillExcludesSelf() {
        for (mode in listOf(PerAppRoutingMode.VPN, PerAppRoutingMode.ROOT)) {
            for (selection in listOf(setOf(self.packageName), setOf("removed"))) {
                assertRoutes(routing(mode, selection, bypass = true),
                    browser to through, other to through, self to direct, sharedSelf to direct)
            }
        }
    }

    @Test
    fun allSelectedAppsAreIncludedOrBypassedExceptForSelf() {
        val selection = installed.map { it.packageName }.toSet() - sharedSelf.packageName
        for (mode in listOf(PerAppRoutingMode.VPN, PerAppRoutingMode.ROOT)) {
            assertRoutes(routing(mode, selection), browser to through, other to through, self to direct)
            assertRoutes(routing(mode, selection, bypass = true), browser to direct, other to direct, self to direct)
        }
    }

    @Test
    fun aSelectedSiblingOfSelfFollowsTheUnderlyingUidPolicy() {
        val selection = setOf(sharedSelf.packageName)
        assertRoutes(routing(PerAppRoutingMode.VPN, selection), self to through, sharedSelf to through, browser to direct)
        assertRoutes(routing(PerAppRoutingMode.ROOT, selection), self to direct, sharedSelf to direct, browser to direct)
    }

    @Test
    fun missingUidDoesNotProduceAnUnverifiedRoutingClaim() {
        val unknown = AppInfo("Unknown", "unknown", false, 0)
        assertNull(routing(PerAppRoutingMode.VPN).descriptionRes(unknown))
        assertNull(routing(PerAppRoutingMode.ROOT).descriptionRes(unknown))
        assertEquals(disabled, routing(PerAppRoutingMode.PROXY_ONLY).descriptionRes(unknown))
    }
}
