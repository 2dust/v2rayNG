package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.ui.compose.AccessibilityLiveRegionText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

private const val TestLiveRegionLifetimeMs = 1000L

@Composable
internal fun MainTestFeedback(
    connectionText: String,
    status: MainStatus,
    isRunning: Boolean,
    testAnnouncements: Flow<MainTestAnnouncement?>,
    formatTestAnnouncement: (MainStatus) -> String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(AnnotatedString) -> Unit,
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
        // Hold each message in the semantics tree before publishing the next one.
        delay(TestLiveRegionLifetimeMs)
        announcements.advance(id)
    }
    val result = announcements.connectionResultForAccessibility(isRunning, status)
    val statusText = localizedTestText(
        result?.let { "$connectionText. ${formatTestAnnouncement(it)}" } ?: connectionText
    )

    Box(modifier) {
        content(statusText)
        AccessibilityLiveRegionText(
            eventId = testAnnouncement?.id,
            text = testAnnouncement?.status?.let(formatTestAnnouncement).orEmpty(),
            mode = LiveRegionMode.Assertive,
            onPublished = { publishedId = it },
        )
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

/** Transient presentation only. Durable test results remain in MainUiState. */
internal class MainTestAnnouncements {
    var current by mutableStateOf<MainTestAnnouncement?>(null)
        private set
    private val pending = ArrayDeque<MainTestAnnouncement>()

    fun connectionResultForAccessibility(isRunning: Boolean, status: MainStatus): MainStatus.ConnectionTest? {
        // The live region owns the result until the announcement queue drains.
        return (status as? MainStatus.ConnectionTest)?.takeIf { isRunning && it.result.isFinal && current == null }
    }

    fun offer(event: MainTestAnnouncement?) {
        if (event == null) {
            clear()
        } else if (current == null) {
            current = event
        } else {
            pending.addLast(event)
        }
    }

    fun advance(id: Long) {
        if (current?.id == id) current = pending.removeFirstOrNull()
    }

    fun clear() {
        current = null
        pending.clear()
    }
}
