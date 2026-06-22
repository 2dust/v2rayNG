package com.v2ray.ang.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FragmentSettingsTest {
    @Test
    fun blankMaxSplitIsOmitted() {
        assertNull(FragmentSettings.normalizeMaxSplit(null))
        assertNull(FragmentSettings.normalizeMaxSplit(""))
        assertNull(FragmentSettings.normalizeMaxSplit("   "))
    }

    @Test
    fun maxSplitIsTrimmedAndKept() {
        assertEquals("3", FragmentSettings.normalizeMaxSplit(" 3 "))
        assertEquals("1-3", FragmentSettings.normalizeMaxSplit(" 1-3 "))
    }
}
