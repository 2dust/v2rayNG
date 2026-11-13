package com.v2ray.ang.ui.compose

import android.view.ViewConfiguration
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.onLongClick as semanticsOnLongClick
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Do not replace this quiet-period check with repeatCount-only detection.
 * Android TV emulators and some keyboard/input translation paths can report a held
 * Enter/DPAD_CENTER as discrete ACTION_UP/ACTION_DOWN pairs whose repeatCount is zero.
 * Without a quiet period, one hold can start movement, drop the item, and then click it.
 * Keep a conservative 500 ms floor, while allowing slower configured key-repeat
 * delays to extend it. This only delays the Center press that drops a moving item;
 * directional movement remains immediate. Remove the quiet period only when every supported
 * Android TV remote and USB/Bluetooth keyboard path reports a held activation as one down/up
 * sequence with non-zero repeatCount or isLongPress on every repeated event.
 */
private const val MIN_KEY_REPEAT_QUIET_PERIOD_MS = 500L

internal enum class DpadReorderDirection { Up, Down, Left, Right }

internal enum class DpadReorderPhase { Idle, Pressed, MovingAwaitRelease, MovingReady }

internal enum class DpadReorderActivation { None, Click, StartedMoving, Dropped }

@Stable
internal class DpadReorderState {
    internal var phase by mutableStateOf(DpadReorderPhase.Idle)
        private set

    private var activeKey by mutableStateOf<Any?>(null)
    private var dropAllowedAfterMillis = Long.MIN_VALUE
    var movingIndex by mutableIntStateOf(-1)
        private set

    val movingKey: Any?
        get() = activeKey.takeIf { isMoving }

    val isMoving: Boolean
        get() = phase == DpadReorderPhase.MovingAwaitRelease ||
                phase == DpadReorderPhase.MovingReady

    fun isMoving(key: Any): Boolean = isMoving && activeKey == key

    fun syncItems(keys: Collection<*>, enabled: Boolean = true) {
        val key = activeKey ?: return
        val index = keys.indexOfFirst { it == key }
        if (!enabled || index < 0) {
            reset()
        } else {
            movingIndex = index
        }
    }

    internal fun onActivationKeyDown(
        key: Any,
        index: Int,
        isLongPress: Boolean,
        eventTimeMillis: Long,
        repeatReleaseGuardMillis: Long
    ): DpadReorderActivation {
        return when (phase) {
            DpadReorderPhase.Idle -> {
                activeKey = key
                movingIndex = index
                phase = if (isLongPress) {
                    DpadReorderPhase.MovingAwaitRelease
                } else {
                    DpadReorderPhase.Pressed
                }
                if (isLongPress) {
                    DpadReorderActivation.StartedMoving
                } else {
                    DpadReorderActivation.None
                }
            }

            DpadReorderPhase.Pressed -> {
                if (activeKey == key && isLongPress) {
                    phase = DpadReorderPhase.MovingAwaitRelease
                    DpadReorderActivation.StartedMoving
                } else {
                    DpadReorderActivation.None
                }
            }

            DpadReorderPhase.MovingAwaitRelease -> DpadReorderActivation.None

            DpadReorderPhase.MovingReady -> {
                if (activeKey == key) {
                    if (isLongPress || eventTimeMillis <= dropAllowedAfterMillis) {
                        dropAllowedAfterMillis = eventTimeMillis + repeatReleaseGuardMillis
                        DpadReorderActivation.None
                    } else {
                        reset()
                        DpadReorderActivation.Dropped
                    }
                } else {
                    DpadReorderActivation.None
                }
            }
        }
    }

    internal fun onActivationKeyUp(
        key: Any,
        eventTimeMillis: Long,
        repeatReleaseGuardMillis: Long,
        canceled: Boolean = false
    ): DpadReorderActivation {
        if (activeKey != key) return DpadReorderActivation.None
        if (canceled && phase == DpadReorderPhase.Pressed) {
            reset()
            return DpadReorderActivation.None
        }

        return when (phase) {
            DpadReorderPhase.Idle -> DpadReorderActivation.None
            DpadReorderPhase.Pressed -> {
                reset()
                DpadReorderActivation.Click
            }

            DpadReorderPhase.MovingAwaitRelease -> {
                phase = DpadReorderPhase.MovingReady
                dropAllowedAfterMillis = eventTimeMillis + repeatReleaseGuardMillis
                DpadReorderActivation.None
            }

            DpadReorderPhase.MovingReady -> {
                dropAllowedAfterMillis = eventTimeMillis + repeatReleaseGuardMillis
                DpadReorderActivation.None
            }
        }
    }

    internal fun onLongPressTimeout(key: Any): DpadReorderActivation {
        if (phase != DpadReorderPhase.Pressed || activeKey != key) {
            return DpadReorderActivation.None
        }
        phase = DpadReorderPhase.MovingAwaitRelease
        return DpadReorderActivation.StartedMoving
    }

    internal fun onPointerClick(key: Any): DpadReorderActivation {
        return if (isMoving && activeKey == key) {
            reset()
            DpadReorderActivation.Dropped
        } else if (phase == DpadReorderPhase.Idle) {
            reset()
            DpadReorderActivation.Click
        } else {
            DpadReorderActivation.None
        }
    }

    internal fun onPointerLongPress(key: Any, index: Int): DpadReorderActivation {
        if (isMoving && activeKey != key) return DpadReorderActivation.None
        activeKey = key
        movingIndex = index
        phase = DpadReorderPhase.MovingReady
        return DpadReorderActivation.StartedMoving
    }

    internal fun onDropRequest(key: Any): DpadReorderActivation {
        if (!isMoving || activeKey != key) return DpadReorderActivation.None
        reset()
        return DpadReorderActivation.Dropped
    }

    internal fun cancelPendingPress(key: Any) {
        if (phase == DpadReorderPhase.Pressed && activeKey == key) reset()
    }

    internal fun cancelInteraction() = reset()

    internal fun move(
        direction: DpadReorderDirection,
        itemCount: Int,
        targetIndex: (Int, DpadReorderDirection) -> Int,
        onMove: (Int, Int) -> Unit
    ): Boolean {
        if (!isMoving) return false

        val fromIndex = movingIndex
        val toIndex = targetIndex(fromIndex, direction)
        if (toIndex in 0 until itemCount && toIndex != fromIndex) {
            movingIndex = toIndex
            onMove(fromIndex, toIndex)
        }
        return true
    }

    private fun reset() {
        phase = DpadReorderPhase.Idle
        activeKey = null
        dropAllowedAfterMillis = Long.MIN_VALUE
        movingIndex = -1
    }
}

internal class DpadReorderItem(
    val state: DpadReorderState,
    val key: Any,
    val index: Int,
    val itemCount: Int,
    val targetIndex: (Int, DpadReorderDirection) -> Int,
    val onMove: (Int, Int) -> Unit
)

@Composable
private fun rememberDpadReorderState(key: Any? = Unit): DpadReorderState =
    remember(key) { DpadReorderState() }

@Composable
internal fun rememberSyncedDpadReorderState(
    keys: List<*>,
    enabled: Boolean,
    stateKey: Any? = Unit,
    onMovingItem: suspend (key: Any, index: Int) -> Unit
): DpadReorderState {
    val state = rememberDpadReorderState(stateKey)
    if (!isTelevisionDevice()) return state
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(state, lifecycleOwner) {
        // Row focus can move transiently while an item is reordered. End the interaction only
        // when the owning screen leaves the foreground or composition.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) state.cancelInteraction()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            state.cancelInteraction()
        }
    }
    LaunchedEffect(state, enabled, keys) {
        state.syncItems(keys, enabled)
    }
    LaunchedEffect(state.movingKey, state.movingIndex) {
        val movingKey = state.movingKey ?: return@LaunchedEffect
        withFrameNanos { }
        onMovingItem(movingKey, state.movingIndex)
    }
    return state
}

internal fun verticalDpadReorderTarget(index: Int, direction: DpadReorderDirection): Int = when (direction) {
    DpadReorderDirection.Up -> index - 1
    DpadReorderDirection.Down -> index + 1
    DpadReorderDirection.Left,
    DpadReorderDirection.Right -> index
}

internal fun twoColumnDpadReorderTarget(
    index: Int,
    direction: DpadReorderDirection,
    isRtl: Boolean
): Int = when (direction) {
    DpadReorderDirection.Up -> index - 2
    DpadReorderDirection.Down -> index + 2
    DpadReorderDirection.Left -> when {
        isRtl && index % 2 == 0 -> index + 1
        !isRtl && index % 2 == 1 -> index - 1
        else -> index
    }

    DpadReorderDirection.Right -> when {
        isRtl && index % 2 == 1 -> index - 1
        !isRtl && index % 2 == 0 -> index + 1
        else -> index
    }
}

internal fun <T> reorderIndicesForKeys(keys: List<T>, fromKey: Any?, toKey: Any?): Pair<Int, Int>? {
    val fromIndex = keys.indexOfFirst { it == fromKey }
    val toIndex = keys.indexOfFirst { it == toKey }
    return if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
        fromIndex to toIndex
    } else {
        null
    }
}

internal fun dpadRepeatReleaseGuardMillis(keyRepeatDelayMillis: Int): Long =
    maxOf(MIN_KEY_REPEAT_QUIET_PERIOD_MS, keyRepeatDelayMillis.toLong() * 2L)

internal fun dpadReorderScrollDelta(
    viewportStart: Int,
    viewportEnd: Int,
    itemStart: Int,
    itemSize: Int
): Float {
    val viewportSize = (viewportEnd - viewportStart).coerceAtLeast(0)
    // Oversized rows cannot expose both edges; consistently anchor the leading edge instead of oscillating.
    if (itemSize >= viewportSize) return (itemStart - viewportStart).toFloat()
    val edgePadding = minOf(itemSize / 2, ((viewportSize - itemSize).coerceAtLeast(0)) / 2)
    val safeStart = viewportStart + edgePadding
    val safeEnd = viewportEnd - edgePadding
    val itemEnd = itemStart + itemSize
    return when {
        itemStart < safeStart -> (itemStart - safeStart).toFloat()
        itemEnd > safeEnd -> (itemEnd - safeEnd).toFloat()
        else -> 0f
    }
}

internal suspend fun LazyListState.keepDpadReorderItemVisible(key: Any, index: Int) {
    val layout = layoutInfo
    val item = layout.visibleItemsInfo.firstOrNull { it.key == key }
    if (item == null) {
        scrollToItem(index.coerceAtLeast(0), -layout.viewportSize.height / 3)
        return
    }
    val delta = dpadReorderScrollDelta(
        viewportStart = layout.viewportStartOffset,
        viewportEnd = layout.viewportEndOffset,
        itemStart = item.offset,
        itemSize = item.size
    )
    if (delta != 0f) scrollBy(delta)
}

internal suspend fun LazyGridState.keepDpadReorderItemVisible(key: Any, index: Int) {
    val layout = layoutInfo
    val item = layout.visibleItemsInfo.firstOrNull { it.key == key }
    if (item == null) {
        scrollToItem(index.coerceAtLeast(0), -layout.viewportSize.height / 3)
        return
    }
    val delta = dpadReorderScrollDelta(
        viewportStart = layout.viewportStartOffset,
        viewportEnd = layout.viewportEndOffset,
        itemStart = item.offset.y,
        itemSize = item.size.height
    )
    if (delta != 0f) scrollBy(delta)
}

private class LongPressTimer(var job: Job? = null)

@Composable
internal fun Modifier.dpadLongPressToMove(
    enabled: Boolean,
    item: DpadReorderItem,
    onClick: () -> Unit,
    addFocusTarget: Boolean = true
): Modifier {
    if (!enabled) return this

    val coroutineScope = rememberCoroutineScope()
    val repeatReleaseGuardMillis = remember {
        dpadRepeatReleaseGuardMillis(ViewConfiguration.getKeyRepeatDelay())
    }
    var isItemFocused by remember { mutableStateOf(false) }
    var suppressedKeyUp by remember { mutableStateOf<Key?>(null) }
    val longPressTimer = remember(item.state, item.key) { LongPressTimer() }

    fun startLongPressTimer() {
        if (longPressTimer.job != null) return
        longPressTimer.job = coroutineScope.launch {
            delay(ViewConfiguration.getLongPressTimeout().toLong())
            item.state.onLongPressTimeout(item.key)
            longPressTimer.job = null
        }
    }

    fun cancelLongPressTimer() {
        longPressTimer.job?.cancel()
        longPressTimer.job = null
    }

    fun handleActivation(action: DpadReorderActivation) {
        when (action) {
            DpadReorderActivation.Click -> onClick()
            DpadReorderActivation.Dropped,
            DpadReorderActivation.StartedMoving,
            DpadReorderActivation.None -> Unit
        }
    }

    DisposableEffect(item.state, item.key) {
        onDispose {
            cancelLongPressTimer()
            item.state.cancelPendingPress(item.key)
        }
    }

    val movementModifier = pointerInput(item.state, item.key, item.index, onClick) {
        detectTapGestures(
            onTap = {
                handleActivation(item.state.onPointerClick(item.key))
            },
            onLongPress = {
                handleActivation(item.state.onPointerLongPress(item.key, item.index))
            }
        )
    }
        .semantics {
            semanticsOnClick {
                handleActivation(item.state.onPointerClick(item.key))
                true
            }
            semanticsOnLongClick {
                handleActivation(item.state.onPointerLongPress(item.key, item.index))
                true
            }
        }
        .onFocusChanged {
            isItemFocused = it.isFocused
            if (!it.isFocused) {
                cancelLongPressTimer()
                item.state.cancelPendingPress(item.key)
                suppressedKeyUp = null
            }
        }
        .onPreviewKeyEvent { event ->
            if (!isItemFocused) return@onPreviewKeyEvent false

            val isActivationKey = event.key.isDpadActivationKey()
            if (suppressedKeyUp == event.key) {
                if (event.type == KeyEventType.KeyUp) suppressedKeyUp = null
                true
            } else if (event.key == Key.Back && item.state.isMoving(item.key)) {
                if (event.type == KeyEventType.KeyDown) {
                    cancelLongPressTimer()
                    val action = item.state.onDropRequest(item.key)
                    if (action == DpadReorderActivation.Dropped) suppressedKeyUp = event.key
                    handleActivation(action)
                }
                true
            } else if (!isActivationKey) {
                val direction = when (event.key) {
                    Key.DirectionUp -> DpadReorderDirection.Up
                    Key.DirectionDown -> DpadReorderDirection.Down
                    Key.DirectionLeft -> DpadReorderDirection.Left
                    Key.DirectionRight -> DpadReorderDirection.Right
                    else -> null
                }
                if (direction == null || !item.state.isMoving(item.key)) {
                    return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown) {
                    item.state.move(
                        direction = direction,
                        itemCount = item.itemCount,
                        targetIndex = item.targetIndex,
                        onMove = item.onMove
                    )
                }
                true
            } else {
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        val isLongPress = event.nativeKeyEvent.repeatCount > 0 ||
                                event.nativeKeyEvent.isLongPress
                        val action = item.state.onActivationKeyDown(
                            key = item.key,
                            index = item.index,
                            isLongPress = isLongPress,
                            eventTimeMillis = event.nativeKeyEvent.eventTime,
                            repeatReleaseGuardMillis = repeatReleaseGuardMillis
                        )
                        if (action == DpadReorderActivation.None &&
                            item.state.phase == DpadReorderPhase.Pressed
                        ) {
                            startLongPressTimer()
                        } else {
                            cancelLongPressTimer()
                        }
                        if (action == DpadReorderActivation.Dropped) suppressedKeyUp = event.key
                        handleActivation(action)
                        true
                    }

                    KeyEventType.KeyUp -> {
                        cancelLongPressTimer()
                        handleActivation(
                            item.state.onActivationKeyUp(
                                key = item.key,
                                eventTimeMillis = event.nativeKeyEvent.eventTime,
                                repeatReleaseGuardMillis = repeatReleaseGuardMillis,
                                canceled = event.nativeKeyEvent.isCanceled
                            )
                        )
                        true
                    }

                    else -> true
                }
            }
        }

    return if (addFocusTarget) movementModifier.focusable() else movementModifier
}
