package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.DeleteConfirmDialog

@Composable
fun SubscriptionImportDialog(name: String, onAction: (MainAction) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = { onAction(MainAction.CancelSubscriptionImport) },
        title = { Text(stringResource(R.string.title_sub_setting)) },
        text = {
            Column(
                Modifier
                    .consumeWindowInsets(WindowInsets.navigationBars)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { onAction(MainAction.ChangeSubscriptionImportName(it)) },
                    label = { Text(stringResource(R.string.sub_import_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onAction(MainAction.ConfirmSubscriptionImport) }
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = { onAction(MainAction.CancelSubscriptionImport) }) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun MainDialogs(
    showDelAllConfirm: Boolean,
    onDismissDelAll: () -> Unit,
    onConfirmDelAll: () -> Unit,
    showDelDuplicateConfirm: Boolean,
    onDismissDelDuplicate: () -> Unit,
    onConfirmDelDuplicate: () -> Unit,
    showDelInvalidConfirm: Boolean,
    onDismissDelInvalid: () -> Unit,
    onConfirmDelInvalid: () -> Unit,
    showRemoveConfirm: String?,
    onDismissRemove: () -> Unit,
    onConfirmRemove: (String) -> Unit,
) {
    if (showDelAllConfirm) {
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_visible_profiles),
            onConfirm = onConfirmDelAll,
            onDismiss = onDismissDelAll
        )
    }
    if (showDelDuplicateConfirm) {
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_duplicate_profiles),
            onConfirm = onConfirmDelDuplicate,
            onDismiss = onDismissDelDuplicate
        )
    }
    if (showDelInvalidConfirm) {
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_invalid_profiles),
            onConfirm = onConfirmDelInvalid,
            onDismiss = onDismissDelInvalid
        )
    }
    if (showRemoveConfirm != null) {
        val guid = showRemoveConfirm
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_profile),
            onConfirm = { onConfirmRemove(guid) },
            onDismiss = onDismissRemove
        )
    }
}
