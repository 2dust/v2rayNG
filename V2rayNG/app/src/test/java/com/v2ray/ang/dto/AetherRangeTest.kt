package com.v2ray.ang.dto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AetherRangeTest {

    @Test
    fun aNumberOrARangeIsReadAndWrittenBack() {
        assertEquals("24", AetherRange.parse("24", AetherRange.FRAGMENT_SIZE).toString())
        assertEquals("16-32", AetherRange.parse("16-32", AetherRange.FRAGMENT_SIZE).toString())
        assertEquals("16-32", AetherRange.parse(" 32 - 16 ", AetherRange.FRAGMENT_SIZE).toString())
        assertEquals("0-10", AetherRange.parse("0-10", AetherRange.FRAGMENT_DELAY).toString())
        assertEquals("16-32", AetherRange.parse("۱۶-۳۲", AetherRange.FRAGMENT_SIZE).toString())
    }

    @Test
    fun valuesOutsideTheLimitsAreRefused() {
        assertNull(AetherRange.parse("0", AetherRange.FRAGMENT_SIZE))
        assertNull(AetherRange.parse("16-5000", AetherRange.FRAGMENT_SIZE))
        assertNull(AetherRange.parse("2-2000", AetherRange.FRAGMENT_DELAY))
    }

    @Test
    fun anythingElseIsNotARange() {
        assertNull(AetherRange.parse("", AetherRange.FRAGMENT_SIZE))
        assertNull(AetherRange.parse(null, AetherRange.FRAGMENT_SIZE))
        assertNull(AetherRange.parse("-5", AetherRange.FRAGMENT_SIZE))
        assertNull(AetherRange.parse("1-2-3", AetherRange.FRAGMENT_SIZE))
        assertNull(AetherRange.parse("16-", AetherRange.FRAGMENT_SIZE))
        assertNull(AetherRange.parse("abc", AetherRange.FRAGMENT_SIZE))
    }
}
