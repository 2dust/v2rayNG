package com.v2ray.ang.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.extension.delay
import com.v2ray.ang.handler.AppNotificationData
import com.v2ray.ang.handler.AppNotificationManager
import com.v2ray.ang.handler.AppNotificationType

val LocalAppNotification = staticCompositionLocalOf<AppNotificationManager> {
    AppNotificationManager
}

private const val MaxNotificationQueueSize = 8
private const val InterNotificationDelayMs = 250L

private enum class InternalNotificationPosition {
    BOTTOM, TOP, CENTER
}

// Single internal variable to control global popup position (BOTTOM, TOP, or CENTER)
private val GlobalNotificationPosition = InternalNotificationPosition.BOTTOM

@Composable
fun AppNotificationBridge() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val queue = remember { mutableStateListOf<AppNotificationData>() }
    var currentData by remember { mutableStateOf<AppNotificationData?>(null) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            AppNotificationManager.notifications.collect { data ->
                if (queue.size >= MaxNotificationQueueSize) {
                    queue.removeAt(0)
                }
                queue.add(data)
            }
        }
    }

    LaunchedEffect(queue.size, currentData) {
        if (currentData == null && queue.isNotEmpty()) {
            val nextData = queue.removeAt(0)
            currentData = nextData
        }
    }

    LaunchedEffect(currentData) {
        if (currentData != null) {
            delay(currentData!!.durationMs)
            currentData = null
            delay(InterNotificationDelayMs)
        }
    }

    AppNotificationHost(
        data = currentData,
        onDismiss = { currentData = null }
    )
}

// Standard TopAppBar height (56.dp) + 8.dp padding below TopAppBar to prevent blocking back button
private val NotificationTopOffset = 64.dp
// Bottom offset (80.dp) to elevate card above navigation bar and bottom action bars
private val NotificationBottomOffset = 80.dp

// Notification Colors
private val notificationNormalBgLight = Color(0xD9353A3E)
private val notificationNormalBgDark = Color(0xD94A4F54)
private val notificationSuccessBg = Color(0xD9388E3C)
private val notificationErrorBg = Color(0xD9D50000)
private val notificationInfoBg = Color(0xD93F51B5)

@Composable
fun AppNotificationHost(
    data: AppNotificationData?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().zIndex(999f)) {
        val maxNotificationCardHeight = maxHeight * 0.6f
        val maxTextScrollHeight = maxHeight * 0.5f
        val density = LocalDensity.current
        val statusBarHeight = with(density) {
            WindowInsets.statusBars.getTop(this).toDp()
        }
        val navigationBarHeight = with(density) {
            WindowInsets.navigationBars.getBottom(this).toDp()
        }

        val targetPosition = GlobalNotificationPosition
        val alignment = when (targetPosition) {
            InternalNotificationPosition.CENTER -> Alignment.Center
            InternalNotificationPosition.TOP -> Alignment.TopCenter
            InternalNotificationPosition.BOTTOM -> Alignment.BottomCenter
        }

        val containerPadding = when (targetPosition) {
            InternalNotificationPosition.TOP -> Modifier.padding(top = NotificationTopOffset + statusBarHeight)
            InternalNotificationPosition.BOTTOM -> Modifier.padding(bottom = NotificationBottomOffset + navigationBarHeight)
            InternalNotificationPosition.CENTER -> Modifier.padding(horizontal = 16.dp)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(containerPadding),
            contentAlignment = alignment
        ) {
            AnimatedVisibility(
                visible = data != null,
                enter = when (targetPosition) {
                    InternalNotificationPosition.CENTER -> scaleIn(initialScale = 0.85f) + fadeIn()
                    InternalNotificationPosition.TOP -> slideInVertically(initialOffsetY = { -it }) + fadeIn()
                    InternalNotificationPosition.BOTTOM -> slideInVertically(initialOffsetY = { it }) + fadeIn()
                },
                exit = when (targetPosition) {
                    InternalNotificationPosition.CENTER -> scaleOut(targetScale = 0.85f) + fadeOut()
                    InternalNotificationPosition.TOP -> slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                    InternalNotificationPosition.BOTTOM -> slideOutVertically(targetOffsetY = { it }) + fadeOut()
                }
            ) {
                if (data != null) {
                    val isDark = LocalDarkTheme.current
                    val bgColor = when (data.type) {
                        AppNotificationType.NORMAL -> if (isDark) notificationNormalBgDark else notificationNormalBgLight
                        AppNotificationType.SUCCESS -> notificationSuccessBg
                        AppNotificationType.ERROR -> notificationErrorBg
                        AppNotificationType.INFO -> notificationInfoBg
                    }

                    val textScrollState = rememberScrollState()
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .widthIn(max = 420.dp)
                            .heightIn(max = maxNotificationCardHeight)
                            .clickable { onDismiss() },
                        shape = RoundedCornerShape(20.dp),
                        color = bgColor,
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(max = maxTextScrollHeight)
                                    .verticalScroll(textScrollState)
                            ) {
                                Text(
                                    text = data.message.toString(),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 14.sp,
                                        lineHeight = 19.sp
                                    ),
                                    color = Color.White,
                                    maxLines = 50,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Clip
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            CloseButton { onDismiss() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    val iconColor = Color.White.copy(alpha = 0.8f)
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(10.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 1.8.dp.toPx()
            drawLine(
                color = iconColor,
                start = Offset(0f, 0f),
                end = Offset(w, h),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = iconColor,
                start = Offset(w, 0f),
                end = Offset(0f, h),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}
