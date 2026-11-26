package com.v2ray.ang.ui.subscription

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DpadReorderItem
import com.v2ray.ang.ui.compose.TvActionDialog
import com.v2ray.ang.ui.compose.TvDeleteConfirmDialog
import com.v2ray.ang.ui.compose.TvIconButton
import com.v2ray.ang.ui.compose.TvListRowSwitch
import com.v2ray.ang.ui.compose.tvListRowDecoration
import com.v2ray.ang.ui.compose.TvQRCodeDialog
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.focusAfterRemoval
import com.v2ray.ang.ui.compose.dpadLongPressToMove
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.dpadTopBarFocusNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.keepDpadReorderItemVisible
import com.v2ray.ang.ui.compose.rememberDpadFocusTargets
import com.v2ray.ang.ui.compose.rememberLazyDpadFocus
import com.v2ray.ang.ui.compose.rememberSyncedDpadReorderState
import com.v2ray.ang.ui.compose.requestFocusWhenReady
import com.v2ray.ang.ui.compose.verticalDpadReorderTarget
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.ui.settings.TvSettingsRows

internal enum class Control { Back, Add, Update, Group, Refresh, Share, Edit, Delete, Enable }
private val toolbar = listOf(Control.Back, Control.Add, Control.Update)
private val row = listOf(Control.Group, Control.Refresh, Control.Share, Control.Edit, Control.Delete, Control.Enable)
private val localRow = row - setOf(Control.Refresh, Control.Share)

internal fun SubscriptionItem.rowControls() = if (url.isEmpty()) localRow else row
internal fun Control.onRow(available: Set<Control>) = if ((this == Control.Refresh || this == Control.Share) && this !in available) Control.Edit else this

class TvSubSettingActivity : BaseSubSettingActivity() {
    @Composable
    override fun SubscriptionListContent() {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val loading by viewModel.isLoading.collectAsStateWithLifecycle()
        val bitmap by viewModel.qrCode.collectAsStateWithLifecycle()
        val groups = state.groups.orEmpty()
        val ids = remember(groups) { groups.map { it.guid } }
        val toolbarFocus = remember { toolbar.associateWith { FocusRequester() } }
        val back = toolbarFocus.getValue(Control.Back)
        val rowFocus = rememberDpadFocusTargets(ids) { row.associateWith { FocusRequester() } }
        val visibleRowFocus = remember(groups) {
            groups.associate { (id, group) -> id to group.rowControls().associateWith(rowFocus.getValue(id)::getValue) }
        }
        val listState = rememberLazyListState()
        // Requesters follow group IDs; their lazy-list indices change when a group moves.
        val focusRows = remember(ids, visibleRowFocus) { ids.map { visibleRowFocus.getValue(it).values.toList() } }
        val requestRow = rememberLazyDpadFocus(focusRows) { listState.scrollToItem(it) }
        fun targetFor(id: String?, slot: Control) = visibleRowFocus[id]?.let { it[slot.onRow(it.keys)] }
        val reorder = rememberSyncedDpadReorderState(ids) { key, index ->
            listState.keepDpadReorderItemVisible(key, index)
            visibleRowFocus[key]?.get(Control.Group)?.requestFocus()
        }
        var focusedControl by rememberSaveable { mutableStateOf(Control.Back) }
        var focusedGroupId by rememberSaveable { mutableStateOf<String?>(null) }
        var dialogOpen by rememberSaveable { mutableStateOf(false) }
        // Ignore Compose's first automatic focus (usually Back) until the saved target is attached.
        var restoringFocus by remember { mutableStateOf(true) }
        val inputMode = LocalInputModeManager.current
        val windowFocused = LocalWindowInfo.current.isWindowFocused

        fun mark(slot: Control, id: String? = null) = Modifier.onFocusChanged {
            if (it.isFocused && windowFocused && !restoringFocus) { focusedControl = slot; focusedGroupId = id }
        }
        fun returnTo(slot: Control, id: String?) {
            focusedControl = slot
            focusedGroupId = id
            restoringFocus = true
        }
        fun edit(slot: Control, id: String? = null) {
            returnTo(slot, id)
            openEditor(id)
        }
        fun openDialog(slot: Control, id: String? = null) {
            returnTo(slot, id)
            dialogOpen = true
        }

        // Restore the same group/action after recreation or a modal, not its old listState position.
        val loaded = state.groups != null
        LaunchedEffect(loaded, windowFocused, dialogOpen, bitmap != null) {
            if (!loaded || !windowFocused || dialogOpen || bitmap != null) return@LaunchedEffect
            // A new or returning TV window can be in touch mode after an IME or pointer action.
            if (restoringFocus) inputMode.requestInputMode(InputMode.Keyboard)
            val target = targetFor(focusedGroupId, focusedControl)
            if (target != null) requestRow(target) else requestFocusWhenReady(toolbarFocus[focusedControl] ?: back)
            restoringFocus = false
        }

        fun belowToolbar() = visibleRowFocus[ids.firstOrNull()]?.get(Control.Group)?.let(requestRow) ?: true
        @Composable
        fun barAction(slot: Control, icon: Int, label: Int, description: Int, click: () -> Unit) {
            val target = toolbarFocus.getValue(slot)
            TvIconButton(painterResource(icon), stringResource(label), click,
                mark(slot).dpadTopBarFocusNavigation(target, toolbarFocus.values.toList(), ::belowToolbar),
                focusRequester = target, contentDescription = stringResource(description))
        }
        Scaffold(topBar = {
            Box(Modifier.padding(horizontal = 48.dp)) {
                AppTopBar(stringResource(R.string.title_sub_setting), ::finish, isLoading = loading, navigationIcon = {
                    barAction(Control.Back, R.drawable.ic_tv_arrow_back_24dp, R.string.action_back, R.string.acc_back, ::finish)
                }, actions = {
                    barAction(Control.Add, R.drawable.ic_add_24dp, R.string.action_add_subscription, R.string.acc_add_subscription) { edit(Control.Add) }
                    barAction(Control.Update, R.drawable.ic_cloud_download_24dp, R.string.title_sub_update, R.string.acc_update_subscriptions) {
                        openDialog(Control.Update)
                    }
                })
            }
        }) { padding ->
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 48.dp).verticalScrollbar(listState),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(groups, key = { _, group -> group.guid }) { index, (id, group) ->
                    val handles = visibleRowFocus.getValue(id)
                    val order = handles.values.toList()
                    val navigation: @Composable (Control) -> Modifier = { slot ->
                        val target = handles.getValue(slot)
                        mark(slot, id).focusRequester(target)
                            .dpadOrderedFocusNavigation(target, order, onBeforeFirst = { back.requestFocus() })
                            .dpadVerticalFocusNavigation({ requestRow(targetFor(ids.getOrNull(index - 1), slot) ?: back) },
                                { targetFor(ids.getOrNull(index + 1), slot)?.let(requestRow) ?: true })
                    }
                    val move = DpadReorderItem(reorder, id, index, groups.size, ::verticalDpadReorderTarget) { _, to ->
                        viewModel.move(id, ids[to])
                    }
                    TvSubscriptionRow(group, navigation, move, onEdit = { edit(it, id) },
                        onRefresh = { openDialog(Control.Refresh, id) },
                        onShare = { openDialog(Control.Share, id) }, onDelete = { openDialog(Control.Delete, id) },
                        onEnable = { viewModel.setEnabled(id, it) })
                }
            }
        }

        if (dialogOpen && loaded) {
            val dismiss = { dialogOpen = false }
            val selected = focusedGroupId?.let { id -> groups.find { it.guid == id } }
            when {
                focusedControl == Control.Update -> TvUpdateSubscriptionsDialog(state.updateOptions, viewModel::setOption,
                    onConfirm = { dismiss(); viewModel.updateSubscriptions() }, onDismiss = dismiss)
                selected == null || focusedControl !in visibleRowFocus[selected.guid].orEmpty() -> LaunchedEffect(Unit) { dismiss() }
                focusedControl == Control.Refresh -> TvUpdateSubscriptionsDialog(state.updateOptions, viewModel::setOption,
                    onConfirm = { dismiss(); viewModel.updateSubscriptions(selected.guid) }, onDismiss = dismiss)
                focusedControl == Control.Share -> TvActionDialog(SubscriptionShareAction.entries, { stringResource(it.labelRes) },
                    onSelected = { dismiss(); shareLink(it, selected.subscription.url) }, onDismiss = dismiss)
                focusedControl == Control.Delete -> TvDeleteConfirmDialog(stringResource(R.string.confirm_delete_subscription_group),
                    itemName = selected.subscription.remarks, onDismiss = dismiss, onConfirm = {
                        val next = focusAfterRemoval(ids, selected.guid)
                        returnTo(if (next == null) Control.Add else Control.Group, next)
                        viewModel.remove(selected.guid)
                        dismiss()
                    })
            }
        }
        bitmap?.let { TvQRCodeDialog(it, viewModel::dismissQRCode) }
    }
}

@Composable
private fun TvUpdateSubscriptionsDialog(
    options: Set<SubscriptionUpdateOption>, onChange: (SubscriptionUpdateOption, Boolean) -> Unit,
    onConfirm: () -> Unit, onDismiss: () -> Unit
) {
    val cancel = remember { FocusRequester() }
    LaunchedEffect(cancel) { requestFocusWhenReady(cancel) }
    AlertDialog(onDismissRequest = onDismiss, text = {
        SubscriptionUpdateOptions(options, onChange, remember { TvSettingsRows(cancel) {} })
    }, confirmButton = {
        TextButton(onConfirm, Modifier.dpadFocusOutline(cornerRadius = 24.dp)) { Text(stringResource(R.string.action_ok)) }
    }, dismissButton = {
        TextButton(onDismiss, Modifier.dpadFocusOutline(cancel, cornerRadius = 24.dp)) { Text(stringResource(R.string.action_cancel)) }
    })
}

@Composable
private fun TvSubscriptionRow(
    group: SubscriptionItem, navigation: @Composable (Control) -> Modifier, move: DpadReorderItem,
    onEdit: (Control) -> Unit, onRefresh: () -> Unit, onShare: () -> Unit,
    onDelete: () -> Unit, onEnable: (Boolean) -> Unit
) {
    val moving = move.state.isMoving(move.key)
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().zIndex(if (moving) 1f else 0f).tvListRowDecoration(focused, moving).padding(end = 8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            for (slot in group.rowControls()) key(slot) {
                val modifier = navigation(slot)
                when (slot) {
                    Control.Group -> Box(Modifier.weight(1f).then(modifier).onFocusChanged { focused = it.isFocused }
                        .semantics(mergeDescendants = true) { contentDescription = group.remarks }
                        .dpadLongPressToMove(move) { onEdit(slot) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        SubscriptionSummary(group, Modifier.clearAndSetSemantics {})
                    }
                    Control.Refresh -> TvIconButton(painterResource(R.drawable.ic_refresh_24dp), stringResource(R.string.title_sub_update),
                        onRefresh, modifier)
                    Control.Share -> TvIconButton(painterResource(R.drawable.ic_share_24dp), stringResource(R.string.action_share), onShare,
                        modifier, contentDescription = stringResource(R.string.acc_share_subscription))
                    Control.Edit -> TvIconButton(painterResource(R.drawable.ic_edit_24dp), stringResource(R.string.action_edit), { onEdit(slot) },
                        modifier, contentDescription = stringResource(R.string.acc_edit))
                    Control.Delete -> TvIconButton(painterResource(R.drawable.ic_delete_24dp), stringResource(R.string.action_delete), onDelete,
                        modifier, contentDescription = stringResource(R.string.acc_delete))
                    Control.Enable -> TvListRowSwitch(stringResource(R.string.sub_setting_enable), group.enabled, onEnable, modifier)
                    else -> error("Not a subscription row control: $slot")
                }
            }
        }
        SubscriptionMetadata(group, Modifier.padding(start = 16.dp, end = 8.dp, bottom = 8.dp))
    }
}
