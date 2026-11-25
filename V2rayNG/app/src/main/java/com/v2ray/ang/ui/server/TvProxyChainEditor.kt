package com.v2ray.ang.ui.server

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.v2ray.ang.R
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.compose.DpadReorderItem
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.LocalEditorFocus
import com.v2ray.ang.ui.compose.LocalTvEditor
import com.v2ray.ang.ui.compose.TvSearchableOptionsDialog
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.dpadLongPressToMove
import com.v2ray.ang.ui.compose.rememberDpadFocusTargets
import com.v2ray.ang.ui.compose.rememberSyncedDpadReorderState
import com.v2ray.ang.ui.compose.verticalDpadReorderTarget

private const val ADD_MEMBER = "chain:add"

@Composable
internal fun TvProxyChainEditor(
    remarks: String, onRemarksChange: (String) -> Unit, isRemarksError: Boolean,
    members: List<String>, memberKeys: List<String>, allRemarks: List<String>,
    onBack: () -> Unit, onSave: () -> Unit, onDelete: (() -> Unit)?, onAdd: () -> String,
    onSelect: (String, String) -> Unit, onRemove: (String) -> Unit, onMove: (String, Int) -> Unit
) {
    var returnKey by remember { mutableStateOf<String?>(null) }
    val remove: (String) -> Unit = { key ->
        val index = memberKeys.indexOf(key)
        if (index >= 0) {
            returnKey = memberKeys.getOrNull(index + 1) ?: memberKeys.getOrNull(index - 1) ?: ADD_MEMBER
            onRemove(key)
        }
    }
    ProxyChainMemberDialogs(members, memberKeys, remove, onSelect, onClosePicker = { returnKey = it }, picker = { selected, select, dismiss ->
        TvSearchableOptionsDialog(stringResource(R.string.server_proxy_chain_member_unselected), allRemarks, selected, select, dismiss)
    }) { pick, requestRemove ->
        TvProxyChainLayout(remarks, onRemarksChange, isRemarksError, members, memberKeys, returnKey, { returnKey = null },
            onBack, onSave, onDelete, { returnKey = onAdd() }, pick, requestRemove, onMove)
    }
}

@Composable
private fun TvProxyChainLayout(
    remarks: String, onRemarksChange: (String) -> Unit, isRemarksError: Boolean,
    members: List<String>, memberKeys: List<String>, returnKey: String?, onReturned: () -> Unit,
    onBack: () -> Unit, onSave: () -> Unit, onDelete: (() -> Unit)?, onAdd: () -> Unit,
    onPick: (String) -> Unit, onRemove: (String) -> Unit, onMove: (String, Int) -> Unit
) {
    val add = remember { FocusRequester() }
    TvServerEditor(EConfigType.PROXYCHAIN.toString(), onBack, onSave, onDelete, onAdd, add) {
        val focus = checkNotNull(LocalEditorFocus.current)
        val back = checkNotNull(LocalTvEditor.current)
        val targets = rememberDpadFocusTargets(memberKeys, ::ChainRowFocus)
        val reorder = rememberSyncedDpadReorderState(memberKeys) { key, _ ->
            if (key is String) targets[key]?.bringIntoView?.bringIntoView()
        }
        BackHandler(enabled = reorder.isMoving, onBack = reorder::cancelInteraction)
        LaunchedEffect(returnKey) {
            if (returnKey != null) {
                if (returnKey == ADD_MEMBER) add.requestFocus() else targets[returnKey]?.pick?.requestFocus()
                onReturned()
            }
        }
        FormTextField(stringResource(R.string.server_lab_remarks), remarks, onRemarksChange, isError = isRemarksError)
        Text(stringResource(R.string.server_proxy_chain_members), modifier = Modifier.padding(start = 16.dp))
        memberKeys.forEachIndexed { index, memberKey ->
            key(memberKey) {
                val member = members[index]
                val row = targets.getValue(memberKey)
                val previous = targets[memberKeys.getOrNull(index - 1)]
                val next = targets[memberKeys.getOrNull(index + 1)]
                val moving = reorder.isMoving(memberKey)
                val reorderItem = DpadReorderItem(reorder, memberKey, index, memberKeys.size, ::verticalDpadReorderTarget) { from, to ->
                    onMove(memberKey, to - from)
                }
                val shape = RoundedCornerShape(12.dp)
                Surface(
                    modifier = Modifier.zIndex(if (moving) 1f else 0f)
                        .bringIntoViewRequester(row.bringIntoView)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .then(if (moving) Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, shape) else Modifier),
                    shape = shape,
                    color = if (moving) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    shadowElevation = if (moving) 8.dp else 0.dp
                ) {
                    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = member.ifBlank { stringResource(R.string.server_proxy_chain_member_unselected) },
                            modifier = Modifier.weight(1f).then(focus.target(row.pick, memberKey))
                                .focusProperties {
                                    start = back
                                    end = row.remove
                                    if (previous != null) up = previous.pick
                                    down = next?.pick ?: add
                                }
                                .dpadFocusOutline(cornerRadius = 8.dp, showFocus = !moving)
                                .dpadLongPressToMove(reorderItem) { onPick(memberKey) }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                        IconButton(
                            onClick = { onRemove(memberKey) },
                            modifier = focus.target(row.remove, "$memberKey:remove").focusProperties {
                                start = row.pick
                                end = FocusRequester.Cancel
                                if (previous != null) up = previous.remove
                                down = next?.remove ?: add
                            }.dpadFocusOutline(cornerRadius = 24.dp)
                        ) {
                            Icon(painterResource(R.drawable.ic_delete_24dp), stringResource(R.string.acc_remove))
                        }
                    }
                }
            }
        }
    }
}

private class ChainRowFocus {
    val pick = FocusRequester()
    val remove = FocusRequester()
    val bringIntoView = BringIntoViewRequester()
}
