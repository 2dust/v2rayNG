package com.v2ray.ang.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.RoundedCornerShape
import com.v2ray.ang.ui.compose.PrimaryGradient
import com.v2ray.ang.ui.compose.liquidGlass
import com.v2ray.ang.ui.compose.softGlow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import kotlinx.coroutines.launch

@Composable
fun MainBottomBar(
    displayText: String,
    isRunning: Boolean,
    isDarkTheme: Boolean,
    onAction: (MainAction) -> Unit
) {
    val scope = rememberCoroutineScope()
    val rotationAnim = remember { Animatable(0f) }

    LaunchedEffect(isRunning) {
        if (!isRunning) {
            rotationAnim.snapTo(0f)
        }
    }

    val barShape = RoundedCornerShape(50)
    val buttonShape = RoundedCornerShape(50)
    val buttonBrush = if (isRunning) PrimaryGradient else Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.surfaceContainerHigh)
    )
    val startLabel = stringResource(R.string.acc_start)
    val stopLabel = stringResource(R.string.acc_stop)

    // Liquid-glass dock: the connection status on one side, the glowing connect button on the other.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .liquidGlass(barShape)
            .clickable(onClick = { onAction(MainAction.TestCurrentServer) })
            .padding(start = 20.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = displayText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = displayText }
        )
        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .size(56.dp)
                .then(if (isRunning) Modifier.softGlow(MaterialTheme.colorScheme.primary, 18.dp, 28.dp) else Modifier)
                .clip(buttonShape)
                .background(buttonBrush)
                .clickable(role = Role.Button) {
                    if (!isRunning) {
                        scope.launch {
                            rotationAnim.animateTo(
                                targetValue = 720f,
                                animationSpec = tween(durationMillis = 3000)
                            )
                        }
                    }
                    onAction(MainAction.ToggleService)
                }
                .semantics { contentDescription = if (isRunning) stopLabel else startLabel },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = if (isRunning) painterResource(R.drawable.ic_stop_24dp)
                else painterResource(R.drawable.ic_play_24dp),
                contentDescription = null,
                tint = if (isRunning) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(26.dp)
                    .graphicsLayer { rotationZ = rotationAnim.value }
            )
        }
    }
}
