package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MainSplashTimelineTest {

    private val delta = 0.0001f

    @Test
    fun progressIsClampedOutsideItsWindow() {
        assertEquals(0f, MainSplashTimeline.progress(0f, 100f, 200f), delta)
        assertEquals(0f, MainSplashTimeline.progress(100f, 100f, 200f), delta)
        assertEquals(0.5f, MainSplashTimeline.progress(150f, 100f, 200f), delta)
        assertEquals(1f, MainSplashTimeline.progress(200f, 100f, 200f), delta)
        assertEquals(1f, MainSplashTimeline.progress(500f, 100f, 200f), delta)
    }

    @Test
    fun emptyWindowIsDoneImmediately() {
        assertEquals(0f, MainSplashTimeline.progress(50f, 100f, 100f), delta)
        assertEquals(1f, MainSplashTimeline.progress(100f, 100f, 100f), delta)
        assertEquals(1f, MainSplashTimeline.progress(150f, 200f, 100f), delta)
    }

    @Test
    fun tileStartsSmallOvershootsThenSettles() {
        assertEquals(0.72f, MainSplashTimeline.tileScale(0f), delta)
        assertEquals(1f, MainSplashTimeline.tileScale(MainSplashTimeline.TILE_END_MS), delta)
        val mid = MainSplashTimeline.tileScale(
            (MainSplashTimeline.TILE_START_MS + MainSplashTimeline.TILE_END_MS) / 2f
        )
        assertTrue(mid > 1f, "expected an overshoot, was $mid")
        assertTrue(mid < 1.15f, "overshoot must stay subtle, was $mid")
    }

    @Test
    fun overlayIsOpaqueUntilTheClosingFade() {
        assertEquals(1f, MainSplashTimeline.overlayAlpha(0f), delta)
        assertEquals(1f, MainSplashTimeline.overlayAlpha(MainSplashTimeline.FADE_START_MS), delta)
        assertEquals(0f, MainSplashTimeline.overlayAlpha(MainSplashTimeline.TOTAL_MS), delta)
        assertEquals(0f, MainSplashTimeline.overlayAlpha(MainSplashTimeline.TOTAL_MS * 2f), delta)
    }

    @Test
    fun ringsAreStaggeredAndReachTheEnd() {
        val step = MainSplashTimeline.RING_STEP_MS
        val start = MainSplashTimeline.RING_START_MS
        val length = MainSplashTimeline.RING_LENGTH_MS

        assertEquals(0f, MainSplashTimeline.ringProgress(start, 0f), delta)
        assertEquals(0f, MainSplashTimeline.ringProgress(start, step), delta)

        // Halfway through the second ring's lead-in the first ring is already expanding.
        assertTrue(MainSplashTimeline.ringProgress(start + step, 0f) > 0f)
        assertEquals(0f, MainSplashTimeline.ringProgress(start + step, step), delta)

        assertEquals(1f, MainSplashTimeline.ringProgress(start + length, 0f), delta)
        assertEquals(1f, MainSplashTimeline.ringProgress(start + step + length, step), delta)
    }

    @Test
    fun everyWindowFitsInsideTheTotalLength() {
        val ends = listOf(
            MainSplashTimeline.TILE_END_MS,
            MainSplashTimeline.R_END_MS,
            MainSplashTimeline.X_END_MS,
            MainSplashTimeline.GLOW_END_MS,
            MainSplashTimeline.SWEEP_END_MS,
            MainSplashTimeline.WORD_END_MS,
            MainSplashTimeline.TAGLINE_END_MS,
            MainSplashTimeline.BAR_END_MS,
            MainSplashTimeline.FADE_END_MS,
            MainSplashTimeline.RING_START_MS + MainSplashTimeline.RING_STEP_MS + MainSplashTimeline.RING_LENGTH_MS,
        )
        ends.forEach { assertTrue(it <= MainSplashTimeline.TOTAL_MS, "window ends at $it") }
        listOf(
            MainSplashTimeline.TILE_START_MS to MainSplashTimeline.TILE_END_MS,
            MainSplashTimeline.R_START_MS to MainSplashTimeline.R_END_MS,
            MainSplashTimeline.X_START_MS to MainSplashTimeline.X_END_MS,
            MainSplashTimeline.TAGLINE_START_MS to MainSplashTimeline.TAGLINE_END_MS,
        ).forEach { (start, end) -> assertTrue(start < end, "window $start..$end") }
    }

    @Test
    fun taglineRidesInOnTheNameAndSettlesBeforeTheFade() {
        // Hidden while the tile and letters are still being drawn.
        assertEquals(0f, MainSplashTimeline.taglineAlpha(0f), delta)
        assertEquals(0f, MainSplashTimeline.taglineAlpha(MainSplashTimeline.TAGLINE_START_MS), delta)
        // Starts after the name's own window opened, so the two read as one line of copy.
        assertTrue(MainSplashTimeline.TAGLINE_START_MS > MainSplashTimeline.WORD_START_MS)
        assertEquals(1f, MainSplashTimeline.taglineAlpha(MainSplashTimeline.TAGLINE_END_MS), delta)
        // Fully readable before the overlay starts fading away.
        assertTrue(MainSplashTimeline.TAGLINE_END_MS < MainSplashTimeline.FADE_START_MS)
    }

    @Test
    fun nameTrackingTightensAsItLands() {
        val wide = MainSplashTimeline.nameLetterSpacingSp(0f)
        val settled = MainSplashTimeline.nameLetterSpacingSp(1f)
        assertEquals(9f, wide, delta)
        assertEquals(4f, settled, delta)
        assertTrue(
            MainSplashTimeline.nameLetterSpacingSp(0.5f) in settled..wide,
            "tracking must tighten monotonically",
        )
    }

    @Test
    fun finishFlagFollowsTheTotalLength() {
        assertFalse(MainSplashTimeline.isFinished(MainSplashTimeline.TOTAL_MS - 1f))
        assertTrue(MainSplashTimeline.isFinished(MainSplashTimeline.TOTAL_MS))
    }
}
