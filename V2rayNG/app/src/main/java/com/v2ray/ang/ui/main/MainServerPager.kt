package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.ReorderableGridItem
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.colorPing
import com.v2ray.ang.ui.compose.colorPingRed
import com.v2ray.ang.ui.compose.verticalScrollbar
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
internal fun GroupPagerPage(
    group: GroupMapItem,
    mainViewModel: MainViewModel,
    uiState: MainUiState,
    scrollStates: MutableMap<String, ProfileScrollState>,
    onAction: (MainAction) -> Unit,
    dialogs: MainDialogActions,
    contentPadding: PaddingValues
) {
    val groupState by remember(mainViewModel, group.id) { mainViewModel.serverGroupState(group.id) }.collectAsStateWithLifecycle()
    val scroll = rememberProfileScrollState(scrollStates, group.id)
    ProfileGroupLayout(group, contentPadding, onAction, actionButton = { icon, label, _, click ->
        SubscriptionCardIconButton(icon, label, click)
    }) { padding ->
        ProfileCollection(group, groupState, uiState, scroll, padding, onAction) { _, row, dragScope, dragging ->
            val doubleColumn = uiState.doubleColumnDisplay
            val renderRow = @Composable { PhoneProfileRow(row, row.guid == uiState.selectedGuid, doubleColumn, onAction, dialogs) }
            Column {
                when {
                    dragScope == null -> renderRow()
                    doubleColumn -> ReorderableGridItem(dragScope, dragging) { renderRow(); ProfileDivider() }
                    else -> ReorderableListItem(dragScope, dragging) { renderRow() }
                }
                if (dragScope == null || !doubleColumn) ProfileDivider()
            }
        }
    }
}

/** Both views use the same floating subscription card and reserve its measured height. */
@Composable
internal fun ProfileGroupLayout(
    group: GroupMapItem,
    contentPadding: PaddingValues,
    onAction: (MainAction) -> Unit,
    headerModifier: Modifier = Modifier,
    actionButton: @Composable (Int, String, Boolean, () -> Unit) -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val usageInfo = group.usageInfo
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    var infoHeightPx by remember(group.id) { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        content(if (usageInfo == null) contentPadding else PaddingValues(
            start = contentPadding.calculateStartPadding(direction),
            top = contentPadding.calculateTopPadding() + with(density) { infoHeightPx.toDp() },
            end = contentPadding.calculateEndPadding(direction),
            bottom = contentPadding.calculateBottomPadding()
        ))
        if (usageInfo != null) {
            SubscriptionInfoCard(
                info = usageInfo,
                fallbackTitle = group.remarks,
                onOpenLink = { onAction(MainAction.OpenSubscriptionLink(it)) },
                onRefresh = { onAction(MainAction.UpdateGroupSubscription(group.id)) },
                modifier = Modifier.align(Alignment.TopCenter)
                    .padding(
                        start = contentPadding.calculateStartPadding(direction),
                        top = contentPadding.calculateTopPadding(),
                        end = contentPadding.calculateEndPadding(direction)
                    )
                    .onSizeChanged { infoHeightPx = it.height }
                    .then(headerModifier),
                actionButton = actionButton
            )
        }
    }
}

/** Shares lazy list/grid mechanics and touch dragging, without owning a focus graph. */
@Composable
internal fun ProfileCollection(
    group: GroupMapItem,
    groupState: ServerGroupUiState,
    uiState: MainUiState,
    scroll: ProfileScrollState,
    contentPadding: PaddingValues,
    onAction: (MainAction) -> Unit,
    itemModifier: (String) -> Modifier = { Modifier },
    item: @Composable (Int, ServerRowUiModel, ReorderableCollectionItemScope?, Boolean) -> Unit
) {
    val rows = groupState.rows
    val canReorder = groupState.canReorder(group.id, uiState.searchQuery)
    val doubleColumn = uiState.doubleColumnDisplay
    LocateTargetEffect(uiState.locateTarget?.takeIf { it.groupId == group.id }, rows, doubleColumn, onAction) {
        scroll.reveal(it, doubleColumn)
    }
    fun move(from: Any?, to: Any?) {
        if (from is String && to is String) onAction(MainAction.MoveServer(group.id, from, to))
    }

    if (doubleColumn) {
        val state = if (canReorder) rememberReorderableLazyGridState(scroll.grid) { from, to -> move(from.key, to.key) } else null
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = scroll.grid,
            modifier = Modifier.fillMaxSize().verticalScrollbar(scroll.grid),
            contentPadding = contentPadding
        ) {
            itemsIndexed(rows, key = { _, row -> row.guid }) { index, row ->
                if (state == null) {
                    item(index, row, null, false)
                } else {
                    ReorderableItem(state, key = row.guid, modifier = itemModifier(row.guid)) { dragging ->
                        item(index, row, this, dragging)
                    }
                }
            }
        }
    } else {
        val state = if (canReorder) rememberReorderableLazyListState(scroll.list) { from, to -> move(from.key, to.key) } else null
        LazyColumn(
            state = scroll.list,
            modifier = Modifier.fillMaxSize().verticalScrollbar(scroll.list),
            contentPadding = contentPadding
        ) {
            itemsIndexed(rows, key = { _, row -> row.guid }) { index, row ->
                if (state == null) {
                    item(index, row, null, false)
                } else {
                    ReorderableItem(state, key = row.guid, modifier = itemModifier(row.guid)) { dragging ->
                        item(index, row, this, dragging)
                    }
                }
            }
        }
    }
}

@Composable
private fun LocateTargetEffect(
    target: LocateTarget?, rows: List<ServerRowUiModel>, doubleColumn: Boolean,
    onAction: (MainAction) -> Unit, reveal: suspend (Int) -> Unit
) {
    if (target == null) return
    LaunchedEffect(target, rows, doubleColumn) {
        val index = rows.indexOfFirst { it.guid == target.serverGuid }
        if (index < 0) return@LaunchedEffect
        reveal(index)
        onAction(MainAction.LocateHandled(target))
    }
}

internal enum class ProfileRowAction(val icon: Int, val label: Int, val description: Int = label) {
    More(R.drawable.ic_more_vert_24dp, R.string.action_more, R.string.acc_more),
    Share(R.drawable.ic_share_24dp, R.string.title_configuration_share),
    Edit(R.drawable.ic_edit_24dp, R.string.menu_item_edit_config, R.string.acc_edit),
    Delete(R.drawable.ic_delete_24dp, R.string.menu_item_del_config, R.string.acc_delete)
}

internal val singleColumnProfileActions = listOf(ProfileRowAction.Share, ProfileRowAction.Edit, ProfileRowAction.Delete)
internal val gridProfileActions = listOf(ProfileRowAction.More)

internal fun runProfileRowAction(
    action: ProfileRowAction,
    row: ServerRowUiModel,
    onAction: (MainAction) -> Unit,
    dialogs: MainDialogActions,
    restoreFocus: () -> Boolean = { false }
) {
    when (action) {
        ProfileRowAction.More, ProfileRowAction.Share -> dialogs.share(row.guid, row.profile, action == ProfileRowAction.More, restoreFocus)
        ProfileRowAction.Edit -> onAction(MainAction.EditServer(row.guid, row.profile))
        ProfileRowAction.Delete -> dialogs.remove(row.guid, row.remarks)
    }
}

@Composable
private fun PhoneProfileRow(
    row: ServerRowUiModel,
    selected: Boolean,
    doubleColumn: Boolean,
    onAction: (MainAction) -> Unit,
    dialogs: MainDialogActions
) {
    ProfileRow(
        model = row,
        selected = selected,
        interaction = Modifier.clickable { onAction(MainAction.SelectServer(row.guid)) },
        actions = {
            (if (doubleColumn) gridProfileActions else singleColumnProfileActions).forEach { action ->
                IconButton(
                    onClick = { runProfileRowAction(action, row, onAction, dialogs) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(painterResource(action.icon), stringResource(action.description), Modifier.size(24.dp))
                }
            }
        }
    ) {
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { ProfileAddress(row) }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ProfileType(row.typeDescription, Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            TestResult(row.testDelayMillis)
        }
    }
}

@Composable
private fun ProfileDivider() = AppDivider(Modifier.padding(horizontal = 12.dp))

@Composable
internal fun ProfileRow(
    model: ServerRowUiModel,
    selected: Boolean,
    interaction: Modifier,
    decoration: Modifier = Modifier,
    markerColor: Color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
    actions: @Composable RowScope.() -> Unit,
    details: @Composable () -> Unit
) {
    val direction = LocalLayoutDirection.current
    val selectedDescription = if (selected) stringResource(R.string.acc_selected_server) else null
    Row(
        modifier = Modifier.fillMaxWidth()
            .semantics { if (selectedDescription != null) stateDescription = selectedDescription }
            .then(decoration)
            .drawBehind {
                if (markerColor != Color.Unspecified) {
                    val width = 4.dp.toPx()
                    val verticalInset = 10.dp.toPx()
                    val startInset = 6.dp.toPx()
                    val x = if (direction == LayoutDirection.Rtl) size.width - startInset - width else startInset
                    drawRect(
                        color = markerColor, topLeft = Offset(x, verticalInset),
                        size = Size(width, (size.height - verticalInset * 2).coerceAtLeast(0f))
                    )
                }
            }
            .then(interaction)
    ) {
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    model.remarks, Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Paragraph),
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                actions()
            }
            details()
        }
    }
}

@Composable
internal fun RowScope.ProfileAddress(model: ServerRowUiModel, badgeSpacing: Dp = 0.dp) {
    SubscriptionBadge(model.subscriptionBadge)
    if (model.subscriptionBadge.isNotBlank() && badgeSpacing > 0.dp) Spacer(Modifier.width(badgeSpacing))
    Text(
        model.statistics, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
    )
}

@Composable
internal fun ProfileType(type: String, modifier: Modifier = Modifier) {
    Text(
        type, modifier, style = MaterialTheme.typography.bodySmall, color = colorConfigType,
        maxLines = 1, overflow = TextOverflow.Ellipsis
    )
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
}

@Composable
internal fun TestResult(delayMillis: Long) {
    Text(
        if (delayMillis == 0L) "" else stringResource(R.string.server_test_delay_value, delayMillis),
        style = MaterialTheme.typography.bodySmall,
        color = if (delayMillis < 0L) colorPingRed else colorPing,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
