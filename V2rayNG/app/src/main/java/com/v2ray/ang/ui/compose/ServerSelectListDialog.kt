package com.v2ray.ang.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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
    var searchQuery by rememberSaveable { mutableStateOf("") }
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.menu_item_search)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_search_24dp),
                            contentDescription = stringResource(R.string.acc_search)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
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
                    modifier = Modifier.fillMaxWidth()
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
