package com.v2ray.ang.ui.server

import android.view.ViewConfiguration
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
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
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.LocalOutboundEditorFocus
import com.v2ray.ang.ui.compose.LocalTvEditor
import com.v2ray.ang.ui.compose.tvEditorFocus

internal const val TV_CHAIN_ADD = "chain:add"

@Composable
internal fun TvProxyChainEditor(
    remarks: String, onRemarksChange: (String) -> Unit, isRemarksError: Boolean,
    members: List<String>, memberKeys: List<String>, returnKey: String?, onReturned: () -> Unit,
    onBack: () -> Unit, onSave: () -> Unit, onDelete: (() -> Unit)?, onAdd: () -> Unit,
    onPick: (String) -> Unit, onRemove: (String, String) -> Unit, onMove: (String, Int) -> Unit
) {
    val add = remember { FocusRequester() }
    TvServerEditor(EConfigType.PROXYCHAIN.toString(), onBack, onSave, onDelete, onAdd, add) {
        val focus = checkNotNull(LocalOutboundEditorFocus.current)
        val back = checkNotNull(LocalTvEditor.current).back
        val targets = remember { mutableMapOf<String, ChainRowFocus>() }
        memberKeys.forEach { targets.getOrPut(it) { ChainRowFocus() } }
        SideEffect { targets.keys.retainAll(memberKeys.toSet()) }
        val quiet = remember { tvChainQuietMillis(ViewConfiguration.getKeyRepeatDelay()) }
        LaunchedEffect(returnKey) {
            if (returnKey != null) {
                if (returnKey == TV_CHAIN_ADD) add.requestFocus() else targets[returnKey]?.pick?.requestFocus()
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
                val move = remember { TvChainMove(quiet) }
                val shape = RoundedCornerShape(12.dp)
                val bringIntoView = remember { BringIntoViewRequester() }
                LaunchedEffect(move.moving, index) {
                    if (move.moving) {
                        withFrameNanos { }
                        bringIntoView.bringIntoView()
                    }
                }
                BackHandler(enabled = move.moving) { move.drop() }
                Surface(
                    modifier = Modifier.zIndex(if (move.moving) 1f else 0f)
                        .bringIntoViewRequester(bringIntoView)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .then(if (move.moving) Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, shape) else Modifier),
                    shape = shape,
                    color = if (move.moving) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    shadowElevation = if (move.moving) 8.dp else 0.dp
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
                                .tvEditorFocus(visible = !move.moving)
                                .tvChainMove(move, onMove = { step -> onMove(memberKey, step) }, onClick = { onPick(memberKey) })
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                        IconButton(
                            onClick = { onRemove(memberKey, member) },
                            modifier = focus.target(row.remove, "$memberKey:remove").focusProperties {
                                start = row.pick
                                end = FocusRequester.Cancel
                                if (previous != null) up = previous.remove
                                down = next?.remove ?: add
                            }.tvEditorFocus(24.dp)
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
}
