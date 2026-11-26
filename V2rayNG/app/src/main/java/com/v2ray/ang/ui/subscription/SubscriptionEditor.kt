package com.v2ray.ang.ui.subscription

import android.content.Intent
import android.os.Bundle
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

    private var forwardingToTv = false

    protected open fun televisionDestination(): Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val destination = televisionDestination()
        forwardingToTv = destination != null
        super.onCreate(savedInstanceState)
        if (destination != null) {
            startActivity(destination)
            finish()
            return
        }
    }

    @Composable
    final override fun ScreenContent() {
        if (forwardingToTv) return
        LaunchedEffect(viewModel) {
            viewModel.viewModelEvent.collect { if (it == BaseViewModelEvent.FinishActivity) finish() }
        }
        val form by viewModel.form.collectAsStateWithLifecycle()
        val draft by viewModel.draft.collectAsStateWithLifecycle()
        val errors by viewModel.errors.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val loaded = form
        val value = draft
        if (loaded == null || value == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            EditorLayout(loaded, isLoading, viewModel::save, viewModel::delete) { controls ->
                controls.SubscriptionForm(value, errors, viewModel::updateDraft)
            }
        }
    }

    @Composable
    internal abstract fun EditorLayout(form: SubscriptionEditViewModel.Form, isLoading: Boolean, onSave: () -> Unit, onDelete: () -> Unit,
        content: @Composable (SubscriptionControls) -> Unit)
}

internal interface SubscriptionControls {
    @Composable fun Field(label: String, value: String, onValueChange: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text,
        error: Boolean = false, hint: String? = null, announceValue: Boolean = false)
    @Composable fun Toggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit)
    @Composable fun Profile(label: String, value: String, hint: String, onSelect: (String) -> Unit)
}

@Composable
private fun SubscriptionControls.SubscriptionForm(
    draft: SubscriptionDraft, errors: SubscriptionErrors, update: (SubscriptionDraft.() -> SubscriptionDraft) -> Unit
) {
    Field(stringResource(R.string.sub_setting_remarks), draft.remarks, { update { copy(remarks = it) } }, error = errors.remarks, announceValue = true)
    Field(stringResource(R.string.sub_setting_url), draft.url, { update { copy(url = it) } }, error = errors.url,
        hint = if (errors.url) stringResource(R.string.toast_invalid_url) else null)
    Field(stringResource(R.string.sub_setting_user_agent), draft.userAgent, { update { copy(userAgent = it) } }, announceValue = true)
    Field(stringResource(R.string.sub_setting_request_headers), draft.requestHeaders, { update { copy(requestHeaders = it) } })
    Field(stringResource(R.string.sub_setting_filter), draft.filter, { update { copy(filter = it) } }, announceValue = true)
    Toggle(stringResource(R.string.sub_setting_enable), draft.enabled) { update { copy(enabled = it) } }
    Toggle(stringResource(R.string.sub_auto_update), draft.autoUpdate) { update { copy(autoUpdate = it) } }
    Field(stringResource(R.string.title_pref_auto_update_interval), draft.interval, { update { copy(interval = it) } }, KeyboardType.Number,
        errors.interval, if (errors.interval) stringResource(R.string.toast_invalid_update_interval) else null, announceValue = true)
    Toggle(stringResource(R.string.sub_allow_insecure_url), draft.allowInsecure) { update { copy(allowInsecure = it) } }
    Profile(stringResource(R.string.sub_setting_pre_profile), draft.entry, stringResource(R.string.sub_setting_entry_proxy_tip)) {
        update { copy(entry = it) }
    }
    Profile(stringResource(R.string.sub_setting_next_profile), draft.exit, stringResource(R.string.sub_setting_exit_proxy_tip)) {
        update { copy(exit = it) }
    }
}
