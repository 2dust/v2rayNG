package com.v2ray.ang.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.enums.Language
import com.v2ray.ang.ui.compose.RxGlassPanel
import com.v2ray.ang.ui.compose.RxMonogramTile

/**
 * First-launch language prompt, shown once on a fresh install before the home screen.
 *
 * It writes nothing itself: the caller applies the answer through
 * [com.v2ray.ang.handler.AppLocaleManager.setApplicationLanguage] and stores the choice, so the
 * activity recreation and the durable preference stay with the existing language machinery.
 */
@Composable
fun LanguageOnboardingScreen(
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val options = listOf(
        stringResource(R.string.onboarding_language_persian) to Language.PERSIAN.code,
        stringResource(R.string.onboarding_language_english) to Language.ENGLISH.code,
        stringResource(R.string.onboarding_language_system) to Language.AUTO.code,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(colors.surfaceContainerLow, colors.background))
            )
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RxMonogramTile(Modifier.size(88.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 24.sp,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(26.dp))
            Text(
                text = stringResource(R.string.onboarding_language_title),
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.onboarding_language_hint),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(26.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                options.forEach { (label, code) ->
                    LanguageChoiceCard(label = label, onClick = { onSelect(code) })
                }
            }
        }
    }
}

@Composable
private fun LanguageChoiceCard(label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    RxGlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
            )
        }
    }
}
