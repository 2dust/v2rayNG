package com.v2ray.ang.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import kotlin.math.roundToInt

/** Owns only the TV presentation; activity results and actions are shared with the phone. */
class TvMainActivity : BaseMainActivity() {
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun MainContent(viewModel: MainViewModel, onAction: (MainAction) -> Unit, onNavigate: (MainDestination) -> Unit) {
        val platform = LocalBringIntoViewSpec.current
        val scrollSpec = remember(platform) { PixelAlignedBringIntoViewSpec(platform) }
        CompositionLocalProvider(LocalBringIntoViewSpec provides scrollSpec) {
            TvMainScreen(viewModel, onAction, onNavigate)
        }
    }
}

internal fun pixelAlignedScrollDistance(distance: Float): Float = distance.roundToInt().toFloat()

/**
 * Isolates [ExperimentalFoundationApi] while retaining the platform focus policy and rounding its
 * movement to lazy-list pixels, preventing one-pixel oscillation on Android TV. Reevaluate when
 * BringIntoViewSpec becomes stable; remove this adapter when the stable platform policy returns
 * pixel-aligned distances on the supported Compose and TV versions.
 */
@OptIn(ExperimentalFoundationApi::class)
private class PixelAlignedBringIntoViewSpec(
    private val delegate: BringIntoViewSpec
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float
    ): Float = pixelAlignedScrollDistance(
        delegate.calculateScrollDistance(offset = offset, size = size, containerSize = containerSize)
    )
}
