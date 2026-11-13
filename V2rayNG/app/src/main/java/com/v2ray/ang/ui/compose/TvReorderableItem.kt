package com.v2ray.ang.ui.compose

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableCollectionItemScope

@Composable
private fun reorderableElevation(isDragging: Boolean, isMoving: Boolean) = animateDpAsState(
    targetValue = when {
        isMoving -> 16.dp
        isDragging -> 4.dp
        else -> 0.dp
    },
    label = "ReorderableElevation"
)

@Composable
private fun Modifier.tvReorderableDragHandle(scope: ReorderableCollectionItemScope): Modifier {
    val hapticFeedback = LocalHapticFeedback.current
    return with(scope) {
        longPressDraggableHandle(onDragStarted = {
            // Platform haptics honor the user's touch-feedback setting.
            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        })
    }
}

@Composable
internal fun TvReorderableItem(
    scope: ReorderableCollectionItemScope,
    isDragging: Boolean,
    isMoving: Boolean,
    content: @Composable () -> Unit
) {
    val elevation by reorderableElevation(isDragging, isMoving)
    val shape = if (isMoving) RoundedCornerShape(12.dp) else RectangleShape
    Box(Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth().tvReorderableDragHandle(scope),
            shape = shape,
            color = if (isMoving) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            shadowElevation = elevation,
            tonalElevation = if (isMoving) 8.dp else 0.dp
        ) { content() }
        if (isMoving) Box(Modifier.matchParentSize().border(4.dp, MaterialTheme.colorScheme.secondary, shape))
    }
}
