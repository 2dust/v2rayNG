package com.v2ray.ang.ui.compose

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThemeTest {
    @Test
    fun newInstallDefaultsToGraphiteDarkTheme() {
        assertEquals("2", ThemeManager.DEFAULT_UI_MODE)
        assertTrue(resolveDarkTheme(ThemeManager.DEFAULT_UI_MODE, systemDark = false))
    }

    @Test
    fun explicitSavedThemeOverridesTheNewDefault() {
        assertFalse(resolveDarkTheme("1", systemDark = true))
        assertTrue(resolveDarkTheme("2", systemDark = false))
        assertFalse(resolveDarkTheme("0", systemDark = false))
        assertTrue(resolveDarkTheme("0", systemDark = true))
    }

    @Test
    fun unknownStoredValueKeepsSystemFallback() {
        assertFalse(resolveDarkTheme("legacy", systemDark = false))
        assertTrue(resolveDarkTheme("legacy", systemDark = true))
    }
}
