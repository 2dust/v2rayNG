package com.v2ray.ang.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.extension.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class ToastType { NORMAL, SUCCESS, ERROR, INFO }

@Immutable
data class AppSnackbarMessage(
    val message: String,
    val type: ToastType = ToastType.NORMAL,
    val long: Boolean = false
)

object AppSnackbarManager {
    private val messageFlow = MutableSharedFlow<AppSnackbarMessage>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages = messageFlow.asSharedFlow()

    fun hasActiveHost(): Boolean = messageFlow.subscriptionCount.value > 0

    fun show(
        message: CharSequence,
        type: ToastType = ToastType.NORMAL,
        long: Boolean = false
    ): Boolean {
        if (!hasActiveHost()) return false
        return messageFlow.tryEmit(AppSnackbarMessage(message.toString(), type, long))
    }
}

private const val QueueCapacity = 8
private val MinVisibleDuration = 2000

@Stable
class AppSnackbarController internal constructor(val hostState: SnackbarHostState) {

    private val queue = Channel<AppSnackbarMessage>(
        capacity = QueueCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    fun show(message: CharSequence, type: ToastType = ToastType.NORMAL, long: Boolean = false) {
        queue.trySend(AppSnackbarMessage(message.toString(), type, long))
    }

    internal suspend fun consume(): Unit = coroutineScope {
        for (message in queue) {
            hostState.currentSnackbarData?.dismiss()
            launch {
                hostState.showSnackbar(
                    AppSnackbarVisuals(
                        message = message.message,
                        type = message.type,
                        duration = if (message.long) SnackbarDuration.Long else SnackbarDuration.Short
                    )
                )
            }
            delay(MinVisibleDuration.toLong())
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
    return remember(hostState) { AppSnackbarController(hostState) }
}

@Composable
fun AppSnackbarBridge(controller: AppSnackbarController) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(controller) { controller.consume() }
    LaunchedEffect(controller, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            AppSnackbarManager.messages.collect { controller.show(it.message, it.type, it.long) }
        }
    }
}

private val ToastCornerRadius = 24.dp
private val ToastHorizontalPad = 16.dp
private val ToastVerticalPad = 12.dp
private val ToastBottomOffset = 100.dp
private const val ToastMaxLines = 8
private const val ToastMaxWidthFraction = 0.75f

private fun Modifier.maxWidthFraction(fraction: Float) = layout { measurable, constraints ->
    val cap = (constraints.maxWidth * fraction).roundToInt().coerceAtLeast(0)
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = cap))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

@Composable
fun AppSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier.fillMaxSize()) { data ->
        val colors = LocalAppColors.current
        val background = when ((data.visuals as? AppSnackbarVisuals)?.type ?: ToastType.NORMAL) {
            ToastType.NORMAL -> colors.toastBackground
            ToastType.SUCCESS -> colors.toastSuccess
            ToastType.ERROR -> colors.toastError
            ToastType.INFO -> colors.toastInfo
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = ToastBottomOffset),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .maxWidthFraction(ToastMaxWidthFraction)
                    .wrapContentWidth(),
                shape = RoundedCornerShape(ToastCornerRadius),
                color = background,
                shadowElevation = 0.dp
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
                        color = colors.toastContent,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = ToastMaxLines,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
