package com.v2ray.ang.ui.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.v2ray.ang.ui.compose.TvQRCodeDialog
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.dpadFocusOutline
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

private enum class Control { Back, Add, Update, Group, Share, Edit, Delete, Enable }
private val toolbar = listOf(Control.Back, Control.Add, Control.Update)
private val row = listOf(Control.Group, Control.Share, Control.Edit, Control.Delete, Control.Enable)
private val localRow = row - Control.Share
private enum class GroupDialog { Update, Share, Delete }

class TvSubSettingActivity : BaseSubSettingActivity() {
    @Composable
    override fun ScreenContent() {
        val subscriptions by viewModel.subsFlow.collectAsStateWithLifecycle()
        val loading by viewModel.isLoading.collectAsStateWithLifecycle()
        val bitmap by viewModel.qrCode.collectAsStateWithLifecycle()
        val groups = subscriptions.orEmpty()
        val ids = remember(groups) { groups.map { it.guid } }
        val top = remember { toolbar.associateWith { FocusRequester() } }
        val back = top.getValue(Control.Back)
        val targets = rememberDpadFocusTargets(ids) { row.associateWith { FocusRequester() } }
        val list = rememberLazyListState()
        // Requesters follow group IDs; their lazy-list indices change when a group moves.
        val focusRows = remember(ids) { ids.map { targets.getValue(it).values.toList() } }
        val request = rememberLazyDpadFocus(focusRows) { list.scrollToItem(it) }
        val reorder = rememberSyncedDpadReorderState(ids) { key, index ->
            list.keepDpadReorderItemVisible(key, index)
            targets[key]?.get(Control.Group)?.requestFocus()
        }
        var control by rememberSaveable { mutableStateOf(Control.Back) }
        var groupId by rememberSaveable { mutableStateOf<String?>(null) }
        var dialog by rememberSaveable { mutableStateOf<GroupDialog?>(null) }
        var dialogId by rememberSaveable { mutableStateOf<String?>(null) }
        // Ignore Compose's first automatic focus (usually Back) until the saved target is attached.
        var restoringFocus by remember { mutableStateOf(true) }
        val inputMode = LocalInputModeManager.current
        val windowFocused = LocalWindowInfo.current.isWindowFocused

        fun mark(slot: Control, id: String? = null) = Modifier.onFocusChanged {
            if (it.isFocused && windowFocused && !restoringFocus) { control = slot; groupId = id }
        }
        fun edit(slot: Control, id: String? = null) {
            control = slot
            groupId = id
            restoringFocus = true
            openEditor(id)
        }
        fun open(which: GroupDialog, id: String? = null) {
            restoringFocus = true
            groupId = id
            control = when (which) {
                GroupDialog.Update -> Control.Update
                GroupDialog.Share -> Control.Share
                GroupDialog.Delete -> Control.Delete
            }
            dialogId = id
            dialog = which
        }
        fun close() { dialog = null; dialogId = null }

        // Restore the same group/action after recreation or a modal, not its old list position.
        LaunchedEffect(subscriptions != null, windowFocused, dialog, bitmap != null) {
            if (subscriptions == null || !windowFocused || dialog != null || bitmap != null) return@LaunchedEffect
            // A new or returning TV window can be in touch mode after an IME or pointer action.
            if (restoringFocus) inputMode.requestInputMode(InputMode.Keyboard)
            val target = targets[groupId]?.get(control)
            if (target != null) request(target) else requestFocusWhenReady(top[control] ?: back)
            restoringFocus = false
        }

        fun belowToolbar() = targets[ids.firstOrNull()]?.get(Control.Group)?.let(request) ?: true
        @Composable
        fun barAction(slot: Control, icon: Int, label: Int, description: Int, click: () -> Unit) {
            val target = top.getValue(slot)
            TvIconButton(painterResource(icon), stringResource(label), click,
                mark(slot).dpadTopBarFocusNavigation(target, top.values.toList(), ::belowToolbar),
                focusRequester = target, contentDescription = stringResource(description))
        }
        Scaffold(topBar = {
            Box(Modifier.padding(horizontal = 48.dp)) {
                AppTopBar(stringResource(R.string.title_sub_setting), ::finish, isLoading = loading, navigationIcon = {
                    barAction(Control.Back, R.drawable.ic_tv_arrow_back_24dp, R.string.action_back, R.string.acc_back, ::finish)
                }, actions = {
                    barAction(Control.Add, R.drawable.ic_add_24dp, R.string.action_add_subscription, R.string.acc_add_subscription) { edit(Control.Add) }
                    barAction(Control.Update, R.drawable.ic_cloud_download_24dp, R.string.title_sub_update, R.string.acc_update_subscriptions) {
                        open(GroupDialog.Update)
                    }
                })
            }
        }) { padding ->
            LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 48.dp).verticalScrollbar(list),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(groups, key = { _, group -> group.guid }) { index, (id, group) ->
                    val actions = if (group.url.isEmpty()) localRow else row
                    val handles = targets.getValue(id)
                    val order = actions.map(handles::getValue)
                    fun adjacent(at: Int, slot: Control): FocusRequester? {
                        val neighbor = groups.getOrNull(at) ?: return null
                        val nextSlot = if (slot == Control.Share && neighbor.subscription.url.isEmpty()) Control.Edit else slot
                        return targets.getValue(neighbor.guid).getValue(nextSlot)
                    }
                    val navigation: @Composable (Control) -> Modifier = { slot ->
                        val target = handles.getValue(slot)
                        mark(slot, id).focusRequester(target)
                            .dpadOrderedFocusNavigation(target, order, onBeforeFirst = { back.requestFocus() })
                            .dpadVerticalFocusNavigation({ request(adjacent(index - 1, slot) ?: back) },
                                { adjacent(index + 1, slot)?.let(request) ?: true })
                    }
                    val move = DpadReorderItem(reorder, id, index, groups.size, ::verticalDpadReorderTarget) { _, to ->
                        viewModel.move(id, ids[to])
                    }
                    TvSubscriptionRow(group, navigation, move, onEdit = { edit(it, id) },
                        onShare = { open(GroupDialog.Share, id) }, onDelete = { open(GroupDialog.Delete, id) },
                        onEnable = { viewModel.setEnabled(id, it) })
                }
            }
        }

        val selected = dialogId?.let { id -> groups.find { it.guid == id } }
        when (dialog) {
            GroupDialog.Update -> {
                val options by viewModel.options.collectAsStateWithLifecycle()
                val cancel = remember { FocusRequester() }
                LaunchedEffect(cancel) { requestFocusWhenReady(cancel) }
                AlertDialog(onDismissRequest = ::close, text = {
                    SubscriptionUpdateOptions(options, viewModel::setOption, remember { TvSettingsRows(cancel) {} })
                }, confirmButton = {
                    TextButton({ close(); viewModel.updateSubscriptions() }, Modifier.dpadFocusOutline(cornerRadius = 24.dp)) {
                        Text(stringResource(R.string.action_ok))
                    }
                }, dismissButton = {
                    TextButton(::close, Modifier.dpadFocusOutline(cancel, cornerRadius = 24.dp)) { Text(stringResource(R.string.action_cancel)) }
                })
            }
            GroupDialog.Share, GroupDialog.Delete -> if (selected != null) {
                if (dialog == GroupDialog.Share) {
                    TvActionDialog(SubscriptionShareAction.entries, { stringResource(it.labelRes) }, onSelected = {
                        close()
                        when (it) {
                            SubscriptionShareAction.QRCode -> viewModel.shareQRCode(selected.subscription.url)
                            SubscriptionShareAction.Clipboard -> copyLink(selected.subscription.url)
                        }
                    }, onDismiss = ::close)
                } else {
                    TvDeleteConfirmDialog(stringResource(R.string.confirm_delete_subscription_group), itemName = selected.subscription.remarks,
                        onDismiss = ::close, onConfirm = {
                            val index = ids.indexOf(selected.guid)
                            groupId = ids.getOrNull(index + 1) ?: ids.getOrNull(index - 1)
                            control = if (groupId == null) Control.Add else Control.Group
                            viewModel.remove(selected.guid)
                            close()
                        })
                }
            } else if (subscriptions != null) {
                LaunchedEffect(dialogId) { close() }
            }
            null -> Unit
        }
        bitmap?.let { TvQRCodeDialog(it, viewModel::dismissQRCode) }
    }
}

@Composable
private fun TvSubscriptionRow(
    group: SubscriptionItem, navigation: @Composable (Control) -> Modifier, move: DpadReorderItem,
    onEdit: (Control) -> Unit, onShare: () -> Unit, onDelete: () -> Unit, onEnable: (Boolean) -> Unit
) {
    val moving = move.state.isMoving(move.key)
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val decoration = when {
        moving -> Modifier.shadow(12.dp, shape).background(MaterialTheme.colorScheme.surfaceContainerHigh, shape).border(2.dp, colorConfigType, shape)
        focused -> Modifier.background(MaterialTheme.colorScheme.primaryContainer, shape).border(2.dp, MaterialTheme.colorScheme.primary, shape)
        else -> Modifier
    }
    Row(Modifier.fillMaxWidth().zIndex(if (moving) 1f else 0f).then(decoration).padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).then(navigation(Control.Group))
            .onFocusChanged { focused = it.isFocused }.semantics(mergeDescendants = true) { contentDescription = group.remarks }
            .dpadLongPressToMove(move) { onEdit(Control.Group) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
            SubscriptionSummary(group, Modifier.clearAndSetSemantics {})
        }
        if (group.url.isNotEmpty()) TvIconButton(painterResource(R.drawable.ic_share_24dp), stringResource(R.string.action_share), onShare,
            navigation(Control.Share), contentDescription = stringResource(R.string.acc_share_subscription))
        TvIconButton(painterResource(R.drawable.ic_edit_24dp), stringResource(R.string.action_edit), { onEdit(Control.Edit) }, navigation(Control.Edit),
            contentDescription = stringResource(R.string.acc_edit))
        TvIconButton(painterResource(R.drawable.ic_delete_24dp), stringResource(R.string.action_delete), onDelete, navigation(Control.Delete),
            contentDescription = stringResource(R.string.acc_delete))
        val label = stringResource(R.string.sub_setting_enable)
        var toggleFocused by remember { mutableStateOf(false) }
        Row(navigation(Control.Enable).heightIn(min = 48.dp).dpadFocusOutline(cornerRadius = 24.dp)
            .onFocusChanged { toggleFocused = it.isFocused }.semantics(mergeDescendants = true) { contentDescription = label }
            .toggleable(group.enabled, role = Role.Switch, onValueChange = onEnable)
            .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(group.enabled, onCheckedChange = null, modifier = Modifier.scale(0.65f))
            if (toggleFocused) Text(label, Modifier.padding(start = 8.dp).clearAndSetSemantics {}, style = MaterialTheme.typography.labelLarge)
        }
    }
}
