package com.v2ray.ang.ui.compose

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
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

@Composable
internal fun isTelevisionDevice(): Boolean =
    LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

/**
 * Requests focus after the target has entered the composition. Android TV does not have a
 * touch event to bootstrap Compose focus, so every screen needs a deterministic first target.
 * On phones and tablets this deliberately does nothing, preserving the existing touch UI.
 */
@Composable
internal fun rememberDpadFocusRequester(requestFocus: Boolean = true, requestKey: Any? = Unit): FocusRequester {
    val isTelevision = isTelevisionDevice()
    val requester = remember { FocusRequester() }
    if (isTelevision && requestFocus) {
        LaunchedEffect(requester, requestKey) {
            requestFocusWhenReady(requester)
        }
    }
    return requester
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
    if (!isTelevisionDevice()) return this

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

/** Keeps TV popup-menu focus borders inside the popup's clipped bounds. */
@Composable
internal fun Modifier.tvMenuItemFocus(): Modifier {
    if (!isTelevisionDevice()) return this
    return padding(horizontal = 8.dp, vertical = 2.dp)
        .dpadFocusOutline(cornerRadius = 10.dp)
}

/** Applies the fixed television safe-area inset without changing touch UIs. */
@Composable
internal fun Modifier.tvSafeAreaPadding(horizontal: Dp = 48.dp, vertical: Dp = 0.dp): Modifier {
    if (!isTelevisionDevice()) return this
    return padding(horizontal = horizontal, vertical = vertical)
}

internal fun tvImeOverlayPadding(
    isTelevision: Boolean,
    isImeVisible: Boolean,
    reportedImeBottomInset: Int,
    fallbackHeight: Dp
): Dp = if (isTelevision && isImeVisible && reportedImeBottomInset == 0) fallbackHeight else 0.dp

/**
 * Isolates [ExperimentalLayoutApi] for WindowInsets.isImeVisible. Some Android TV overlay IMEs
 * report a visible keyboard with a zero-height inset; without fallback space, the last form field
 * cannot be brought above the keyboard. Reevaluate the opt-in when isImeVisible becomes stable,
 * and remove the fallback when supported TV keyboards consistently report a non-zero IME inset.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun Modifier.tvAwareImePadding(overlayFallbackHeight: Dp = 240.dp): Modifier {
    val isTelevision = isTelevisionDevice()
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.isImeVisible
    val reportedImeBottomInset = if (isTelevision && isImeVisible) {
        WindowInsets.ime.getBottom(density)
    } else {
        0
    }
    val overlayPadding = tvImeOverlayPadding(isTelevision, isImeVisible, reportedImeBottomInset, overlayFallbackHeight)

    return imePadding().padding(bottom = overlayPadding)
}

/**
 * Uses the normal Material indication on touch devices and the explicit focus outline on TV.
 * This keeps the platform distinction in one place instead of duplicating clickable branches.
 */
@Composable
internal fun Modifier.dpadClickable(enabled: Boolean = true, role: Role? = null, onClick: () -> Unit): Modifier {
    if (!isTelevisionDevice()) {
        return clickable(enabled = enabled, role = role, onClick = onClick)
    }
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
    if (!isTelevisionDevice()) return this
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
    if (!isTelevisionDevice() || (onMovePrevious == null && onMoveNext == null)) return this
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

/**
 * Lets a focused row leave through its logical leading edge. This deliberately handles the
 * bubbling event: a focused child action gets the first chance to navigate within the row.
 */
@Composable
internal fun Modifier.dpadMovePreviousNavigation(enabled: Boolean = true, onMovePrevious: () -> Unit): Modifier {
    if (!isTelevisionDevice() || !enabled) return this
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return onKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown &&
            logicalHorizontalDirection(event.key, isRtl) == DpadHorizontalDirection.Previous
        ) {
            onMovePrevious()
            true
        } else {
            false
        }
    }
}

/** Handles Back inside a focused TV subtree before the activity fallback closes the screen. */
@Composable
internal fun Modifier.dpadBackNavigation(enabled: Boolean = true, onBack: () -> Unit): Modifier {
    if (!isTelevisionDevice() || !enabled) return this
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
    if (!isTelevisionDevice()) return this
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

/** Wires corresponding actions across adjacent rows without imposing an action order on either screen. */
internal class DpadRowActionFocus<T>(
    private val current: T,
    private val previous: T?,
    private val next: T?,
    private val order: List<FocusRequester>,
    private val requestFocus: (FocusRequester) -> Boolean
) {
    @Composable
    fun modifier(action: (T) -> FocusRequester): Modifier =
        Modifier.dpadRowActionNavigation(action(current), order, previous?.let(action), next?.let(action), requestFocus)
}

/**
 * Lets a remote leave a focused text field after the on-screen keyboard is dismissed.
 * Compose text fields otherwise consume D-pad Up/Down as cursor movement, trapping TV focus.
 */
@Composable
internal fun Modifier.dpadTextFieldNavigation(
    onMoveUp: (() -> Boolean)? = null,
    onMoveDown: (() -> Boolean)? = null
): Modifier {
    if (!isTelevisionDevice()) return this

    val focusManager = LocalFocusManager.current
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

        when (event.key) {
            Key.DirectionUp -> onMoveUp?.invoke() ?: focusManager.moveFocus(FocusDirection.Up)
            Key.DirectionDown -> onMoveDown?.invoke() ?: focusManager.moveFocus(FocusDirection.Down)
            else -> false
        }
    }
}
