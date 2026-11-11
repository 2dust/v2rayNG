package com.v2ray.ang.ui.main

/**
 * Frame timing of the startup brand animation drawn by [MainSplashOverlay].
 *
 * The sequence lives here instead of inside the composable so it stays free of Compose and Android
 * types and can be asserted by a plain JVM test. Every function is pure: callers pass the elapsed
 * time of the animation, the overlay only renders the returned values.
 */
internal object MainSplashTimeline {

    /** Whole animation, including the closing fade. Nothing follows it. */
    const val TOTAL_MS = 2680f

    const val TILE_START_MS = 0f
    const val TILE_END_MS = 420f
    const val R_START_MS = 300f
    const val R_END_MS = 880f
    const val X_START_MS = 760f
    const val X_END_MS = 1360f
    const val GLOW_START_MS = 260f
    const val GLOW_END_MS = 900f
    const val RING_START_MS = 1280f
    const val RING_STEP_MS = 160f
    const val RING_LENGTH_MS = 900f
    const val SWEEP_START_MS = 1280f
    const val SWEEP_END_MS = 1820f
    const val WORD_START_MS = 1420f
    const val WORD_END_MS = 1880f
    const val TAGLINE_START_MS = 1660f
    const val TAGLINE_END_MS = 2060f
    const val BAR_START_MS = 1500f
    const val BAR_END_MS = 2050f
    const val FADE_START_MS = 2180f
    const val FADE_END_MS = 2680f

    /** Linear 0..1 progress of the window [startMs]..[endMs], clamped at both ends. */
    fun progress(elapsedMs: Float, startMs: Float, endMs: Float): Float {
        if (endMs <= startMs) return if (elapsedMs >= endMs) 1f else 0f
        return ((elapsedMs - startMs) / (endMs - startMs)).coerceIn(0f, 1f)
    }

    /** Ease-out-back curve: reaches 1 with a small overshoot, then settles. */
    fun overshoot(p: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val x = p.coerceIn(0f, 1f) - 1f
        return 1f + c3 * x * x * x + c1 * x * x
    }

    /** Scale of the icon tile: starts smaller than 1, overshoots slightly, settles at 1. */
    fun tileScale(elapsedMs: Float): Float =
        0.72f + 0.28f * overshoot(progress(elapsedMs, TILE_START_MS, TILE_END_MS))

    /**
     * Hauling of the app name's letter spacing while it lands: wide and airy as it fades in, then
     * it settles to its final tracking.
     */
    fun nameLetterSpacingSp(wordProgress: Float): Float =
        9f - 5f * wordProgress.coerceIn(0f, 1f)

    /**
     * Opacity of the tagline. It rides in on the name's landing, so the two read as one line of
     * copy rather than two separate events.
     */
    fun taglineAlpha(elapsedMs: Float): Float =
        progress(elapsedMs, TAGLINE_START_MS, TAGLINE_END_MS)

    /** Opacity of the whole overlay; stays 1 until the closing fade. */
    fun overlayAlpha(elapsedMs: Float): Float =
        1f - progress(elapsedMs, FADE_START_MS, FADE_END_MS)

    /** Expansion of an echo ring that starts [offsetMs] after the first one. */
    fun ringProgress(elapsedMs: Float, offsetMs: Float): Float =
        progress(elapsedMs, RING_START_MS + offsetMs, RING_START_MS + offsetMs + RING_LENGTH_MS)

    /** True once the overlay has nothing left to show. */
    fun isFinished(elapsedMs: Float): Boolean = elapsedMs >= TOTAL_MS
}
