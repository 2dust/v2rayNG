package com.v2ray.ang.ui.splash

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.GlassCardShape
import com.v2ray.ang.ui.compose.XfGreen
import com.v2ray.ang.ui.compose.liquidGlass
import com.v2ray.ang.ui.main.MainActivity

/** Launcher entry: shows the opening words and a few lines typed out like a terminal session, in about a second, then hands over to [MainActivity]. */
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

private const val TYPING_MS = 950
private const val HANDOVER_DELAY_MS = 200L
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

/** A run of terminal text in one color. */
private class TermSegment(val text: String, val color: Color)

@Composable
private fun SplashScreen(onFinished: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val segments = remember(scheme) {
        listOf(
            TermSegment("$ ", scheme.primary), TermSegment("matixray --start\n", scheme.onSurface),
            TermSegment("[ ok ] ", XfGreen), TermSegment("xray core\n", scheme.onSurfaceVariant),
            TermSegment("[ ok ] ", XfGreen), TermSegment("routing rules\n", scheme.onSurfaceVariant),
            TermSegment("[ ok ] ", XfGreen), TermSegment("secure tunnel\n", scheme.onSurfaceVariant),
            TermSegment("$ ", scheme.primary), TermSegment("ready", scheme.onSurface),
        )
    }
    val total = remember(segments) { segments.sumOf { it.text.length } }
    val typed = remember { Animatable(0f) }
    // The words are on screen from the first frame and the typing starts at once: nothing waits for a fade-in.
    LaunchedEffect(Unit) {
        typed.animateTo(total.toFloat(), tween(durationMillis = TYPING_MS, easing = LinearEasing))
        kotlinx.coroutines.delay(HANDOVER_DELAY_MS)
        onFinished()
    }
    val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Restart),
        label = "blink",
    )

    val count = typed.value.toInt().coerceIn(0, total)
    val text = buildAnnotatedString {
        var left = count
        for (segment in segments) {
            if (left <= 0) break
            val take = minOf(left, segment.text.length)
            withStyle(SpanStyle(color = segment.color)) { append(segment.text.substring(0, take)) }
            left -= take
        }
        // Block cursor: solid while typing, blinking at the end.
        if (count < total || blink < 0.5f) withStyle(SpanStyle(color = scheme.primary)) { append("\u258C") }
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
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.xf_splash_title),
                color = scheme.onBackground,
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 38.sp, fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))
            // Code reads left to right whatever the language of the app is.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(GlassCardShape)
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Start,
                    minLines = 5,
                )
            }
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
