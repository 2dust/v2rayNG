package com.v2ray.ang.compose

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

enum class ToastType {
    NORMAL, SUCCESS, ERROR, INFO
}

data class AppSnackbarMessage(
    val message: CharSequence,
    val type: ToastType = ToastType.NORMAL,
    val long: Boolean = false,
)

object AppSnackbarManager {
    private val activeHosts = AtomicInteger(0)

    private val _messages = MutableSharedFlow<AppSnackbarMessage>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages = _messages.asSharedFlow()

    fun registerHost() {
        activeHosts.incrementAndGet()
    }

    fun unregisterHost() {
        val current = activeHosts.decrementAndGet()
        if (current < 0) activeHosts.set(0)
    }

    fun hasActiveHost(): Boolean = activeHosts.get() > 0

    fun show(
        message: CharSequence,
        type: ToastType = ToastType.NORMAL,
        long: Boolean = false,
    ): Boolean {
        if (!hasActiveHost()) return false
        return _messages.tryEmit(
            AppSnackbarMessage(
                message = message,
                type = type,
                long = long
            )
        )
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
                if (elapsed < 500) {
                    delay(500 - elapsed)
                }
            }

             hostState.currentSnackbarData?.dismiss()

            launch {
                hostState.showSnackbar(
                    message = message.toString(),
                    actionLabel = type.name,
                    duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short,
                    withDismissAction = false,
                )
                if (id == currentId) {
                    currentShowTime = 0L
                }
            }

            currentShowTime = System.currentTimeMillis()
         }
     }

    fun showInfo(context: Context, @StringRes messageRes: Int, long: Boolean = false) {
        show(context.getString(messageRes), ToastType.NORMAL, long)
    }

    fun showInfo(message: CharSequence, long: Boolean = false) {
        show(message, ToastType.NORMAL, long)
    }

    fun showSuccess(context: Context, @StringRes messageRes: Int, long: Boolean = false) {
        show(context.getString(messageRes), ToastType.SUCCESS, long)
    }

    fun showSuccess(message: CharSequence, long: Boolean = false) {
        show(message, ToastType.SUCCESS, long)
    }

    fun showError(context: Context, @StringRes messageRes: Int, long: Boolean = false) {
        show(context.getString(messageRes), ToastType.ERROR, long)
    }

    fun showError(message: CharSequence, long: Boolean = false) {
        show(message, ToastType.ERROR, long)
    }
}

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
    DisposableEffect(Unit) {
        AppSnackbarManager.registerHost()
        onDispose { AppSnackbarManager.unregisterHost() }
    }

    LaunchedEffect(controller) {
        AppSnackbarManager.messages.collect { event ->
            controller.show(
                message = event.message,
                type = event.type,
                long = event.long
            )
        }
    }
}

private val ToastCornerRadius = 24.dp
private val ToastHorizontalPad = 16.dp
private val ToastVerticalPad = 12.dp
private val ToastIconSize = 24.dp
private val ToastIconSpacing = 10.dp
private val ToastMaxLines = 8
private val ToastMaxWidthFraction = 0.75f
private val ToastBottomOffset = 100.dp

@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val maxSnackbarWidth = maxWidth * ToastMaxWidthFraction
        val density = LocalDensity.current
        val navigationBarHeight = with(density) {
            WindowInsets.navigationBars.getBottom(this).toDp()
        }

        SnackbarHost(
            hostState = hostState,
            modifier = Modifier.fillMaxSize()
        ) { data ->
            val type = runCatching {
                ToastType.valueOf(data.visuals.actionLabel.orEmpty())
            }.getOrDefault(ToastType.NORMAL)

            val isDark = LocalDarkTheme.current
            val bgColor = when (type) {
                ToastType.NORMAL -> if (isDark) toastNormalBgDark else toastNormalBgLight
                ToastType.SUCCESS -> toastSuccessBg
                ToastType.ERROR -> toastErrorBg
                ToastType.INFO -> toastInfoBg
            }

            val iconText = when (type) {
                ToastType.SUCCESS -> "✓"
                ToastType.ERROR -> "✕"
                ToastType.INFO -> "ℹ"
                ToastType.NORMAL -> null
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
                        if (iconText != null) {
                            Box(
                                modifier = Modifier
                                    .size(ToastIconSize)
                                    .clip(CircleShape)
                                    .background(toastIconCircleBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = iconText,
                                    color = toastTextColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(ToastIconSpacing))
                        }

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
