package com.v2ray.ang.ui.compose

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.extension.AccessibilityLiveRegionMode
import com.v2ray.ang.extension.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

enum class ToastType {
    NORMAL, SUCCESS, ERROR, INFO
}

data class AppSnackbarMessage(
    val message: CharSequence,
    val type: ToastType = ToastType.NORMAL,
    val long: Boolean = false,
    val liveRegionMode: AccessibilityLiveRegionMode? = null,
)

object AppSnackbarManager {
    private val _messages = MutableSharedFlow<AppSnackbarMessage>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages = _messages.asSharedFlow()

    fun hasActiveHost(): Boolean = _messages.subscriptionCount.value > 0

    fun show(message: AppSnackbarMessage): Boolean {
        if (!hasActiveHost()) return false
        return _messages.tryEmit(message)
    }
}

class AppSnackbarController(
    val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    private var currentId = 0
    private var currentShowTime = 0L

    fun show(message: CharSequence, type: ToastType = ToastType.NORMAL, long: Boolean = false) {
        val id = ++currentId
        scope.launch {
            if (currentShowTime != 0L) {
                val elapsed = System.currentTimeMillis() - currentShowTime
                if (elapsed < SnackbarThrottleMs) {
                    delay((SnackbarThrottleMs - elapsed))
                }
            }

            hostState.currentSnackbarData?.dismiss()

            launch {
                hostState.showSnackbar(
                    AppSnackbarVisuals(
                        message = message.toString(),
                        type = type,
                        duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short
                    )
                )
                if (id == currentId) {
                    currentShowTime = 0L
                }
            }

            currentShowTime = System.currentTimeMillis()
        }
    }
}

private data class AppSnackbarVisuals(
    override val message: String,
    val type: ToastType,
    override val duration: SnackbarDuration,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false
) : SnackbarVisuals

val LocalAppSnackbar = staticCompositionLocalOf<AppSnackbarController> {
    error("AppSnackbarController not provided. Wrap your content in AppTheme.")
}

@Composable
fun rememberAppSnackbarController(): AppSnackbarController {
    val hostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    return remember(hostState, scope) { AppSnackbarController(hostState, scope) }
}

@Composable
fun AppSnackbarBridge(
    controller: AppSnackbarController
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val liveRegionMessageTracker = remember { LiveRegionMessageTracker() }
    var liveRegionMessage by remember { mutableStateOf<LiveRegionMessage?>(null) }

    LaunchedEffect(controller, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            AppSnackbarManager.messages.collect { event ->
                controller.show(
                    message = event.message,
                    type = event.type,
                    long = event.long
                )
                liveRegionMessageTracker.next(event)?.let { liveRegionMessage = it }
            }
        }
    }

    LaunchedEffect(liveRegionMessage?.id) {
        val id = liveRegionMessage?.id ?: return@LaunchedEffect
        delay(LiveRegionMessageLifetimeMs)
        if (liveRegionMessage?.id == id) liveRegionMessage = null
    }

    AccessibilityLiveRegion(liveRegionMessage)
}

internal data class LiveRegionMessage(
    val id: Long,
    val text: String,
    val mode: AccessibilityLiveRegionMode,
)

internal class LiveRegionMessageTracker(
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    private var nextId = 0L
    private var lastMessage: String? = null
    private var lastMode: AccessibilityLiveRegionMode? = null
    private var lastUpdateAt = 0L

    fun next(event: AppSnackbarMessage): LiveRegionMessage? {
        val mode = event.liveRegionMode ?: return null
        val text = event.message.toString()
        if (text.isBlank()) return null

        val now = elapsedRealtime()
        val duplicate = text == lastMessage && mode == lastMode &&
            now - lastUpdateAt < DuplicateLiveRegionMessageWindowMs
        if (duplicate) return null

        lastMessage = text
        lastMode = mode
        lastUpdateAt = now
        return LiveRegionMessage(
            id = ++nextId,
            text = text,
            mode = mode,
        )
    }
}

@Composable
private fun AccessibilityLiveRegion(message: LiveRegionMessage?) {
    val text = message?.text.orEmpty()
    val liveRegionMode = when (message?.mode) {
        AccessibilityLiveRegionMode.ASSERTIVE -> LiveRegionMode.Assertive
        AccessibilityLiveRegionMode.POLITE, null -> LiveRegionMode.Polite
    }

    var armed by remember { mutableStateOf(false) }
    var announcedText by remember { mutableStateOf("") }
    var announcedMode by remember { mutableStateOf(liveRegionMode) }

    LaunchedEffect(message?.id, text, liveRegionMode) {
        if (message == null || text.isEmpty()) {
            armed = false
            announcedText = ""
            return@LaunchedEffect
        }

        announcedMode = liveRegionMode
        announcedText = ""
        armed = true

        // First expose the empty live region, then publish its text after that semantics state has
        // reached Android. This creates a genuine text change after other controls have settled
        // without leaving an idle accessibility node.
        withFrameNanos { }
        withFrameNanos { }
        announcedText = text
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

private val ToastCornerRadius = 24.dp
private val ToastHorizontalPad = 16.dp
private val ToastVerticalPad = 12.dp
private const val ToastMaxLines = 8
private const val ToastMaxWidthFraction = 0.75f
private val ToastBottomOffset = 100.dp
private const val SnackbarThrottleMs = 2000L
internal const val DuplicateLiveRegionMessageWindowMs = 1000L
private const val LiveRegionMessageLifetimeMs = 1000L

@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.clearAndSetSemantics { }) {
        val maxSnackbarWidth = maxWidth * ToastMaxWidthFraction
        val density = LocalDensity.current
        val navigationBarHeight = with(density) {
            WindowInsets.navigationBars.getBottom(this).toDp()
        }

        SnackbarHost(
            hostState = hostState,
            modifier = Modifier.fillMaxSize()
        ) { data ->
            val type = (data.visuals as? AppSnackbarVisuals)?.type ?: ToastType.NORMAL

            val isDark = LocalDarkTheme.current
            val bgColor = when (type) {
                ToastType.NORMAL -> if (isDark) toastNormalBgDark else toastNormalBgLight
                ToastType.SUCCESS -> toastSuccessBg
                ToastType.ERROR -> toastErrorBg
                ToastType.INFO -> toastInfoBg
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = ToastBottomOffset + navigationBarHeight),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier
                        .wrapContentWidth()
                        .widthIn(max = maxSnackbarWidth),
                    shape = RoundedCornerShape(ToastCornerRadius),
                    color = bgColor,
                    shadowElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = ToastHorizontalPad,
                            vertical = ToastVerticalPad
                        ),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = data.visuals.message,
                            color = toastTextColor,
                            fontSize = 14.sp,
                            maxLines = ToastMaxLines,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.wrapContentWidth()
                        )
                    }
                }
            }
        }
    }
}
