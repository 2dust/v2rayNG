package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.DpadReorderDirection
import com.v2ray.ang.ui.compose.DpadReorderItem
import com.v2ray.ang.ui.compose.TvIconButton
import com.v2ray.ang.ui.compose.TvReorderableItem
import com.v2ray.ang.ui.compose.dpadBackNavigation
import com.v2ray.ang.ui.compose.dpadClickable
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.dpadLongPressToMove
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.dpadRowActionNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.keepDpadReorderItemVisible
import com.v2ray.ang.ui.compose.rememberDpadFocusTargets
import com.v2ray.ang.ui.compose.rememberLazyDpadFocus
import com.v2ray.ang.ui.compose.rememberSyncedDpadReorderState
import com.v2ray.ang.ui.compose.requestFocusWhenReady
import com.v2ray.ang.ui.compose.twoColumnDpadReorderTarget
import com.v2ray.ang.ui.compose.verticalDpadReorderTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private class ProfileRowFocus(actions: List<ProfileRowAction>) {
    val row = FocusRequester()
    val buttons = actions.associateWith { FocusRequester() }
    val order = listOf(row) + buttons.values
}

internal data class ProfileReveal(val generation: Int, val guid: String, val doubleColumn: Boolean)

internal fun focusAfterProfileRemoval(keys: List<String>, guid: String): String? {
    val index = keys.indexOf(guid)
    if (index < 0) return null
    return keys.getOrNull(index + 1) ?: keys.getOrNull(index - 1)
}

@Composable
internal fun TvProfileGroup(
    group: GroupMapItem,
    mainViewModel: MainViewModel,
    uiState: MainUiState,
    scroll: TvProfileScrollState,
    dialogs: TvMainDialogActions,
    onAction: (MainAction) -> Unit,
    onEdit: (MainAction.EditServer) -> Unit,
    editorFocusGuid: String?,
    onEditorFocusHandled: (Boolean) -> Unit,
    revealGeneration: Int,
    lastReveals: MutableMap<String, ProfileReveal>,
    onOpenDrawer: (() -> Boolean) -> Unit,
    onBackToToolbar: () -> Unit,
    onUpFromFirstRow: (() -> Boolean)?
) {
    val groupStateFlow = remember(mainViewModel, group.id) { mainViewModel.serverGroupState(group.id) }
    val groupState by groupStateFlow.collectAsStateWithLifecycle()
    val rows = groupState.rows
    val doubleColumn = uiState.doubleColumnDisplay
    val rowActions = if (doubleColumn) gridProfileActions else singleColumnProfileActions
    val keys = remember(rows) { rows.map { it.guid } }
    val targets = key(doubleColumn) { rememberDpadFocusTargets(keys) { ProfileRowFocus(rowActions) } }
    val requestFocus = rememberLazyDpadFocus(keys.map { targets.getValue(it).order }) {
        if (doubleColumn) scroll.grid.scrollToItem(it) else scroll.list.scrollToItem(it)
    }
    // A drawer or dialog may outlive a lazy item. Resolve the current row by GUID when restoring.
    val latestTargets by rememberUpdatedState(targets)
    val latestKeys by rememberUpdatedState(keys)
    fun restore(guid: String, action: ProfileRowAction? = null): Boolean {
        val focus = latestTargets[guid] ?: return false
        val target = if (action == null) focus.row else focus.buttons[action] ?: return false
        return requestFocus(target)
    }
    suspend fun restoreAfterRemoval(guid: String, neighbor: String?, action: ProfileRowAction): Boolean {
        // Refresh runs off the UI thread; focus the replacement only after the row has gone.
        val removed = withTimeoutOrNull(2_000) { snapshotFlow { guid !in latestKeys }.first { it } } == true
        return if (removed) neighbor?.let { restore(it) } ?: false else restore(guid, action)
    }

    if (editorFocusGuid == null) {
        RevealSelectedProfile(group.id, scroll, revealGeneration, uiState.selectedGuid, rows, doubleColumn, lastReveals)
    } else {
        val window = LocalWindowInfo.current
        val onHandled by rememberUpdatedState(onEditorFocusHandled)
        LaunchedEffect(editorFocusGuid, keys, doubleColumn) {
            snapshotFlow { window.isWindowFocused }.first { it }
            mainViewModel.awaitInitialProfilePage()
            val index = keys.indexOf(editorFocusGuid)
            // After process recreation the group flow can be populated before its rows are composed.
            if (index < 0 && groupStateFlow.value.rows.any { it.guid == editorFocusGuid }) return@LaunchedEffect
            val focus = targets[editorFocusGuid]
            val button = focus?.buttons?.get(if (doubleColumn) ProfileRowAction.More else ProfileRowAction.Edit)
            val restored = button != null && (button.requestFocus() || run {
                scroll.reveal(index, doubleColumn)
                requestFocusWhenReady(button)
            })
            if (restored) {
                uiState.selectedGuid?.let { lastReveals[group.id] = ProfileReveal(revealGeneration, it, doubleColumn) }
            }
            onHandled(restored)
        }
    }
    val canReorder = groupState.canReorder(group.id, uiState.searchQuery)
    val reorder = if (canReorder) rememberSyncedDpadReorderState(keys) { key, index ->
        val guid = key as? String ?: return@rememberSyncedDpadReorderState
        if (index >= 0) {
            if (doubleColumn) scroll.grid.keepDpadReorderItemVisible(guid, index)
            else scroll.list.keepDpadReorderItemVisible(guid, index)
        }
        targets[guid]?.row?.requestFocus()
    } else null
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val moveTarget: (Int, DpadReorderDirection) -> Int = if (doubleColumn) {
        { index, direction -> twoColumnDpadReorderTarget(index, direction, isRtl) }
    } else ::verticalDpadReorderTarget

    val refreshFocus = if (group.usageInfo != null) remember { FocusRequester() } else null
    val headerModifier = Modifier
        .dpadVerticalFocusNavigation(onMoveUp = { onUpFromFirstRow?.invoke() ?: false }, onMoveDown = { false })
        .dpadBackNavigation(onBack = onBackToToolbar)
    TvProfileGroupLayout(
        group = group,
        contentPadding = PaddingValues(start = 48.dp, top = 16.dp, end = 48.dp, bottom = 80.dp),
        onAction = onAction,
        headerModifier = headerModifier,
        actionButton = { icon, label, isRefresh, click ->
            TvIconButton(painterResource(icon), label, click, focusRequester = refreshFocus.takeIf { isRefresh })
        }
    ) { padding ->
        TvProfileCollection(
            group, groupState, uiState, scroll, padding, onAction,
            itemModifier = { guid -> if (reorder?.isMoving(guid) == true) Modifier.zIndex(1f) else Modifier }
        ) { index, row, dragScope, dragging ->
            val stride = if (doubleColumn) 2 else 1
            fun targetAt(position: Int) = keys.getOrNull(position)?.let(targets::get)
            val leadingColumn = !doubleColumn || index % 2 == 0
            val reorderItem = reorder?.let {
                DpadReorderItem(it, row.guid, index, rows.size, moveTarget) { _, destination ->
                    // Remote repeats may arrive before the rendered order has caught up.
                    groupStateFlow.value.rows.getOrNull(destination)?.let { target ->
                        onAction(MainAction.MoveServer(group.id, row.guid, target.guid))
                    }
                }
            }
            val renderRow = @Composable {
                TvProfileRow(
                    row = row,
                    selected = row.guid == uiState.selectedGuid,
                    current = targets.getValue(row.guid),
                    above = targetAt(index - stride),
                    below = targetAt(index + stride),
                    nextColumn = if (doubleColumn && leadingColumn) targetAt(index + 1)?.row else null,
                    requestFocus = requestFocus,
                    onPrevious = {
                        val previousColumn = if (leadingColumn) null else targetAt(index - 1)?.buttons?.get(ProfileRowAction.More)
                        if (previousColumn != null) requestFocus(previousColumn) else onOpenDrawer { restore(row.guid) }
                    },
                    onUpFromFirstRow = {
                        if (refreshFocus != null) refreshFocus.requestFocus() else onUpFromFirstRow?.invoke() ?: false
                    },
                    onBackToToolbar = onBackToToolbar,
                    reorderItem = reorderItem,
                    onSelect = { onAction(MainAction.SelectServer(row.guid)) },
                    onAction = { action ->
                        val neighbor = focusAfterProfileRemoval(latestKeys, row.guid)
                        runProfileRowAction(action, row, onEdit, dialogs) { removed ->
                            if (removed) restoreAfterRemoval(row.guid, neighbor, action)
                            else restore(row.guid, action)
                        }
                    }
                )
            }
            if (dragScope == null) renderRow()
            else TvReorderableItem(dragScope, dragging, reorder?.isMoving(row.guid) == true, renderRow)
        }
    }
}

@Composable
private fun RevealSelectedProfile(
    groupId: String,
    scroll: TvProfileScrollState,
    revealGeneration: Int,
    selectedGuid: String?,
    rows: List<ServerRowUiModel>,
    doubleColumn: Boolean,
    lastReveals: MutableMap<String, ProfileReveal>
) {
    val latestRows by rememberUpdatedState(rows)
    LaunchedEffect(scroll, revealGeneration, selectedGuid, doubleColumn) {
        if (selectedGuid == null) return@LaunchedEffect
        val reveal = ProfileReveal(revealGeneration, selectedGuid, doubleColumn)
        // Returning to an already revealed group must not reset its scroll position.
        if (lastReveals[groupId] == reveal) return@LaunchedEffect
        val index = snapshotFlow { latestRows.indexOfFirst { it.guid == selectedGuid } }.first { it >= 0 }
        scroll.reveal(index, doubleColumn)
        lastReveals[groupId] = reveal
    }
}

@Composable
private fun TvProfileRow(
    row: ServerRowUiModel,
    selected: Boolean,
    current: ProfileRowFocus,
    above: ProfileRowFocus?,
    below: ProfileRowFocus?,
    nextColumn: FocusRequester?,
    requestFocus: (FocusRequester) -> Boolean,
    onPrevious: () -> Unit,
    onUpFromFirstRow: () -> Boolean,
    onBackToToolbar: () -> Unit,
    reorderItem: DpadReorderItem?,
    onSelect: () -> Unit,
    onAction: (ProfileRowAction) -> Unit
) {
    val moving = reorderItem?.let { it.state.isMoving(row.guid) } == true
    val decoration = Modifier.dpadFocusOutline(focusRequester = current.row, showFocus = !moving)
        .dpadBackNavigation(enabled = !moving, onBack = onBackToToolbar)
    val interaction = Modifier
        .dpadOrderedFocusNavigation(current.row, current.order, onBeforeFirst = onPrevious)
        .dpadVerticalFocusNavigation(
            onMoveUp = { above?.row?.let(requestFocus) ?: onUpFromFirstRow() },
            onMoveDown = { below?.row?.let(requestFocus) ?: true }
        )
        .then(
            if (reorderItem == null) Modifier.dpadClickable(onClick = onSelect)
            else Modifier.dpadLongPressToMove(item = reorderItem, onClick = onSelect)
        )
    Column {
        TvProfileRowLayout(
            model = row,
            selected = selected,
            showSelectionMarker = selected && !moving,
            decoration = decoration,
            interaction = interaction,
            actions = {
                current.buttons.forEach { (action, target) ->
                    val previous = above?.buttons?.get(action)
                    val next = below?.buttons?.get(action)
                    val navigation = if (action == ProfileRowAction.More) {
                        Modifier.dpadOrderedFocusNavigation(
                            target, current.order, onAfterLast = { nextColumn?.let(requestFocus) ?: target.requestFocus() }
                        ).dpadVerticalFocusNavigation(
                            onMoveUp = { previous?.let(requestFocus) ?: false },
                            onMoveDown = { next?.let(requestFocus) ?: true }
                        )
                    } else Modifier.dpadRowActionNavigation(target, current.order, previous, next, requestFocus)
                    TvIconButton(
                        painterResource(action.icon), stringResource(action.label), { onAction(action) },
                        modifier = navigation, focusRequester = target, contentDescription = stringResource(action.description)
                    )
                }
            }
        ) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TvProfileAddress(row)
                Spacer(Modifier.width(16.dp))
                TvProfileType(row.typeDescription)
                Spacer(Modifier.width(16.dp))
                TvProfileTestResult(row.testDelayMillis)
            }
        }
        AppDivider(Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
    }
}
