package com.v2ray.ang.ui.splash

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.DotGlobe
import com.v2ray.ang.ui.compose.liquidGlass
import com.v2ray.ang.ui.main.MainActivity

/** Launcher entry: shows the Bismillah over a turning dotted globe and fills a thin progress line over about three seconds, then hands over to [MainActivity]. */
class SplashActivity : BaseComponentActivity() {

    @Composable
    override fun ScreenContent() {
        // Nothing to go back to: the user is either waiting or leaving the app.
        BackHandler { finish() }
        SplashScreen(onFinished = {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        })
    }
}

private const val SPLASH_DURATION_MS = 3200
private const val HANDOVER_DELAY_MS = 250L
private val splashStatusRes = intArrayOf(
    R.string.xf_splash_status_0,
    R.string.xf_splash_status_1,
    R.string.xf_splash_status_2,
    R.string.xf_splash_status_3,
    R.string.xf_splash_status_4,
    R.string.xf_splash_status_5,
)

/** Status line for a percentage: five stages while loading, the last one only at 100 %. */
internal fun splashStatusIndex(percent: Int): Int =
    if (percent >= 100) splashStatusRes.lastIndex else (percent.coerceAtLeast(0) * 5 / 100)

@Composable
private fun SplashScreen(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(durationMillis = 1100, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = SPLASH_DURATION_MS, easing = FastOutSlowInEasing))
        kotlinx.coroutines.delay(HANDOVER_DELAY_MS)
        onFinished()
    }
    val percent = (progress.value * 100f).toInt().coerceIn(0, 100)
    val scheme = MaterialTheme.colorScheme

    // A light sweep slides across the title while it is on screen.
    val shimmer by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
    val titleBrush = Brush.linearGradient(
        colorStops = arrayOf(
            0f to scheme.onBackground,
            (shimmer - 0.15f).coerceIn(0f, 1f) to scheme.onBackground,
            shimmer.coerceIn(0f, 1f) to scheme.secondary,
            (shimmer + 0.15f).coerceIn(0f, 1f) to scheme.onBackground,
            1f to scheme.onBackground,
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // The globe sits behind the title, slowly turning; it fades in with the title.
        DotGlobe(modifier = Modifier.fillMaxSize().alpha(reveal.value))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.xf_splash_title),
                modifier = Modifier
                    .alpha(reveal.value)
                    .scale(0.92f + 0.08f * reveal.value),
                style = MaterialTheme.typography.displayMedium.copy(
                    brush = titleBrush,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                ),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(56.dp))
            ProgressLine(progress = progress.value, modifier = Modifier.alpha(reveal.value))
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(splashStatusRes[splashStatusIndex(percent)]),
                modifier = Modifier.alpha(reveal.value),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = stringResource(R.string.xf_brand),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .alpha(reveal.value),
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            letterSpacing = 0.2.em,
        )
    }
}

/** 240 × 8 dp liquid-glass track with a violet gradient fill that grows with [progress]. */
@Composable
private fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .size(width = 240.dp, height = 8.dp)
            .liquidGlass(shape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(shape)
                .background(Brush.horizontalGradient(listOf(scheme.secondary, scheme.primary))),
        )
    }
}
