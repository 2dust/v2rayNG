package com.v2ray.ang.ui.perappproxy

import com.v2ray.ang.AppConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PerAppRoutingModeTest {
    @Test
    fun legacyFlagsMapToExplicitModes() {
        assertEquals(PerAppRoutingMode.ALL, PerAppRoutingMode.fromSettings(false, false))
        assertEquals(PerAppRoutingMode.ALL, PerAppRoutingMode.fromSettings(false, true))
        assertEquals(PerAppRoutingMode.ONLY_SELECTED, PerAppRoutingMode.fromSettings(true, false))
        assertEquals(PerAppRoutingMode.EXCEPT_SELECTED, PerAppRoutingMode.fromSettings(true, true))
    }

    @Test
    fun everyModeRoundTripsThroughLegacyFlags() {
        PerAppRoutingMode.entries.forEach { mode ->
            assertEquals(mode, PerAppRoutingMode.fromSettings(mode.perAppEnabled, mode.bypassSelected))
        }
    }

    @Test
    fun bulkSelectionEnablesOnlyModeButPreservesBypassMode() {
        assertEquals(PerAppRoutingMode.ONLY_SELECTED, PerAppRoutingMode.afterBulkSelection(PerAppRoutingMode.ALL))
        assertEquals(PerAppRoutingMode.ONLY_SELECTED, PerAppRoutingMode.afterBulkSelection(PerAppRoutingMode.ONLY_SELECTED))
        assertEquals(PerAppRoutingMode.EXCEPT_SELECTED, PerAppRoutingMode.afterBulkSelection(PerAppRoutingMode.EXCEPT_SELECTED))
    }

    @Test
    fun everyTransitionPersistsTheExistingKeys() {
        PerAppRoutingMode.entries.forEach { previous ->
            PerAppRoutingMode.entries.forEach { target ->
                val writes = mutableMapOf<String, Boolean>()
                assertTrue(PerAppRoutingMode.persist(previous, target) { key, value ->
                    writes[key] = value
                    true
                })
                val expected = buildMap {
                    if (previous.perAppEnabled != target.perAppEnabled) {
                        put(AppConfig.PREF_PER_APP_PROXY, target.perAppEnabled)
                    }
                    if (previous.bypassSelected != target.bypassSelected) {
                        put(AppConfig.PREF_BYPASS_APPS, target.bypassSelected)
                    }
                }
                assertEquals(expected, writes)
            }
        }
    }

    @Test
    fun firstWriteFailureDoesNotWriteTheSecondFlag() {
        val writes = mutableListOf<String>()
        assertFalse(PerAppRoutingMode.persist(PerAppRoutingMode.ALL, PerAppRoutingMode.EXCEPT_SELECTED) { key, _ ->
            writes += key
            false
        })
        assertEquals(listOf(AppConfig.PREF_PER_APP_PROXY), writes)
    }

    @Test
    fun secondWriteFailureRestoresTheFirstFlag() {
        val values = mutableMapOf(
            AppConfig.PREF_PER_APP_PROXY to false,
            AppConfig.PREF_BYPASS_APPS to false,
        )
        assertFalse(PerAppRoutingMode.persist(PerAppRoutingMode.ALL, PerAppRoutingMode.EXCEPT_SELECTED) { key, value ->
            if (key == AppConfig.PREF_BYPASS_APPS) return@persist false
            values[key] = value
            true
        })
        assertEquals(false, values[AppConfig.PREF_PER_APP_PROXY])
        assertEquals(false, values[AppConfig.PREF_BYPASS_APPS])
    }

    @Test
    fun thrownWriteFailureRestoresChangedFlags() {
        val values = mutableMapOf(
            AppConfig.PREF_PER_APP_PROXY to false,
            AppConfig.PREF_BYPASS_APPS to false,
        )
        var failOnce = true
        assertFalse(PerAppRoutingMode.persist(PerAppRoutingMode.ALL, PerAppRoutingMode.EXCEPT_SELECTED) { key, value ->
            if (key == AppConfig.PREF_BYPASS_APPS && failOnce) {
                failOnce = false
                error("write failed")
            }
            values[key] = value
            true
        })
        assertEquals(false, values[AppConfig.PREF_PER_APP_PROXY])
        assertEquals(false, values[AppConfig.PREF_BYPASS_APPS])
    }

    @Test
    fun failedRollbackIsNeverReportedAsSuccess() {
        var enabledWriteAttempts = 0
        assertThrows(IllegalStateException::class.java) {
            PerAppRoutingMode.persist(PerAppRoutingMode.ALL, PerAppRoutingMode.EXCEPT_SELECTED) { key, _ ->
                when (key) {
                    // The initial enable succeeds, but restoring it during rollback fails.
                    AppConfig.PREF_PER_APP_PROXY -> ++enabledWriteAttempts == 1
                    else -> false
                }
            }
        }
        assertEquals(2, enabledWriteAttempts)
    }
}
