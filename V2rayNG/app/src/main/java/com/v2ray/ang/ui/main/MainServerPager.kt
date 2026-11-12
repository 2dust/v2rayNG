package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import com.v2ray.ang.ui.compose.rememberDpadFocusTargets
import com.v2ray.ang.ui.compose.rememberLazyDpadFocus
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.AppIconButton
import com.v2ray.ang.ui.compose.DpadReorderDirection
import com.v2ray.ang.ui.compose.DpadReorderItem
import com.v2ray.ang.ui.compose.DpadReorderState
import com.v2ray.ang.ui.compose.ReorderableGridItem
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.colorFabActive
import com.v2ray.ang.ui.compose.colorPing
import com.v2ray.ang.ui.compose.colorPingRed
import com.v2ray.ang.ui.compose.dpadBackNavigation
import com.v2ray.ang.ui.compose.dpadClickable
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.dpadLongPressToMove
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.dpadRowActionNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.isTelevisionDevice
import com.v2ray.ang.ui.compose.keepDpadReorderItemVisible
import com.v2ray.ang.ui.compose.rememberSyncedDpadReorderState
import com.v2ray.ang.ui.compose.reorderIndicesForKeys
import com.v2ray.ang.ui.compose.twoColumnDpadReorderTarget
import com.v2ray.ang.ui.compose.verticalDpadReorderTarget
import com.v2ray.ang.ui.compose.verticalScrollbar
import kotlinx.coroutines.yield
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs

private class ServerRowFocusRequesters {
    val row = FocusRequester()
    val more = FocusRequester()
    val share = FocusRequester()
    val edit = FocusRequester()
    val delete = FocusRequester()
}

private enum class ServerRowLayout { SingleColumn, TwoColumn }

private data class ServerRowFocusTargets(
    val current: ServerRowFocusRequesters,
    val previous: ServerRowFocusRequesters?,
    val next: ServerRowFocusRequesters?,
    val adjacentColumn: ServerRowFocusRequesters? = null,
    val requestFocus: (FocusRequester) -> Boolean
)

private data class ServerRowActions(
    val select: () -> Unit,
    val edit: () -> Unit,
    val share: () -> Unit,
    val remove: () -> Unit,
    val more: () -> Unit,
    val back: () -> Unit,
    val movePrevious: ((FocusRequester) -> Unit)?,
    val moveUp: (() -> Unit)?,
    val reorderItem: DpadReorderItem?
)

private data class ServerCollectionActions(
    val select: (String) -> Unit,
    val edit: (String, ProfileItem) -> Unit,
    val share: (String, ProfileItem, Boolean, () -> Boolean) -> Unit,
    val remove: (String, String) -> Unit,
    val move: (Int, Int) -> Unit,
    val back: () -> Unit,
    val movePrevious: (() -> Boolean) -> Unit,
    val moveUpFromFirstRow: (() -> Unit)?
)

@Composable
private fun ServerCollectionItem(
    row: ServerRowUiModel,
    index: Int,
    rows: List<ServerRowUiModel>,
    focusRequesters: Map<String, ServerRowFocusRequesters>?,
    layout: ServerRowLayout,
    reorderState: DpadReorderState?,
    reorderTarget: (Int, DpadReorderDirection) -> Int,
    collectionActions: ServerCollectionActions,
    selectedGuid: String?,
    requestFocus: (FocusRequester) -> Boolean,
    restoreFocus: (String, (ServerRowFocusRequesters) -> FocusRequester) -> Boolean
) {
    val stride = if (layout == ServerRowLayout.TwoColumn) 2 else 1
    val current = focusRequesters?.get(row.guid)
    val previous = rows.getOrNull(index - stride)?.let { focusRequesters?.get(it.guid) }
    val next = rows.getOrNull(index + stride)?.let { focusRequesters?.get(it.guid) }
    val previousColumn = if (layout == ServerRowLayout.TwoColumn && index % 2 == 1) {
        rows.getOrNull(index - 1)?.let { focusRequesters?.get(it.guid) }
    } else null
    val adjacentColumn = if (layout == ServerRowLayout.TwoColumn && index % 2 == 0) {
        rows.getOrNull(index + 1)?.let { focusRequesters?.get(it.guid) }
    } else null
    val reorderItem = reorderState?.let {
        DpadReorderItem(it, row.guid, index, rows.size, reorderTarget, collectionActions.move)
    }
    Column {
        ServerListItem(
            model = row,
            isSelected = row.guid == selectedGuid,
            layout = layout,
            actions = ServerRowActions(
                select = { collectionActions.select(row.guid) },
                edit = { collectionActions.edit(row.guid, row.profile) },
                share = { collectionActions.share(row.guid, row.profile, false) { restoreFocus(row.guid) { it.share } } },
                remove = { collectionActions.remove(row.guid, row.remarks) },
                more = { collectionActions.share(row.guid, row.profile, true) { restoreFocus(row.guid) { it.more } } },
                back = collectionActions.back,
                movePrevious = if (current == null) null else { target ->
                    if (previousColumn != null) requestFocus(previousColumn.more)
                    else collectionActions.movePrevious { restoreFocus(row.guid) { if (target === current.more) it.more else it.row } }
                },
                moveUp = if (index < stride) collectionActions.moveUpFromFirstRow else null,
                reorderItem = reorderItem
            ),
            focusTargets = current?.let { ServerRowFocusTargets(it, previous, next, adjacentColumn, requestFocus) }
        )
        ServerItemDivider()
    }
}

@Composable
private fun RevealSelectedServerEffect(
    isTelevision: Boolean,
    generation: Int,
    selectedGuid: String?,
    selectedIndex: Int,
    doubleColumnDisplay: Boolean,
    groupId: String,
    scrollStates: MainGroupScrollStates,
    reveal: suspend (Int) -> Unit
) {
    val currentIndex by rememberUpdatedState(selectedIndex)
    LaunchedEffect(isTelevision, generation, selectedGuid, doubleColumnDisplay) {
        if (isTelevision && selectedGuid != null) {
            val revealKey = Triple(generation, selectedGuid, doubleColumnDisplay)
            // Returning to an already revealed group must not reset the user's scroll position.
            if (scrollStates.lastReveal[groupId] == revealKey) return@LaunchedEffect
            val index = snapshotFlow { currentIndex }.first { it >= 0 }
            reveal(index)
            scrollStates.lastReveal[groupId] = revealKey
        }
    }
}

@Composable
internal fun GroupPagerPage(
    groupId: String,
    mainViewModel: MainViewModel,
    selectedGuid: String?,
    locateTarget: LocateTarget?,
    doubleColumnDisplay: Boolean,
    revealSelectedGeneration: Int,
    searchQuery: String,
    scrollStates: MainGroupScrollStates,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem, Boolean, () -> Boolean) -> Unit,
    onRemoveServer: (String, String) -> Unit,
    usageInfo: SubscriptionUsageInfo?,
    groupTitle: String,
    onOpenLink: (String) -> Unit,
    onOpenDrawer: (() -> Boolean) -> Unit,
    onBackFromList: () -> Unit,
    onMoveUpFromFirstRow: (() -> Unit)?,
    contentPadding: PaddingValues
) {
    val groupStateFlow = remember(mainViewModel, groupId) { mainViewModel.serverGroupState(groupId) }
    val groupState by groupStateFlow.collectAsStateWithLifecycle()
    val canReorder = groupId.isNotEmpty() && searchQuery.isEmpty()
    val isTelevision = isTelevisionDevice()
    val refreshFocusRequester = remember(groupId) { FocusRequester() }
    // Keep the upstream floating card and reserve its height above the scrollable server rows.
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var infoHeightPx by remember(groupId) { mutableIntStateOf(0) }
    val listPadding = if (usageInfo == null) {
        contentPadding
    } else {
        PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection),
            top = contentPadding.calculateTopPadding() + with(density) { infoHeightPx.toDp() },
            end = contentPadding.calculateEndPadding(layoutDirection),
            bottom = contentPadding.calculateBottomPadding()
        )
    }
    val moveUpFromFirstRow: (() -> Unit)? = if (isTelevision && usageInfo != null) {
        { refreshFocusRequester.requestFocus() }
    } else onMoveUpFromFirstRow

    Box(Modifier.fillMaxSize()) {
        ServerListPage(
            rows = groupState.rows,
            selectedGuid = selectedGuid,
            locateTarget = locateTarget?.takeIf { it.groupId == groupId },
            canReorder = canReorder,
            doubleColumnDisplay = doubleColumnDisplay,
            revealSelectedGeneration = revealSelectedGeneration,
            groupId = groupId,
            scrollStates = scrollStates,
            collectionActions = ServerCollectionActions(
                select = onSelectServer,
                edit = onEditServer,
                share = onShareServer,
                remove = onRemoveServer,
                move = { from, to -> mainViewModel.moveServer(groupId, from, to) },
                back = onBackFromList,
                movePrevious = onOpenDrawer,
                moveUpFromFirstRow = moveUpFromFirstRow
            ),
            onLocateHandled = { mainViewModel.onAction(MainAction.LocateHandled(it)) },
            contentPadding = listPadding
        )
        if (usageInfo != null) {
            SubscriptionInfoCard(
                info = usageInfo,
                fallbackTitle = groupTitle,
                onOpenLink = onOpenLink,
                onRefresh = { mainViewModel.onAction(MainAction.UpdateGroupSubscription(groupId)) },
                refreshFocusRequester = refreshFocusRequester,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(
                        start = contentPadding.calculateStartPadding(layoutDirection),
                        top = contentPadding.calculateTopPadding(),
                        end = contentPadding.calculateEndPadding(layoutDirection)
                    )
                    .onSizeChanged { infoHeightPx = it.height }
                    .dpadVerticalFocusNavigation(
                        onMoveUp = { onMoveUpFromFirstRow?.let { it(); true } ?: false },
                        onMoveDown = { false }
                    )
                    .dpadBackNavigation(onBack = onBackFromList)
            )
        }
    }
}

@Composable
private fun ServerListPage(
    rows: List<ServerRowUiModel>,
    selectedGuid: String?,
    locateTarget: LocateTarget?,
    canReorder: Boolean,
    doubleColumnDisplay: Boolean,
    revealSelectedGeneration: Int,
    groupId: String,
    scrollStates: MainGroupScrollStates,
    collectionActions: ServerCollectionActions,
    onLocateHandled: (LocateTarget) -> Unit,
    contentPadding: PaddingValues
) {
    val isTelevision = isTelevisionDevice()
    val selectedServerIndex = rows.indexOfFirst { it.guid == selectedGuid }
    val serverGuids = rows.map { it.guid }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val rowFocusTargets = if (isTelevision) rememberDpadFocusTargets(serverGuids) { ServerRowFocusRequesters() } else null
    val gridState = remember(groupId) {
        scrollStates.grids.getOrPut(groupId) { LazyGridState() }
    }
    val listState = remember(groupId) {
        scrollStates.lists.getOrPut(groupId) { LazyListState() }
    }
    val revealRow: suspend (Int) -> Unit = { index ->
        if (doubleColumnDisplay) gridState.scrollToItem(index, -gridState.layoutInfo.viewportSize.height / 3)
        else listState.scrollToItem(index, -listState.layoutInfo.viewportSize.height / 3)
    }
    RevealSelectedServerEffect(
        isTelevision, revealSelectedGeneration, selectedGuid, selectedServerIndex, doubleColumnDisplay, groupId, scrollStates, revealRow
    )
    LocateTargetEffect(locateTarget, rows, doubleColumnDisplay, onLocateHandled, revealRow)

    val requestRowFocus: (FocusRequester) -> Boolean = if (rowFocusTargets != null) {
        rememberLazyDpadFocus(rows.map {
            with(rowFocusTargets.getValue(it.guid)) {
                if (doubleColumnDisplay) listOf(row, more) else listOf(row, share, edit, delete)
            }
        }) { if (doubleColumnDisplay) gridState.scrollToItem(it) else listState.scrollToItem(it) }
    } else { _ -> false }
    // Restoration lives with the collection, not a lazy item that may leave composition.
    val latestTargets by rememberUpdatedState(rowFocusTargets)
    fun restoreFocus(guid: String, slot: (ServerRowFocusRequesters) -> FocusRequester): Boolean =
        latestTargets?.get(guid)?.let { requestRowFocus(slot(it)) } ?: false
    val dpadReorderState = if (rowFocusTargets != null && canReorder) rememberSyncedDpadReorderState(
        keys = serverGuids,
        enabled = true,
        stateKey = groupId
    ) { key, index ->
        val guid = key as? String ?: return@rememberSyncedDpadReorderState
        if (index >= 0) {
            if (doubleColumnDisplay) {
                gridState.keepDpadReorderItemVisible(guid, index)
            } else {
                listState.keepDpadReorderItemVisible(guid, index)
            }
        }
        rowFocusTargets[guid]?.row?.requestFocus()
    } else null

    fun moveByKey(fromKey: Any?, toKey: Any?) {
        reorderIndicesForKeys(serverGuids, fromKey, toKey)?.let { (fromIndex, toIndex) ->
            collectionActions.move(fromIndex, toIndex)
        }
    }

    if (doubleColumnDisplay) {
        val reorderableGridState = if (canReorder) {
            rememberReorderableLazyGridState(gridState) { from, to -> moveByKey(from.key, to.key) }
        } else null

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollbar(gridState),
            contentPadding = contentPadding
        ) {
            itemsIndexed(items = rows, key = { _, item -> item.guid }) { index, row ->
                val isMoving = dpadReorderState?.isMoving(row.guid) == true
                val content: @Composable () -> Unit = {
                    ServerCollectionItem(
                        row, index, rows, rowFocusTargets, ServerRowLayout.TwoColumn,
                        dpadReorderState,
                        { currentIndex, direction -> twoColumnDpadReorderTarget(currentIndex, direction, isRtl) },
                        collectionActions, selectedGuid, requestRowFocus, ::restoreFocus
                    )
                }
                if (canReorder && reorderableGridState != null) {
                    ReorderableItem(reorderableGridState, key = row.guid, modifier = Modifier.zIndex(if (isMoving) 1f else 0f)) { isDragging ->
                        ReorderableGridItem(this, isDragging, isMoving, moveModeCornerRadius = 12.dp) { content() }
                    }
                } else {
                    content()
                }
            }
        }
    } else {
        val reorderableState = if (canReorder) {
            rememberReorderableLazyListState(listState) { from, to -> moveByKey(from.key, to.key) }
        } else null

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollbar(listState),
            contentPadding = contentPadding
        ) {
            itemsIndexed(items = rows, key = { _, item -> item.guid }) { index, row ->
                val isMoving = dpadReorderState?.isMoving(row.guid) == true
                val content: @Composable () -> Unit = {
                    ServerCollectionItem(
                        row, index, rows, rowFocusTargets, ServerRowLayout.SingleColumn,
                        dpadReorderState, ::verticalDpadReorderTarget,
                        collectionActions, selectedGuid, requestRowFocus, ::restoreFocus
                    )
                }
                if (canReorder && reorderableState != null) {
                    ReorderableItem(reorderableState, key = row.guid, modifier = Modifier.zIndex(if (isMoving) 1f else 0f)) { isDragging ->
                        ReorderableListItem(this, isDragging, isMoving, moveModeCornerRadius = 12.dp) { content() }
                    }
                } else {
                    content()
                }
            }
        }
    }
}

@Composable
private fun LocateTargetEffect(
    target: LocateTarget?, rows: List<ServerRowUiModel>, doubleColumnDisplay: Boolean,
    onHandled: (LocateTarget) -> Unit, reveal: suspend (Int) -> Unit
) {
    if (target == null) return
    LaunchedEffect(target, rows, doubleColumnDisplay) {
        val index = rows.indexOfFirst { it.guid == target.serverGuid }
        if (index < 0) return@LaunchedEffect
        reveal(index)
        onHandled(target)
    }
}

@Composable
private fun ServerItemDivider() {
    AppDivider(Modifier.padding(horizontal = 12.dp, vertical = if (isTelevisionDevice()) 2.dp else 0.dp))
}

@Composable
private fun ServerListItem(
    model: ServerRowUiModel,
    isSelected: Boolean,
    layout: ServerRowLayout,
    actions: ServerRowActions,
    focusTargets: ServerRowFocusTargets?
) {
    val isTelevision = isTelevisionDevice()
    val currentFocus = focusTargets?.current
    val previousFocus = focusTargets?.previous
    val nextFocus = focusTargets?.next
    val requestFocus = focusTargets?.requestFocus ?: { _: FocusRequester -> false }
    val isTwoColumn = layout == ServerRowLayout.TwoColumn
    val reorderItem = actions.reorderItem
    val isMoving = reorderItem?.let { it.state.isMoving(it.key) } == true
    val layoutDirection = LocalLayoutDirection.current
    val compactActionModifier = if (isTelevision) Modifier else Modifier.size(36.dp)
    val actionFocusOrder = remember(currentFocus, isTwoColumn) {
        when {
            currentFocus == null -> emptyList()
            isTwoColumn -> listOf(currentFocus.row, currentFocus.more)
            else -> listOf(currentFocus.row, currentFocus.share, currentFocus.edit, currentFocus.delete)
        }
    }
    val rowNavigation = if (currentFocus != null) Modifier
        .dpadOrderedFocusNavigation(currentFocus.row, actionFocusOrder,
            onBeforeFirst = { actions.movePrevious?.invoke(currentFocus.row) })
        .dpadVerticalFocusNavigation(
            onMoveUp = { previousFocus?.row?.let(requestFocus) ?: actions.moveUp?.let { it(); true } ?: false },
            onMoveDown = { nextFocus?.row?.let(requestFocus) ?: true }
        ) else Modifier
    @Composable
    fun actionModifier(slot: (ServerRowFocusRequesters) -> FocusRequester): Modifier =
        if (currentFocus == null) compactActionModifier
        else compactActionModifier.dpadRowActionNavigation(
            slot(currentFocus), actionFocusOrder, previousFocus?.let(slot), nextFocus?.let(slot), requestFocus
        )
    val selectedStateDescription = if (isSelected) stringResource(R.string.acc_selected_server) else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { if (selectedStateDescription != null) stateDescription = selectedStateDescription }
            .dpadFocusOutline(
                focusRequester = currentFocus?.row,
                showFocus = !isMoving
            )
            .dpadBackNavigation(enabled = !isMoving, onBack = actions.back)
            .drawBehind {
                if (isSelected && !isMoving) {
                    val stripWidth = 4.dp.toPx()
                    val verticalInset = 10.dp.toPx()
                    val startInset = 6.dp.toPx()
                    val x = if (layoutDirection == LayoutDirection.Rtl) size.width - startInset - stripWidth else startInset
                    drawRect(
                        color = colorFabActive,
                        topLeft = Offset(x, verticalInset),
                        size = Size(stripWidth, (size.height - verticalInset * 2).coerceAtLeast(0f))
                    )
                }
            }
            .then(rowNavigation)
            .then(
                if (!isTelevision) {
                    Modifier.clickable(onClick = actions.select)
                } else if (reorderItem != null) {
                    Modifier.dpadLongPressToMove(enabled = true, item = reorderItem, onClick = actions.select)
                } else {
                    Modifier.dpadClickable(onClick = actions.select)
                }
            )
    ) {
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    model.remarks,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Paragraph),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (isTwoColumn) {
                    AppIconButton(
                        icon = painterResource(R.drawable.ic_more_vert_24dp),
                        label = stringResource(R.string.action_more),
                        onClick = actions.more,
                        focusRequester = currentFocus?.more,
                        modifier = if (currentFocus != null) compactActionModifier
                            .dpadOrderedFocusNavigation(
                                current = currentFocus.more,
                                order = actionFocusOrder,
                                onAfterLast = {
                                    focusTargets.adjacentColumn?.row?.let(requestFocus)
                                        ?: currentFocus.more.requestFocus()
                                }
                            )
                            .dpadVerticalFocusNavigation(
                                onMoveUp = { previousFocus?.more?.let(requestFocus) ?: false },
                                onMoveDown = { nextFocus?.more?.let(requestFocus) ?: true }
                            ) else compactActionModifier
                    )
                } else {
                    AppIconButton(
                        icon = painterResource(R.drawable.ic_share_24dp),
                        label = stringResource(R.string.title_configuration_share),
                        onClick = actions.share,
                        focusRequester = currentFocus?.share,
                        modifier = actionModifier(ServerRowFocusRequesters::share)
                    )
                    AppIconButton(
                        icon = painterResource(R.drawable.ic_edit_24dp),
                        label = stringResource(R.string.menu_item_edit_config),
                        onClick = actions.edit,
                        focusRequester = currentFocus?.edit,
                        modifier = actionModifier(ServerRowFocusRequesters::edit)
                    )
                    AppIconButton(
                        icon = painterResource(R.drawable.ic_delete_24dp),
                        label = stringResource(R.string.menu_item_del_config),
                        onClick = actions.remove,
                        focusRequester = currentFocus?.delete,
                        modifier = actionModifier(ServerRowFocusRequesters::delete)
                    )
                }
            }

            if (isTelevision) {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SubscriptionBadge(model.subscriptionBadge)
                    Text(
                        model.statistics,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        model.typeDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorConfigType,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(16.dp))
                    TestResult(model.testDelayMillis)
                }
            } else {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SubscriptionBadge(model.subscriptionBadge)
                    Text(
                        model.statistics,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        model.typeDescription,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorConfigType,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    TestResult(model.testDelayMillis)
                }
            }
        }
    }
}

@Composable
private fun SubscriptionBadge(text: String) {
    if (text.isBlank()) return
    Box(
        Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.width(8.dp))
}

@Composable
private fun TestResult(delayMillis: Long) {
    Text(
        if (delayMillis == 0L) "" else stringResource(R.string.server_test_delay_value, delayMillis),
        style = MaterialTheme.typography.bodySmall,
        color = if (delayMillis < 0L) colorPingRed else colorPing,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

internal suspend fun PagerState.navigateToPageOptimized(targetPage: Int) {
    if (pageCount <= 0) return
    val target = targetPage.coerceIn(0, pageCount - 1)
    val current = settledPage.coerceIn(0, pageCount - 1)
    if (target == current) return
    if (abs(target - current) > 1) {
        val adjacent = if (target > current) target - 1 else target + 1
        scrollToPage(adjacent)
        yield()
    }
    animateScrollToPage(target)
}
