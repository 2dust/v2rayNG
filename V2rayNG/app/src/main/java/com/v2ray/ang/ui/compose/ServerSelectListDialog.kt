package com.v2ray.ang.ui.compose

import android.view.ViewTreeObserver.OnGlobalLayoutListener
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField as MaterialOutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.v2ray.ang.R

/**
 * Dialog for selecting a server/profile remark with search filter functionality.
 */
@Composable
fun ServerSelectListDialog(
    options: List<String>,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
    selectedOption: String? = null,
    showRadio: Boolean = true,
) {
    if (isTelevisionDevice()) {
        TvServerSelectListDialog(options, onSelected, onDismiss, title, selectedOption, showRadio)
        return
    }
    var search by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val searchQuery = search.text
    var resumeSearch by rememberSaveable { mutableStateOf(false) }
    val restoreSearch = remember { resumeSearch }
    val filteredOptions = remember(options, searchQuery) {
        if (searchQuery.isBlank()) {
            options
        } else {
            val query = searchQuery.trim()
            options.filter { it.contains(query, ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = {
            val searchFocus = remember { FocusRequester() }
            val view = LocalView.current
            val keyboard = LocalSoftwareKeyboardController.current
            var searchFocused by remember { mutableStateOf(false) }
            DisposableEffect(view, searchFocused) {
                val observer = view.viewTreeObserver
                fun updateSearchState() {
                    resumeSearch = searchFocused &&
                        ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                }
                // Read the dialog window's insets; the Compose IME inset can be zero in this dialog.
                val listener = OnGlobalLayoutListener { updateSearchState() }
                updateSearchState()
                if (searchFocused) observer.addOnGlobalLayoutListener(listener)
                onDispose { if (searchFocused && observer.isAlive) observer.removeOnGlobalLayoutListener(listener) }
            }
            LaunchedEffect(Unit) {
                if (restoreSearch) {
                    withFrameNanos { }
                    if (searchFocus.requestFocus()) keyboard?.show()
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MaterialOutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text(stringResource(R.string.menu_item_search)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_search_24dp),
                            contentDescription = stringResource(R.string.acc_search)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { search = TextFieldValue() }) {
                                Icon(
                                    painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                                    contentDescription = null
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.secondary,
                        selectionColors = TextSelectionColors(
                            handleColor = MaterialTheme.colorScheme.secondary,
                            backgroundColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                        )
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(searchFocus)
                        .onFocusChanged { searchFocused = it.isFocused }
                )

                LazyColumn {
                    items(filteredOptions) { option ->
                        val isSelected = option == selectedOption
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (showRadio) Modifier.selectable(
                                        selected = isSelected,
                                        onClick = { onSelected(option) },
                                        role = Role.RadioButton
                                    ) else Modifier.clickable { onSelected(option) }
                                )
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showRadio) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun TvServerSelectListDialog(
    options: List<String>,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String?,
    selectedOption: String?,
    showRadio: Boolean,
) {
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
    val initialOption = remember { selectedOption.takeIf { showRadio && it in filteredOptions } ?: filteredOptions.firstOrNull() }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = filteredOptions.indexOf(initialOption).coerceAtLeast(0)
    )
    val initialFocus = rememberDpadFocusRequester(requestFocus = initialOption != null)
    val searchFocus = rememberDpadFocusRequester(requestFocus = initialOption == null)
    val clearFocus = remember { FocusRequester() }
    val showClear = searchQuery.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(decorFitsSystemWindows = false),
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
                                searchFocus.requestFocus()
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
                            selected = (option == selectedOption).takeIf { showRadio },
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
