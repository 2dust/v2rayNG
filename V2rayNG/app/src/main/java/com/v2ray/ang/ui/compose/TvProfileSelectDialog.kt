package com.v2ray.ang.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.v2ray.ang.R

@Composable
internal fun TvProfileSelectDialog(
    title: String, options: List<String>, selected: String?, onSelect: (String) -> Unit, onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(query, options) { matchingProfileRemarks(options, query) }
    var focusedTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val restoredTarget = remember { focusedTarget }
    val targetOption = remember {
        if (restoredTarget == null) selected?.takeIf { it in matches } ?: matches.firstOrNull()
        else restoredTarget.takeIf { it.startsWith(RESULT_FOCUS_PREFIX) }?.removePrefix(RESULT_FOCUS_PREFIX)
    }
    val search = remember { FocusRequester() }
    val result = remember { FocusRequester() }
    val cancel = remember { FocusRequester() }
    val initialIndex = remember { matches.indexOf(targetOption) }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex.coerceAtLeast(0))
    AlertDialog(
        onDismissRequest = onDismiss,
        // Let the search field observe the dialog window’s IME insets after recreation.
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text(title) },
        text = {
            val ime = rememberTvEditorIme()
            val inputMode = LocalInputModeManager.current
            CompositionLocalProvider(LocalTvEditor provides cancel, LocalEditorFocus provides null) {
                Column(ime.padding) {
                    TvEditorTextField(stringResource(R.string.menu_item_search), query, { query = it },
                        Modifier.focusRequester(search).onFocusChanged { if (it.hasFocus) focusedTarget = SEARCH_FOCUS },
                        enabled = true, keyboardType = KeyboardType.Text, placeholder = null, maxLines = 1,
                        isError = false, supportingText = null)
                    LazyColumn(state = list, modifier = Modifier.heightIn(max = 264.dp)) {
                        items(matches, key = { it }) { option ->
                            val focus = if (option == targetOption) Modifier.focusRequester(result) else Modifier
                            Row(
                                focus.fillMaxWidth().dpadFocusOutline(cornerRadius = 8.dp)
                                    .onFocusChanged { if (it.isFocused) focusedTarget = RESULT_FOCUS_PREFIX + option }
                                    .selectable(option == selected, role = Role.RadioButton, onClick = { onSelect(option) })
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = option == selected, onClick = null, modifier = Modifier.padding(end = 12.dp))
                                Text(option)
                            }
                        }
                    }
                }
            }
            LaunchedEffect(Unit) {
                withFrameNanos { }
                if (restoredTarget != null) inputMode.requestInputMode(InputMode.Keyboard)
                when (restoredTarget) {
                    SEARCH_FOCUS -> search.requestFocus()
                    CANCEL_FOCUS -> cancel.requestFocus()
                    else -> {
                        if (initialIndex >= 0 && list.layoutInfo.visibleItemsInfo.none { it.key == targetOption }) {
                            list.scrollToItem(initialIndex)
                        }
                        if ((initialIndex < 0 || !result.requestFocus()) && !search.requestFocus()) cancel.requestFocus()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.focusRequester(cancel).dpadFocusOutline(cornerRadius = 24.dp)
                    .onFocusChanged { if (it.isFocused) focusedTarget = CANCEL_FOCUS }
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

private const val SEARCH_FOCUS = "search"
private const val CANCEL_FOCUS = "cancel"
private const val RESULT_FOCUS_PREFIX = "result:"

internal fun matchingProfileRemarks(options: List<String>, query: String): List<String> {
    val text = query.trim()
    return if (text.isEmpty()) options else options.filter { it.contains(text, ignoreCase = true) }
}
