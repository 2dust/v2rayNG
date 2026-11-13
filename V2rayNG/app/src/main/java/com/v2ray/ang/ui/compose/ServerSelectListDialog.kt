package com.v2ray.ang.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

/** A searchable picker for the remark/tag values already stored by the editors. */
@Composable
fun ServerSelectListDialog(
    options: List<String>,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
    selectedOption: String? = null,
) {
    val isTelevision = isTelevisionDevice()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val uniqueOptions = remember(options) { options.distinct() }
    val filteredOptions = remember(uniqueOptions, searchQuery) {
        if (searchQuery.isBlank()) {
            uniqueOptions
        } else {
            val query = searchQuery.trim()
            uniqueOptions.filter { it.contains(query, ignoreCase = true) }
        }
    }
    val initialOption = remember { filteredOptions.firstOrNull { it == selectedOption } ?: filteredOptions.firstOrNull() }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = filteredOptions.indexOf(initialOption).coerceAtLeast(0)
    )
    val initialFocus = rememberDpadFocusRequester(requestFocus = initialOption != null)
    val searchFocus = rememberDpadFocusRequester(requestFocus = initialOption == null)
    val clearFocus = remember { FocusRequester() }
    val showClear = searchQuery.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = {
            Column(
                modifier = Modifier.tvAwareImePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FormTextField(
                        label = stringResource(R.string.menu_item_search),
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f).dpadPopupHorizontalNavigation(
                            onMoveNext = if (showClear) ({ clearFocus.requestFocus() }) else null
                        ),
                        singleLine = true,
                        tvNavigation = TvTextFieldNavigation(focusRequester = searchFocus)
                    )
                    if (showClear) {
                        AppIconButton(
                            icon = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                            label = stringResource(R.string.action_clear),
                            focusRequester = clearFocus,
                            modifier = Modifier.dpadMovePreviousNavigation { searchFocus.requestFocus() },
                            onClick = {
                                if (isTelevision) searchFocus.requestFocus()
                                searchQuery = ""
                            }
                        )
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f, fill = false).verticalScrollbar(listState)
                ) {
                    items(filteredOptions, key = { it }) { option ->
                        DialogOptionRow(
                            text = option,
                            selected = option == selectedOption,
                            onClick = { onSelected(option) },
                            modifier = Modifier.dpadMovePreviousNavigation { searchFocus.requestFocus() },
                            focusRequester = initialFocus.takeIf { option == initialOption },
                            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            AppDialogButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
internal fun ServerSelectButton(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    focusRequester: FocusRequester? = null,
    showFocus: Boolean = true,
) {
    val displayValue = value.ifBlank { placeholder }
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .dpadFocusOutline(focusRequester, cornerRadius = 8.dp, showFocus = showFocus)
            .semantics {
                contentDescription = label
                stateDescription = displayValue
            }
            .dpadClickable(role = Role.Button, onClick = onClick)
            .then(if (isTelevisionDevice()) Modifier.heightIn(min = 48.dp) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = displayValue,
            style = MaterialTheme.typography.bodyMedium,
            color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (value.isBlank()) FontWeight.Normal else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}
