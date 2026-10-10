package com.v2ray.ang.ui.splash

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.SpeederLoader
import com.v2ray.ang.ui.main.MainActivity

/** Launcher entry: the opening words fade in over the speeder loader, then it hands over to [MainActivity]. */
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

private const val TITLE_FADE_MS = 1800
private const val HANDOVER_DELAY_MS = 500L
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
    val scheme = MaterialTheme.colorScheme
    // The opening words fade in over about two seconds while rising a few dp; only alpha and translation animate.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = TITLE_FADE_MS, easing = FastOutSlowInEasing))
        kotlinx.coroutines.delay(HANDOVER_DELAY_MS)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .padding(top = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.xf_splash_title),
                modifier = Modifier.graphicsLayer {
                    alpha = progress.value
                    translationY = (1f - progress.value) * 16.dp.toPx()
                },
                color = scheme.onBackground,
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 38.sp, fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(48.dp))
            SpeederLoader()
        }
        Text(
            text = stringResource(R.string.xf_brand),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            letterSpacing = 0.2.em,
        )
    }
}
