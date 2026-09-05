package com.v2ray.ang.ui.subscription

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseScreen
import com.v2ray.ang.ui.compose.AppTheme
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.verticalScrollbar
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val CONTENT_TYPE_SUB = "subscription"
private const val SwitchScale = 0.8f
private val RowHorizontalPad = 14.dp
private val RowLineGap = 2.dp
private val RowActionsStartPad = 8.dp
private val SwitchTopGap = 4.dp

@Composable
fun SubSettingScreen(viewModel: SubSettingViewModel) {
    val onAction = remember(viewModel) { viewModel::onAction }
    val dialogs = rememberSubDialogHost(onAction)

    BackHandler { onAction(SubAction.Back) }

    BaseScreen(
        viewModel = viewModel,
        showLoading = false,
        onEvent = { event ->
            when (event) {
                SubEvent.ShowUpdateOptions -> {
                    dialogs.show(SubDialog.UpdateOptions)
                    true
                }
                is SubEvent.ShowShare -> {
                    dialogs.show(SubDialog.Share(event.url))
                    true
                }
                is SubEvent.ShowQrCode -> {
                    dialogs.show(SubDialog.QrCode(event.bitmap))
                    true
                }
                SubEvent.CloseUpdateOptions -> {
                    dialogs.dismiss()
                    true
                }
                else -> false
            }
        },
        onResult = { result -> onAction(SubAction.ResultReceived(result)) },
        topBar = {
            val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
            val progress by viewModel.updateProgress.collectAsStateWithLifecycle()
            SubSettingTopBar(
                isLoading = isLoading,
                progress = progress?.fraction,
                onAction = onAction
            )
        }
    ) { state, dispatch ->
        SideEffect { dialogs.confirmRemove = state.confirmRemove }

        val callbacks = remember(dispatch, dialogs) {
            SubRowCallbacks(
                onShare = { url -> dispatch(SubAction.ShareClicked(url)) },
                onEdit = { guid -> dispatch(SubAction.Edit(guid)) },
                onRemove = dialogs.requestRemove,
                onToggle = { guid, enabled -> dispatch(SubAction.ToggleEnabled(guid, enabled)) }
            )
        }

        SubSettingContent(
            subscriptions = state.subscriptions,
            callbacks = callbacks,
            onMove = { fromId, toId -> dispatch(SubAction.Move(fromId, toId)) }
        )

        SubDialogs(host = dialogs, updateOptions = state.updateOptions, onAction = dispatch)
    }
}

@Composable
private fun SubSettingTopBar(
    isLoading: Boolean,
    progress: Float?,
    onAction: (SubAction) -> Unit
) {
    AppTopBar(
        title = stringResource(R.string.title_sub_setting),
        onBackClick = { onAction(SubAction.Back) },
        isLoading = isLoading,
        progress = progress,
        actions = {
            IconButton(onClick = { onAction(SubAction.Add) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_add_24dp),
                    contentDescription = stringResource(R.string.menu_item_add_config)
                )
            }
            IconButton(onClick = { onAction(SubAction.OpenUpdateOptions) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_restore_24dp),
                    contentDescription = stringResource(R.string.title_sub_update)
                )
            }
        }
    )
}

@Composable
fun SubSettingContent(
    subscriptions: List<SubRow>,
    callbacks: SubRowCallbacks,
    onMove: (fromId: String, toId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromId = from.key as? String ?: return@rememberReorderableLazyListState
        val toId = to.key as? String ?: return@rememberReorderableLazyListState
        onMove(fromId, toId)
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier.fillMaxSize().verticalScrollbar(lazyListState),
        contentPadding = NavigationBarsBottomPadding()
    ) {
        items(
            items = subscriptions,
            key = { it.guid },
            contentType = { CONTENT_TYPE_SUB }
        ) { subRow ->
            ReorderableItem(reorderableState, key = subRow.guid) { isDragging ->
                ReorderableListItem(scope = this, isDragging = isDragging) {
                    SubscriptionRow(
                        subRow = subRow,
                        callbacks = callbacks,
                        modifier = Modifier.weight(1f)
                    )
                }
                ItemDivider()
            }
        }
    }
}

@Composable
private fun SubscriptionRow(
    subRow: SubRow,
    callbacks: SubRowCallbacks,
    modifier: Modifier = Modifier
) {
    val guid = subRow.guid
    val url = subRow.url
    val lastUpdated = subRow.lastUpdatedText.ifEmpty { "" }

    Row(
        modifier = modifier.padding(horizontal = RowHorizontalPad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = subRow.remarks,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subRow.hasUrl) {
                Spacer(modifier = Modifier.height(RowLineGap))
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(RowLineGap))
            Text(
                text = lastUpdated,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(start = RowActionsStartPad)
        ) {
            Row {
                if (subRow.hasUrl) {
                    IconButton(onClick = { callbacks.onShare(url) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_share_24dp),
                            contentDescription = stringResource(R.string.acc_share_subscription)
                        )
                    }
                }
                IconButton(onClick = { callbacks.onEdit(guid) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit_24dp),
                        contentDescription = stringResource(R.string.acc_edit)
                    )
                }
                IconButton(onClick = { callbacks.onRemove(guid) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete_24dp),
                        contentDescription = stringResource(R.string.acc_delete)
                    )
                }
            }
            Spacer(modifier = Modifier.height(SwitchTopGap))
            Switch(
                checked = subRow.enabled,
                onCheckedChange = { enabled -> callbacks.onToggle(guid, enabled) },
                modifier = Modifier.minimumInteractiveComponentSize().scale(SwitchScale),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                    checkedTrackColor = MaterialTheme.colorScheme.secondary
                )
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SubSettingContentPreview() = AppTheme {
    SubSettingContent(
        subscriptions = listOf(
            SubRow("1", "Primary", "https://example.com/sub", "2026-05-21 09:30", true),
            SubRow("2", "Local only", "", "", false)
        ),
        callbacks = SubRowCallbacks({}, {}, {}, { _, _ -> }),
        onMove = { _, _ -> }
    )
}

@Preview(showBackground = true)
@Composable
private fun SubSettingEmptyPreview() = AppTheme {
    SubSettingContent(
        subscriptions = emptyList(),
        callbacks = SubRowCallbacks({}, {}, {}, { _, _ -> }),
        onMove = { _, _ -> }
    )
}
