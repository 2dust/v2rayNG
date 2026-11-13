package com.v2ray.ang.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val TvFocusAttachmentRetryFrames = 30

/** Retain focus identity when siblings are inserted, removed, filtered, or reordered. */
internal class DpadFocusTargets<K, T> {
    private val targets = mutableMapOf<K, T>()

    fun snapshot(keys: List<K>, create: () -> T): Map<K, T> {
        targets.keys.retainAll(keys.toSet())
        return keys.associateWith { targets.getOrPut(it, create) }
    }
}

@Composable
internal fun <K, T> rememberDpadFocusTargets(keys: List<K>, create: () -> T): Map<K, T> {
    val store = remember { DpadFocusTargets<K, T>() }
    return remember(keys) { store.snapshot(keys, create) }
}

/**
 * A lazy focus chain owns its item-to-target mapping, including headers. Attached targets take
 * the immediate path; detached ones must be scrolled into composition before attachment retries.
 * Keeping the latest mapping also makes restored handles follow their item after reordering.
 */
@Composable
internal fun rememberLazyDpadFocus(
    itemTargets: List<List<FocusRequester>>,
    scrollToItem: suspend (Int) -> Unit
): (FocusRequester) -> Boolean {
    val scope = rememberCoroutineScope()
    val indices = rememberUpdatedState(remember(itemTargets) {
        buildMap { itemTargets.forEachIndexed { index, targets -> targets.forEach { put(it, index) } } }
    })
    val scroll = rememberUpdatedState(scrollToItem)
    return remember {
        var pendingRequest: Job? = null
        { target: FocusRequester ->
            pendingRequest?.cancel()
            if (target.requestFocus()) true
            else if (target !in indices.value) false
            else {
                pendingRequest = scope.launch {
                    val index = indices.value[target] ?: return@launch
                    scroll.value(index)
                    requestFocusWhenReady(target)
                }
                true
            }
        }
    }
}

/**
 * Focus nodes in lazy lists, dialogs, drawers, and popup menus can be attached several frames
 * after their surrounding composition. The immediate attempt keeps ordinary navigation fast;
 * the bounded frame retries recover only while that real focus target is being attached.
 *
 * This is deliberately frame-based rather than a fixed delay: fixed delays made drawer focus
 * feel sluggish and were still unreliable under a busy TV renderer. Thirty frames is a generous
 * upper bound, not animation timing. Do not remove or shorten it without testing newly composed
 * lazy-list items and popup focus on a real/emulated TV.
 */
internal suspend fun requestFocusWhenReady(vararg requesters: FocusRequester): Boolean {
    repeat(TvFocusAttachmentRetryFrames) {
        if (requesters.any { it.requestFocus() }) return true
        withFrameNanos { }
    }
    return false
}

/**
 * Adds a TV-style focus treatment while keeping the component's existing click/focus node.
 * Unfocused controls are left completely untouched; focused controls receive a restrained
 * tonal lift and thin accent edge that read clearly at TV distance without changing layout size.
 * The same rounded shape clips descendant indications so Material focus/ripple layers cannot
 * escape the border as square corners.
 * Apply this before clickable/focusable modifiers so it observes their focus state.
 */
@Composable
internal fun Modifier.dpadFocusOutline(
    focusRequester: FocusRequester? = null,
    cornerRadius: Dp = 12.dp,
    focusContainerColor: Color? = null,
    showFocus: Boolean = true
): Modifier {

    val requesterModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }
    if (!showFocus) return then(requesterModifier)

    var isFocused by remember { mutableStateOf(false) }
    val focusColor = MaterialTheme.colorScheme.primary
    val resolvedFocusContainerColor = focusContainerColor ?: MaterialTheme.colorScheme.primaryContainer
    val shape = RoundedCornerShape(cornerRadius)
    val focusDecoration = if (isFocused) {
        Modifier
            .background(resolvedFocusContainerColor, shape)
            .border(width = 2.dp, color = focusColor, shape = shape)
    } else {
        Modifier
    }

    return this
        .then(requesterModifier)
        .onFocusChanged { isFocused = it.isFocused }
        .then(focusDecoration)
        .clip(shape)
}

@Composable
internal fun Modifier.dpadClickable(enabled: Boolean = true, role: Role? = null, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(interactionSource = interactionSource, indication = null, enabled = enabled, role = role, onClick = onClick)
}

internal fun Key.isDpadActivationKey(): Boolean =
    this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter || this == Key.Spacebar

internal enum class DpadHorizontalDirection {
    Previous,
    Next
}

private fun logicalHorizontalDirection(key: Key, isRtl: Boolean): DpadHorizontalDirection? = when (key) {
    Key.DirectionLeft -> if (isRtl) {
        DpadHorizontalDirection.Next
    } else {
        DpadHorizontalDirection.Previous
    }

    Key.DirectionRight -> if (isRtl) {
        DpadHorizontalDirection.Previous
    } else {
        DpadHorizontalDirection.Next
    }

    else -> null
}

private fun adjacentDpadFocusIndex(currentIndex: Int, itemCount: Int, direction: DpadHorizontalDirection): Int? {
    if (currentIndex !in 0 until itemCount) return null
    val targetIndex = when (direction) {
        DpadHorizontalDirection.Previous -> currentIndex - 1
        DpadHorizontalDirection.Next -> currentIndex + 1
    }
    return targetIndex.takeIf { it in 0 until itemCount }
}

internal fun adjacentDpadFocusTarget(
    current: FocusRequester,
    order: List<FocusRequester>,
    direction: DpadHorizontalDirection
): FocusRequester? {
    val currentIndex = order.indexOfFirst { it === current }
    return adjacentDpadFocusIndex(currentIndex, order.size, direction)?.let(order::get)
}

/**
 * Handles horizontal TV navigation in logical composition order. Previous is physically Left in
 * LTR and Right in RTL; Next is the opposite. Callers must never mirror these callbacks themselves.
 */
@Composable
internal fun Modifier.dpadLogicalHorizontalNavigation(onMovePrevious: () -> Unit, onMoveNext: () -> Unit): Modifier {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        when (logicalHorizontalDirection(event.key, isRtl)) {
            DpadHorizontalDirection.Previous -> {
                onMovePrevious()
                true
            }

            DpadHorizontalDirection.Next -> {
                onMoveNext()
                true
            }

            null -> false
        }
    }
}

/**
 * Moves within one ordered TV focus chain. A row or toolbar supplies its targets once in logical
 * composition order; individual controls no longer wire their left/right neighbors pair-by-pair.
 * RTL conversion stays exclusively in [dpadLogicalHorizontalNavigation].
 */
@Composable
internal fun Modifier.dpadOrderedFocusNavigation(
    current: FocusRequester,
    order: List<FocusRequester>,
    onBeforeFirst: (() -> Unit)? = null,
    onAfterLast: (() -> Unit)? = null,
    requestFocus: (FocusRequester) -> Boolean = { it.requestFocus() }
): Modifier {
    fun move(direction: DpadHorizontalDirection, onEdge: (() -> Unit)?) {
        val target = adjacentDpadFocusTarget(current, order, direction)
        if (target == null) {
            onEdge?.invoke() ?: current.requestFocus()
        } else {
            requestFocus(target)
        }
    }

    return dpadLogicalHorizontalNavigation(
        onMovePrevious = {
            move(DpadHorizontalDirection.Previous, onBeforeFirst)
        },
        onMoveNext = {
            move(DpadHorizontalDirection.Next, onAfterLast)
        }
    )
}

/**
 * Handles horizontal navigation inside menus and dialogs before controls such as text fields
 * consume the D-pad event. Unspecified directions keep their default behavior.
 */
@Composable
internal fun Modifier.dpadPopupHorizontalNavigation(
    onMovePrevious: (() -> Unit)? = null, onMoveNext: (() -> Unit)? = null
): Modifier {
    if (onMovePrevious == null && onMoveNext == null) return this
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val move = when (logicalHorizontalDirection(event.key, isRtl)) {
            DpadHorizontalDirection.Previous -> onMovePrevious
            DpadHorizontalDirection.Next -> onMoveNext
            null -> null
        } ?: return@onPreviewKeyEvent false
        move()
        true
    }
}

/** Handles Back inside a focused TV subtree before the activity fallback closes the screen. */
@Composable
internal fun Modifier.dpadBackNavigation(enabled: Boolean = true, onBack: () -> Unit): Modifier {
    if (!enabled) return this
    return onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
            onBack()
            true
        } else {
            false
        }
    }
}

/** Gives TV controls an explicit up/down focus chain while preserving spatial fallback. */
@Composable
internal fun Modifier.dpadVerticalFocusNavigation(onMoveUp: () -> Boolean, onMoveDown: () -> Boolean): Modifier {
    return onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        when (event.key) {
            Key.DirectionUp -> onMoveUp()
            Key.DirectionDown -> onMoveDown()
            else -> false
        }
    }
}

/** Applies the shared logical and vertical navigation policy to one top-bar control. */
@Composable
internal fun Modifier.dpadTopBarFocusNavigation(
    current: FocusRequester,
    order: List<FocusRequester>,
    onMoveDown: () -> Boolean
): Modifier = dpadOrderedFocusNavigation(current, order)
    .dpadVerticalFocusNavigation(onMoveUp = { true }, onMoveDown = onMoveDown)

/**
 * Applies the uniform action-column navigation used by list rows. Down is consumed at the final
 * row so focus cannot escape beyond the list; Up may fall back to the surrounding screen.
 */
@Composable
internal fun Modifier.dpadRowActionNavigation(
    current: FocusRequester,
    order: List<FocusRequester>,
    previousRow: FocusRequester?,
    nextRow: FocusRequester?,
    requestFocus: (FocusRequester) -> Boolean = { it.requestFocus() }
): Modifier {
    return dpadOrderedFocusNavigation(
        current = current,
        order = order
    ).dpadVerticalFocusNavigation(
        onMoveUp = { previousRow?.let(requestFocus) ?: false },
        onMoveDown = { nextRow?.let(requestFocus) ?: true }
    )
}
