package com.v2ray.ang.ui.compose

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
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
internal fun Modifier.reorderableDragHandle(scope: ReorderableCollectionItemScope): Modifier {
    val hapticFeedback = LocalHapticFeedback.current
    return with(scope) {
        longPressDraggableHandle(onDragStarted = {
            // Platform haptics honor the user's touch-feedback setting.
            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        })
    }
}

@Composable
private fun ReorderableSurface(
    scope: ReorderableCollectionItemScope,
    isDragging: Boolean,
    isMoving: Boolean,
    moveModeCornerRadius: Dp,
    content: @Composable () -> Unit
) {
    val elevation by reorderableElevation(isDragging, isMoving)
    val shape = if (isMoving) RoundedCornerShape(moveModeCornerRadius) else RectangleShape
    Box(Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth().reorderableDragHandle(scope),
            shape = shape,
            color = if (isMoving) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            shadowElevation = elevation,
            tonalElevation = if (isMoving) 8.dp else 0.dp
        ) { content() }
        if (isMoving) Box(Modifier.matchParentSize().border(4.dp, MaterialTheme.colorScheme.secondary, shape))
    }
}

@Composable
internal fun ReorderableListItem(
    scope: ReorderableCollectionItemScope,
    isDragging: Boolean,
    isMoving: Boolean = false,
    moveModeCornerRadius: Dp = 16.dp,
    content: @Composable RowScope.() -> Unit
) {
    ReorderableSurface(scope, isDragging, isMoving, moveModeCornerRadius) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
internal fun ReorderableGridItem(
    scope: ReorderableCollectionItemScope,
    isDragging: Boolean,
    isMoving: Boolean = false,
    moveModeCornerRadius: Dp = 16.dp,
    content: @Composable () -> Unit
) = ReorderableSurface(scope, isDragging, isMoving, moveModeCornerRadius, content)
