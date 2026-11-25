package com.v2ray.ang.ui.subscription

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.settings.SettingsRows

internal enum class SubscriptionUpdateOption(val key: String, @StringRes val title: Int, @StringRes val summary: Int? = null) {
    Update(AppConfig.PREF_UPDATE_SUBSCRIPTION, R.string.title_sub_update),
    Test(AppConfig.PREF_AUTO_TEST_AFTER_UPDATE_SUBSCRIPTION, R.string.title_pref_auto_test_after_update_subscription,
        R.string.summary_pref_auto_test_after_update_subscription),
    RemoveInvalid(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST, R.string.title_pref_auto_remove_invalid_after_test,
        R.string.summary_pref_auto_remove_invalid_after_test),
    Sort(AppConfig.PREF_AUTO_SORT_AFTER_TEST, R.string.title_pref_auto_sort_after_test, R.string.summary_pref_auto_sort_after_test);

    fun isAvailable(enabled: Set<SubscriptionUpdateOption>) = this == Update || this == Test || Test in enabled
}

@Composable
internal fun SubscriptionUpdateOptions(enabled: Set<SubscriptionUpdateOption>, onChange: (SubscriptionUpdateOption, Boolean) -> Unit, rows: SettingsRows) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SubscriptionUpdateOption.entries.forEach { option ->
            rows.SettingsSwitchItem(stringResource(option.title), option.summary?.let { stringResource(it) },
                checked = option in enabled, enabled = option.isAvailable(enabled), onCheckedChange = { onChange(option, it) })
        }
    }
}
