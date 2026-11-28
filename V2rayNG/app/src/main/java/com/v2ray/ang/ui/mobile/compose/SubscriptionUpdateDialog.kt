package com.v2ray.ang.ui.mobile.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager.rememberMmkvBool
import com.v2ray.ang.vm.SubscriptionUpdateViewModel

@Composable
fun SubscriptionUpdateDialog(
    subId: String? = null,
    updateViewModel: SubscriptionUpdateViewModel = viewModel(),
    onLoadingStateChanged: ((Boolean) -> Unit)? = null,
    onUpdated: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var updateSubscription by rememberMmkvBool(AppConfig.PREF_UPDATE_SUBSCRIPTION, false)
    var autoTestAfterUpdateSubscription by rememberMmkvBool(AppConfig.PREF_AUTO_TEST_AFTER_UPDATE_SUBSCRIPTION, false)
    var autoRemoveInvalidAfterTest by rememberMmkvBool(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST, false)
    var autoSortAfterTest by rememberMmkvBool(AppConfig.PREF_AUTO_SORT_AFTER_TEST, false)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.title_sub_update)) },
        text = {
            Column {
                SettingsSwitchItem(
                    title = stringResource(R.string.title_sub_update),
                    checked = updateSubscription,
                    onCheckedChange = { updateSubscription = it }
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_auto_test_after_update_subscription),
                    summary = stringResource(R.string.summary_pref_auto_test_after_update_subscription),
                    checked = autoTestAfterUpdateSubscription,
                    onCheckedChange = { autoTestAfterUpdateSubscription = it }
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_auto_remove_invalid_after_test),
                    summary = stringResource(R.string.summary_pref_auto_remove_invalid_after_test),
                    checked = autoRemoveInvalidAfterTest,
                    enabled = autoTestAfterUpdateSubscription,
                    onCheckedChange = { autoRemoveInvalidAfterTest = it }
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_auto_sort_after_test),
                    summary = stringResource(R.string.summary_pref_auto_sort_after_test),
                    checked = autoSortAfterTest,
                    enabled = autoTestAfterUpdateSubscription,
                    onCheckedChange = { autoSortAfterTest = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                updateViewModel.updateSubscriptions(subId, onLoadingStateChanged, onUpdated)
            }) {
                Text(text = stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        }
    )
}
