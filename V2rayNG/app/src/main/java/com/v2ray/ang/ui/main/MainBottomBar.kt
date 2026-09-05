package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.AccessibilityLiveRegionText
import com.v2ray.ang.ui.compose.colorFabActive
import com.v2ray.ang.ui.compose.colorFabInactiveDark
import com.v2ray.ang.ui.compose.colorFabInactiveLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

@Composable
fun MainBottomBar(
    displayText: String,
    accessibilityText: String,
    status: MainStatus,
    testAnnouncements: Flow<MainTestAnnouncement?>,
    formatTestAnnouncement: (MainStatus) -> String,
    isRunning: Boolean,
    isDarkTheme: Boolean,
    onAction: (MainAction) -> Unit
) {
    val announcements = remember { MainTestAnnouncements() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var publishedId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(testAnnouncements, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                testAnnouncements.collect(announcements::offer)
            } finally {
                announcements.clear()
            }
        }
    }

    val testAnnouncement = announcements.current
    LaunchedEffect(testAnnouncement?.id, publishedId) {
        val id = testAnnouncement?.id ?: return@LaunchedEffect
        if (publishedId != id) return@LaunchedEffect
        // Preserve publication order, including fast Testing -> result updates. This is a
        // semantics lifetime, not a timer claiming to know when TalkBack finishes speaking.
        delay(TestLiveRegionLifetimeMs)
        announcements.advance(id)
    }
    val exposeTestResult = isRunning && status is MainStatus.ConnectionTest && testAnnouncement == null
    val resultText = localizedTestText(if (exposeTestResult) formatTestAnnouncement(status) else "")

    val checkConnectionLabel = stringResource(R.string.connection_test_pending)
    val connectionActionModifier = if (isRunning) {
        Modifier.clickable(
            onClickLabel = checkConnectionLabel,
            onClick = { onAction(MainAction.TestCurrentServer) },
        )
    } else {
        Modifier
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibilityText
                }
                .then(connectionActionModifier)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            AppDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clearAndSetSemantics {
                        if (exposeTestResult) {
                            text = resultText
                        } else {
                            hideFromAccessibility()
                        }
                    }
                )
            }
        }
        AccessibilityLiveRegionText(
            eventId = testAnnouncement?.id,
            text = testAnnouncement?.status?.let(formatTestAnnouncement).orEmpty(),
            mode = LiveRegionMode.Assertive,
            onPublished = { publishedId = it },
        )
        FloatingActionButton(
            onClick = { onAction(MainAction.ToggleService) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 24.dp)
                .offset(y = (-28).dp)
                .navigationBarsPadding(),
            containerColor = if (isRunning) colorFabActive
            else if (isDarkTheme) colorFabInactiveDark
            else colorFabInactiveLight
        ) {
            Icon(
                painter = if (isRunning) painterResource(R.drawable.ic_stop_24dp)
                else painterResource(R.drawable.ic_play_24dp),
                contentDescription = stringResource(
                    if (isRunning) R.string.acc_stop else R.string.acc_start
                ),
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun localizedTestText(text: String): AnnotatedString {
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    return remember(text, languageTag) {
        buildAnnotatedString {
            withStyle(SpanStyle(localeList = LocaleList(Locale(languageTag)))) {
                append(text)
            }
        }
    }
}

private const val TestLiveRegionLifetimeMs = 1000L
