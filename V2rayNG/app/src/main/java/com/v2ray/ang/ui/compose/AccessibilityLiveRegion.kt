package com.v2ray.ang.ui.compose

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AccessibilityLiveRegionText(
    eventId: Long?,
    text: String,
    mode: LiveRegionMode,
    onPublished: ((Long) -> Unit)? = null,
) {
    var armed by remember { mutableStateOf(false) }
    var announcedText by remember { mutableStateOf("") }
    var announcedMode by remember { mutableStateOf(mode) }
    val publicationCallback by rememberUpdatedState(onPublished)

    LaunchedEffect(eventId, text, mode) {
        if (eventId == null || text.isEmpty()) {
            armed = false
            announcedText = ""
            return@LaunchedEffect
        }

        announcedMode = mode
        announcedText = ""
        armed = true

        // First expose the empty live region, then publish its text after that semantics state has
        // reached Android. This creates a genuine text change after other controls have settled
        // without leaving an idle accessibility node.
        withFrameNanos { }
        withFrameNanos { }
        announcedText = text
        if (publicationCallback != null) {
            // Let the nonempty semantics reach a frame before the owner starts its hold time.
            withFrameNanos { }
            withFrameNanos { }
            publicationCallback?.invoke(eventId)
        }
    }

    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val localizedText = remember(announcedText, languageTag) {
        buildAnnotatedString {
            withStyle(SpanStyle(localeList = LocaleList(Locale(languageTag)))) {
                append(announcedText)
            }
        }
    }

    // Keep the underlying layout node stable, but hide it from navigation between messages.
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
                    liveRegion = announcedMode
                    if (localizedText.isNotEmpty()) this.text = localizedText
                }
            },
    )
}
