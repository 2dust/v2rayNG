package com.v2ray.ang.ui.subscription

import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.base.BaseViewModelEvent

abstract class BaseSubEditActivity : BaseComponentActivity() {
    private val viewModel: SubscriptionEditViewModel by viewModels()

    @Composable
    final override fun ScreenContent() {
        if (isFinishing) return
        LaunchedEffect(viewModel) {
            viewModel.viewModelEvent.collect { if (it == BaseViewModelEvent.FinishActivity) finish() }
        }
        val form by viewModel.form.collectAsStateWithLifecycle()
        val draft by viewModel.draft.collectAsStateWithLifecycle()
        val errors by viewModel.errors.collectAsStateWithLifecycle()
        val busy by viewModel.isLoading.collectAsStateWithLifecycle()
        val loaded = form
        val value = draft
        if (loaded == null || value == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Editor(loaded, busy, viewModel::save, viewModel::delete) { controls ->
                controls.SubscriptionForm(value, errors, loaded.suggestions, viewModel::change)
            }
        }
    }

    @Composable
    internal abstract fun Editor(form: SubscriptionEditViewModel.Form, busy: Boolean, save: () -> Unit, delete: () -> Unit,
        content: @Composable (SubscriptionControls) -> Unit)
}

internal interface SubscriptionControls {
    @Composable fun Field(label: String, value: String, change: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text,
        error: Boolean = false, hint: String? = null)
    @Composable fun Toggle(label: String, checked: Boolean, change: (Boolean) -> Unit)
    @Composable fun Profile(label: String, value: String, hint: String, options: List<String>, change: (String) -> Unit)
}

@Composable
private fun SubscriptionControls.SubscriptionForm(
    draft: SubscriptionDraft, errors: SubscriptionErrors, suggestions: List<String>, change: (SubscriptionDraft) -> Unit
) {
    Field(stringResource(R.string.sub_setting_remarks), draft.remarks, { change(draft.copy(remarks = it)) }, error = errors.remarks)
    Field(stringResource(R.string.sub_setting_url), draft.url, { change(draft.copy(url = it)) }, error = errors.url,
        hint = if (errors.url) stringResource(R.string.toast_invalid_url) else null)
    Field(stringResource(R.string.sub_setting_user_agent), draft.userAgent, { change(draft.copy(userAgent = it)) })
    Field(stringResource(R.string.sub_setting_request_headers), draft.requestHeaders, { change(draft.copy(requestHeaders = it)) })
    Field(stringResource(R.string.sub_setting_filter), draft.filter, { change(draft.copy(filter = it)) })
    Toggle(stringResource(R.string.sub_setting_enable), draft.enabled) { change(draft.copy(enabled = it)) }
    Toggle(stringResource(R.string.sub_auto_update), draft.autoUpdate) { change(draft.copy(autoUpdate = it)) }
    Field(stringResource(R.string.title_pref_auto_update_interval), draft.interval, { change(draft.copy(interval = it)) }, KeyboardType.Number,
        errors.interval, if (errors.interval) stringResource(R.string.toast_invalid_update_interval) else null)
    Toggle(stringResource(R.string.sub_allow_insecure_url), draft.allowInsecure) { change(draft.copy(allowInsecure = it)) }
    Profile(stringResource(R.string.sub_setting_pre_profile), draft.entry, stringResource(R.string.sub_setting_entry_proxy_tip), suggestions) {
        change(draft.copy(entry = it))
    }
    Profile(stringResource(R.string.sub_setting_next_profile), draft.exit, stringResource(R.string.sub_setting_exit_proxy_tip), suggestions) {
        change(draft.copy(exit = it))
    }
}
