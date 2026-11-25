package com.v2ray.ang.ui.routing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.LocalOutboundEditorFocus
import com.v2ray.ang.ui.compose.LocalTvEditor
import com.v2ray.ang.ui.compose.TvEditorTextField
import com.v2ray.ang.ui.compose.TvIconButton
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.rememberTvEditorIme
import com.v2ray.ang.ui.compose.requestFocusWhenReady
import com.v2ray.ang.ui.compose.verticalScrollbar
import kotlinx.coroutines.flow.drop

@Composable
internal fun TvRoutingOutboundField(label: String, value: String, options: List<String>, onChange: (String) -> Unit, back: FocusRequester) {
    val anchor = remember { FocusRequester() }
    var open by rememberSaveable { mutableStateOf(false) }
    var restoreAnchor by remember { mutableStateOf(false) }
    fun close() {
        restoreAnchor = true
        open = false
    }
    LaunchedEffect(open) {
        if (!open && restoreAnchor) {
            requestFocusWhenReady(anchor)
            restoreAnchor = false
        }
    }
    OutlinedButton(
        onClick = { open = true },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .then(LocalOutboundEditorFocus.current?.target(anchor) ?: Modifier)
            .focusProperties { start = back }.dpadFocusOutline(cornerRadius = 24.dp)
            .semantics { stateDescription = value }
    ) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            if (value.isNotBlank()) Text(value, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.bodyLarge)
        }
        Icon(painterResource(R.drawable.ic_expand_more_24dp), contentDescription = null)
    }
    if (open) RoutingOutboundOptions(label, value, options, ::close) {
        onChange(it)
        close()
    }
}

@Composable
private fun RoutingOutboundOptions(label: String, value: String, options: List<String>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val trimmedQuery = query.trim()
    val filtered = remember(options, trimmedQuery) {
        if (trimmedQuery.isEmpty()) options else options.filter { it.contains(trimmedQuery, ignoreCase = true) }
    }
    val initialOption = remember { value.takeIf { it in filtered } ?: filtered.firstOrNull() }
    // When the dialog is recreated, its focus target and viewport must start on the same option.
    val listState = remember { LazyListState(filtered.indexOf(initialOption).coerceAtLeast(0), 0) }
    val selected = remember { FocusRequester() }
    val cancel = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        // Android TV must deliver IME insets to this dialog so Back restores passive search focus.
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text(label) },
        text = {
            val ime = rememberTvEditorIme()
            val focusManager = LocalFocusManager.current
            LaunchedEffect(Unit) { requestFocusWhenReady(if (initialOption == null) cancel else selected) }
            LaunchedEffect(listState) {
                snapshotFlow { query.trim() }.drop(1).collect { listState.scrollToItem(0) }
            }
            Column(Modifier.then(ime.padding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Keep dialog focus out of the editor's saved focus registry.
                    CompositionLocalProvider(LocalOutboundEditorFocus provides null, LocalTvEditor provides cancel) {
                        TvEditorTextField(stringResource(R.string.menu_item_search), query, { query = it },
                            Modifier.weight(1f), true, KeyboardType.Text, null, 1, false, null)
                    }
                    if (query.isNotEmpty()) {
                        TvIconButton(painterResource(android.R.drawable.ic_menu_close_clear_cancel), stringResource(R.string.action_clear),
                            onClick = { focusManager.moveFocus(FocusDirection.Previous); query = "" })
                    }
                }
                LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false).verticalScrollbar(listState)) {
                    items(filtered, key = { it }) { option ->
                        val checked = option == value
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                .dpadFocusOutline(focusRequester = selected.takeIf { option == initialOption })
                                .selectable(selected = checked, role = Role.RadioButton, onClick = { onSelect(option) })
                                .padding(end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(checked, onClick = null, modifier = Modifier.padding(12.dp))
                            Text(option, modifier = Modifier.padding(vertical = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.dpadFocusOutline(focusRequester = cancel, cornerRadius = 24.dp)) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
