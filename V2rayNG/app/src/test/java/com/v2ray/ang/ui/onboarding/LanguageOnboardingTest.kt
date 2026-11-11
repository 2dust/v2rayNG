package com.v2ray.ang.ui.onboarding

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LanguageOnboardingTest {

    @Test
    fun freshInstallIsAsked() {
        assertTrue(LanguageOnboarding.shouldAsk(chosen = false, freshInstall = true))
    }

    @Test
    fun upgradeIsNotAsked() {
        assertFalse(LanguageOnboarding.shouldAsk(chosen = false, freshInstall = false))
    }

    @Test
    fun answeredInstallIsNeverAskedAgain() {
        assertFalse(LanguageOnboarding.shouldAsk(chosen = true, freshInstall = true))
        assertFalse(LanguageOnboarding.shouldAsk(chosen = true, freshInstall = false))
    }
}
