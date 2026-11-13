package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateAnnouncementPolicyTest {
    @Test
    fun comparesDottedVersionsNumerically() {
        assertTrue(UpdateAnnouncementPolicy.compareVersions("2.3.14.34", "2.3.14.33") > 0)
        assertTrue(UpdateAnnouncementPolicy.compareVersions("2.3.9", "2.3.10") < 0)
        assertEquals(0, UpdateAnnouncementPolicy.compareVersions("2.3.14", "2.3.14.0"))
        assertTrue(UpdateAnnouncementPolicy.compareVersions("2.4", "2.3.99.99") > 0)
    }

    @Test
    fun nonNumericPartsCountAsZeroInsteadOfCrashing() {
        assertEquals(0, UpdateAnnouncementPolicy.compareVersions("2.3.x", "2.3.0"))
        assertEquals(0, UpdateAnnouncementPolicy.compareVersions("2.3.1-beta", "2.3.0"))
    }

    @Test
    fun announcesWhenNothingWasPostponed() {
        assertTrue(UpdateAnnouncementPolicy.shouldAnnounce("2.3.15", null))
        assertTrue(UpdateAnnouncementPolicy.shouldAnnounce("2.3.15", ""))
    }

    @Test
    fun staysQuietForThePostponedVersionAndOlder() {
        assertFalse(UpdateAnnouncementPolicy.shouldAnnounce("2.3.15", "2.3.15"))
        assertFalse(UpdateAnnouncementPolicy.shouldAnnounce("2.3.14", "2.3.15"))
    }

    @Test
    fun announcesAgainOnlyForANewerRelease() {
        assertTrue(UpdateAnnouncementPolicy.shouldAnnounce("2.3.16", "2.3.15"))
        assertTrue(UpdateAnnouncementPolicy.shouldAnnounce("2.3.15.2", "2.3.15.1"))
    }

    @Test
    fun checkIsDueFirstTimeAfterTheIntervalOrWhenTheClockWentBack() {
        val interval = 1_000L
        assertTrue(UpdateAnnouncementPolicy.isCheckDue(5_000, 0, interval))
        assertFalse(UpdateAnnouncementPolicy.isCheckDue(5_500, 5_000, interval))
        assertTrue(UpdateAnnouncementPolicy.isCheckDue(6_000, 5_000, interval))
        assertTrue(UpdateAnnouncementPolicy.isCheckDue(4_000, 5_000, interval))
    }
}
