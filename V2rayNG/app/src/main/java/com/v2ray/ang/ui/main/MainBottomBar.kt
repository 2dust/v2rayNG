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
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.colorFabActive
import com.v2ray.ang.ui.compose.colorFabInactiveDark
import com.v2ray.ang.ui.compose.colorFabInactiveLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

@Composable
fun MainBottomBar(
    displayText: String,
    accessibilityText: String,
    testAnnouncements: Flow<MainTestAnnouncement>,
    formatTestAnnouncement: (MainTestAnnouncement) -> String,
    isRunning: Boolean,
    isDarkTheme: Boolean,
    onAction: (MainAction) -> Unit
) {
    var testAnnouncement by remember { mutableStateOf<MainTestAnnouncement?>(null) }

    LaunchedEffect(testAnnouncements) {
        testAnnouncements.collect { testAnnouncement = it }
    }

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
                    modifier = Modifier.semantics { hideFromAccessibility() }
                )
            }
        }
        AssertiveTestLiveRegion(
            eventId = testAnnouncement?.id,
            text = testAnnouncement?.let(formatTestAnnouncement).orEmpty(),
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
private fun AssertiveTestLiveRegion(
    eventId: Long?,
    text: String,
) {
    var armed by remember { mutableStateOf(false) }
    var announcedText by remember { mutableStateOf("") }

    LaunchedEffect(eventId, text) {
        if (eventId == null || text.isBlank()) {
            armed = false
            announcedText = ""
            return@LaunchedEffect
        }

        announcedText = ""
        armed = true

        // Establish the live region before publishing its message, then hide it after the
        // announcement so it never remains as an empty navigation target.
        withFrameNanos { }
        withFrameNanos { }
        announcedText = text
        delay(TestLiveRegionLifetimeMs)
        armed = false
        announcedText = ""
    }

    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val localizedText = remember(announcedText, languageTag) {
        buildAnnotatedString {
            withStyle(
                SpanStyle(localeList = LocaleList(Locale(languageTag)))
            ) {
                append(announcedText)
            }
        }
    }

    Text(
        text = localizedText,
        color = Color.Transparent,
        fontSize = 1.sp,
        maxLines = 1,
        modifier = Modifier
            .size(1.dp)
            .clearAndSetSemantics {
                if (!armed) {
                    hideFromAccessibility()
                } else {
                    liveRegion = LiveRegionMode.Assertive
                    if (localizedText.isNotEmpty()) this.text = localizedText
                }
            },
    )
}

private const val TestLiveRegionLifetimeMs = 1000L
