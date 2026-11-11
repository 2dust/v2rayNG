package com.v2ray.ang.ui.main

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.rxLetterR
import com.v2ray.ang.ui.compose.rxLetterX
import com.v2ray.ang.util.LogUtil
import kotlin.math.min

/**
 * Startup brand animation: the RX tile pops in, its two letters are revealed left to right, a warm
 * sheen sweeps across the tile and the app name and a progress line follow, then the whole overlay
 * fades into [MainScreen].
 *
 * Presentation only: it owns no durable state and performs no repository, settings, or native work.
 * It draws with the active color scheme, so dark, light, and dynamic color are all covered. The
 * animation is skipped when the system animation scale is off and can be dismissed with a tap; the
 * caller hides the overlay exactly once through [onFinished].
 */
@Composable
fun MainSplashOverlay(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val motionDisabled = remember(context) { animationsDisabled(context) }
    val currentOnFinished by rememberUpdatedState(onFinished)

    // Negative until the first frame, so nothing is painted when the animation is skipped.
    var elapsedMs by remember { mutableFloatStateOf(-1f) }
    var finished by remember { mutableStateOf(false) }

    val finish = {
        if (!finished) {
            finished = true
            currentOnFinished()
        }
    }

    LaunchedEffect(Unit) {
        if (motionDisabled) {
            finish()
            return@LaunchedEffect
        }
        val startNs = withFrameNanos { it }
        while (true) {
            val frameNs = withFrameNanos { it }
            val elapsed = (frameNs - startNs) / 1_000_000f
            elapsedMs = min(elapsed, MainSplashTimeline.TOTAL_MS)
            if (MainSplashTimeline.isFinished(elapsed)) break
        }
        finish()
    }

    // Nothing at all when the user disabled motion; otherwise paint from t=0 so the main screen
    // never flashes through the one frame before the first clock tick.
    if (motionDisabled) return
    val elapsed = elapsedMs.coerceAtLeast(0f)

    val tileScale = MainSplashTimeline.tileScale(elapsed)
    val rProgress = MainSplashTimeline.progress(
        elapsed, MainSplashTimeline.R_START_MS, MainSplashTimeline.R_END_MS
    )
    val xProgress = MainSplashTimeline.progress(
        elapsed, MainSplashTimeline.X_START_MS, MainSplashTimeline.X_END_MS
    )
    val glowAlpha = MainSplashTimeline.progress(
        elapsed, MainSplashTimeline.GLOW_START_MS, MainSplashTimeline.GLOW_END_MS
    )
    val sweepProgress = MainSplashTimeline.progress(
        elapsed, MainSplashTimeline.SWEEP_START_MS, MainSplashTimeline.SWEEP_END_MS
    )
    val wordProgress = MainSplashTimeline.progress(
        elapsed, MainSplashTimeline.WORD_START_MS, MainSplashTimeline.WORD_END_MS
    )
    val barProgress = MainSplashTimeline.progress(
        elapsed, MainSplashTimeline.BAR_START_MS, MainSplashTimeline.BAR_END_MS
    )
    val taglineProgress = MainSplashTimeline.taglineAlpha(elapsed)
    val overlayAlpha = MainSplashTimeline.overlayAlpha(elapsed)
    val ringOne = MainSplashTimeline.ringProgress(elapsed, 0f)
    val ringTwo = MainSplashTimeline.ringProgress(elapsed, MainSplashTimeline.RING_STEP_MS)

    val letterR = remember { rxLetterR() }
    val letterX = remember { rxLetterX() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha }
            .background(Brush.verticalGradient(listOf(colors.surfaceContainerLow, colors.background)))
            .pointerInput(Unit) { detectTapGestures { finish() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(300.dp)) {
                    if (glowAlpha > 0f) {
                        val radius = size.minDimension / 2f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    colors.primary.copy(alpha = 0.30f * glowAlpha),
                                    Color.Transparent,
                                ),
                                center = center,
                                radius = radius,
                            ),
                            radius = radius,
                        )
                    }
                }
                Canvas(Modifier.size(300.dp)) {
                    drawRing(ringOne, colors.primary)
                    drawRing(ringTwo, colors.primary)
                }
                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .graphicsLayer {
                            scaleX = tileScale
                            scaleY = tileScale
                        }
                        .clip(RoundedCornerShape(30.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(colors.surfaceContainerHigh, colors.background)
                            )
                        )
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRxMark(
                            letterR = letterR,
                            letterX = letterX,
                            rProgress = rProgress,
                            xProgress = xProgress,
                            rColor = colors.onSurface,
                            xColor = colors.primary,
                        )
                    }
                    Canvas(Modifier.fillMaxSize()) {
                        drawSheen(sweepProgress, colors.onSurface)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.app_name),
                color = colors.onSurface,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = MainSplashTimeline.nameLetterSpacingSp(wordProgress).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    alpha = wordProgress
                    translationY = (1f - wordProgress) * 36f
                },
            )

            Spacer(Modifier.height(8.dp))

            // The line the app greets a returning user with, riding in right as the name lands.
            Text(
                text = stringResource(R.string.splash_tagline),
                color = colors.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    alpha = taglineProgress
                    translationY = (1f - taglineProgress) * 18f
                },
            )

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .width(124.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.onSurface.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = barProgress
                            transformOrigin = TransformOrigin(1f, 0.5f)
                        }
                        .background(
                            Brush.horizontalGradient(
                                listOf(colors.primary.copy(alpha = 0.55f), colors.primary)
                            )
                        )
                )
            }
        }
    }
}

/**
 * Accessibility: the system "Remove animations" developer/accessibility switch sets the animator
 * duration scale to zero. Reading it keeps the startup free of motion for those users instead of
 * playing a full-screen animation they asked the platform to suppress.
 */
private fun animationsDisabled(context: Context): Boolean = try {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
} catch (e: Exception) {
    LogUtil.w(AppConfig.TAG, "Could not read the animator duration scale", e)
    false
}

private fun DrawScope.drawRing(progress: Float, color: Color) {
    if (progress <= 0f || progress >= 1f) return
    val radius = size.minDimension / 2f * (0.33f + 0.67f * progress)
    drawCircle(
        color = color.copy(alpha = 0.38f * (1f - progress)),
        radius = radius,
        style = Stroke(width = 1.5.dp.toPx()),
    )
}

private fun DrawScope.drawRxMark(
    letterR: Path,
    letterX: Path,
    rProgress: Float,
    xProgress: Float,
    rColor: Color,
    xColor: Color,
) {
    if (rProgress <= 0f && xProgress <= 0f) return
    val unit = size.minDimension / 512f
    withTransform({ scale(unit, unit, Offset.Zero) }) {
        if (rProgress > 0f) {
            clipRect(left = 0f, top = 0f, right = 512f * rProgress, bottom = 512f) {
                drawPath(letterR, rColor)
            }
        }
        if (xProgress > 0f) {
            clipRect(left = 0f, top = 0f, right = 512f * xProgress, bottom = 512f) {
                drawPath(letterX, xColor)
            }
        }
    }
}

private fun DrawScope.drawSheen(progress: Float, highlight: Color) {
    if (progress <= 0f || progress >= 1f) return
    val band = size.width * 0.7f
    val current = -band + (size.width + band * 2f) * progress
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, highlight.copy(alpha = 0.30f), Color.Transparent),
            start = Offset(current - band, -band),
            end = Offset(current + band, size.height + band),
        )
    )
}
