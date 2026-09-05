package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.ServerRowItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.compose.LocalAppColors
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.ReorderableGridItem
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.verticalScrollbar
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val ServerRowContentType = "server-row"
private val SelectionGutterWidth = 10.dp
private val SelectionBarWidth = 4.dp
private val SelectionBarInset = 6.dp
private val SelectionBarVerticalPadding = 10.dp
private val RowIconSize = 24.dp
private val RowIconButtonSize = 36.dp
private val RowLineSpacing = 6.dp
private val SubscriptionBadgeSize = 24.dp
private const val SubscriptionBadgeAlpha = 0.2f

@Composable
fun GroupPagerPage(
    groupId: String,
    selectedGuid: String?,
    doubleColumnDisplay: Boolean,
    canReorder: Boolean,
    handles: MainScreenHandles,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val rows by remember(groupId, handles) { handles.slices.servers(groupId) }
        .collectAsStateWithLifecycle()
    val callbacks = remember(handles) {
        ServerRowCallbacks(
            onSelect = { guid -> handles.dispatch(MainAction.SelectServer(guid)) },
            onEdit = { guid, type -> handles.dispatch(MainAction.EditServer(guid, type)) },
            onShare = { guid, type -> handles.showDialog(MainDialog.Share(guid, type, false)) },
            onMore = { guid, type -> handles.showDialog(MainDialog.Share(guid, type, true)) },
            onRemove = handles.requestRemove
        )
    }
    val showBadge = groupId.isEmpty()
    if (doubleColumnDisplay) {
        val gridState = remember(handles, groupId) { handles.scrollStates.grid(groupId) }
        val reorderable = if (canReorder) {
            rememberReorderableLazyGridState(gridState) { from, to ->
                handles.dispatch(MainAction.MoveServer(groupId, from.index, to.index))
            }
        } else null
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = modifier.fillMaxSize().verticalScrollbar(gridState),
            contentPadding = contentPadding
        ) {
            items(
                items = rows,
                key = { it.guid },
                contentType = { ServerRowContentType }
            ) { row ->
                val content: @Composable () -> Unit = {
                    Column {
                        ServerRow(row, row.guid == selectedGuid, showBadge, true, callbacks)
                        ItemDivider()
                    }
                }
                if (reorderable != null) {
                    ReorderableItem(reorderable, key = row.guid) { isDragging ->
                        ReorderableGridItem(scope = this, isDragging = isDragging) { content() }
                    }
                } else content()
            }
        }
    } else {
        val listState = remember(handles, groupId) { handles.scrollStates.list(groupId) }
        val reorderable = if (canReorder) {
            rememberReorderableLazyListState(listState) { from, to ->
                handles.dispatch(MainAction.MoveServer(groupId, from.index, to.index))
            }
        } else null
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize().verticalScrollbar(listState),
            contentPadding = contentPadding
        ) {
            items(
                items = rows,
                key = { it.guid },
                contentType = { ServerRowContentType }
            ) { row ->
                val content: @Composable () -> Unit = {
                    Column {
                        ServerRow(row, row.guid == selectedGuid, showBadge, false, callbacks)
                        ItemDivider()
                    }
                }
                if (reorderable != null) {
                    ReorderableItem(reorderable, key = row.guid) { isDragging ->
                        ReorderableListItem(scope = this, isDragging = isDragging) { content() }
                    }
                } else content()
            }
        }
    }
}

@Stable
class ServerRowCallbacks(
    val onSelect: (String) -> Unit,
    val onEdit: (String, EConfigType) -> Unit,
    val onShare: (String, EConfigType) -> Unit,
    val onMore: (String, EConfigType) -> Unit,
    val onRemove: (String) -> Unit
)

@Composable
private fun ServerRow(
    row: ServerRowItem,
    isSelected: Boolean,
    showSubscriptionBadge: Boolean,
    doubleColumnDisplay: Boolean,
    callbacks: ServerRowCallbacks,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = { callbacks.onSelect(row.guid) }
            )
    ) {
        Box(Modifier.width(SelectionGutterWidth).fillMaxHeight()) {
            if (isSelected) {
                Row {
                    Spacer(Modifier.width(SelectionBarInset))
                    Box(
                        Modifier
                            .width(SelectionBarWidth)
                            .fillMaxHeight()
                            .padding(vertical = SelectionBarVerticalPadding)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.remarks,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Paragraph),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (doubleColumnDisplay) {
                    IconButton({ callbacks.onMore(row.guid, row.configType) }, Modifier.size(RowIconButtonSize)) {
                        Icon(
                            painterResource(R.drawable.ic_more_vert_24dp),
                            stringResource(R.string.acc_more),
                            Modifier.size(RowIconSize)
                        )
                    }
                } else {
                    IconButton({ callbacks.onShare(row.guid, row.configType) }, Modifier.size(RowIconButtonSize)) {
                        Icon(
                            painterResource(R.drawable.ic_share_24dp),
                            stringResource(R.string.title_configuration_share),
                            Modifier.size(RowIconSize)
                        )
                    }
                    IconButton({ callbacks.onEdit(row.guid, row.configType) }, Modifier.size(RowIconButtonSize)) {
                        Icon(
                            painterResource(R.drawable.ic_edit_24dp),
                            stringResource(R.string.acc_edit),
                            Modifier.size(RowIconSize)
                        )
                    }
                    IconButton({ callbacks.onRemove(row.guid) }, Modifier.size(RowIconButtonSize)) {
                        Icon(
                            painterResource(R.drawable.ic_delete_24dp),
                            stringResource(R.string.acc_delete),
                            Modifier.size(RowIconSize)
                        )
                    }
                }
            }
            Spacer(Modifier.height(RowLineSpacing))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (showSubscriptionBadge && row.subscriptionBadge.isNotBlank()) {
                    Box(
                        Modifier
                            .size(SubscriptionBadgeSize)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = SubscriptionBadgeAlpha)),
                        Alignment.Center
                    ) {
                        Text(
                            row.subscriptionBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    row.statistics,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(RowLineSpacing))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    row.typeDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val delayText = if (row.testDelayMillis == 0L) {
                    ""
                } else {
                    stringResource(R.string.server_test_delay_value, row.testDelayMillis)
                }
                Text(
                    delayText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.testDelayMillis < 0L) LocalAppColors.current.pingBad
                            else MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
