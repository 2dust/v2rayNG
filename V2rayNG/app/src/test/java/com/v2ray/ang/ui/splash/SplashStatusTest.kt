package com.v2ray.ang.ui.splash

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SplashStatusTest {
    @Test
    fun fiveLoadingStagesThenReadyOnlyAtOneHundred() {
        assertEquals(0, splashStatusIndex(0))
        assertEquals(0, splashStatusIndex(19))
        assertEquals(1, splashStatusIndex(20))
        assertEquals(2, splashStatusIndex(40))
        assertEquals(3, splashStatusIndex(60))
        assertEquals(4, splashStatusIndex(99))
        assertEquals(5, splashStatusIndex(100))
    }

    @Test
    fun outOfRangeInputsAreClamped() {
        assertEquals(0, splashStatusIndex(-5))
        assertEquals(5, splashStatusIndex(250))
    }
}
