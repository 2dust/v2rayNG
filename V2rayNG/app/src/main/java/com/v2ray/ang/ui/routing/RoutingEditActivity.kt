package com.v2ray.ang.ui.routing

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AppConfig.BUILTIN_OUTBOUND_TAGS
import com.v2ray.ang.R
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.ProfilePickerChoices
import com.v2ray.ang.ui.compose.ServerSelectListDialog
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.verticalScrollbar

class RoutingEditActivity : BaseRoutingEditActivity() {
    override fun televisionDestination(): Intent? {
        val uiMode = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return if (uiMode == Configuration.UI_MODE_TYPE_TELEVISION) {
            Intent(intent).setClass(this, TvRoutingEditActivity::class.java)
        } else null
    }

    @Composable
    override fun Editor(form: RoutingEditViewModel.Form, draft: RoutingRuleDraft, remarksError: Boolean, busy: Boolean, onSave: () -> Unit) {
        val scrollState = rememberScrollState()
        var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
        var showOutboundDialog by rememberSaveable { mutableStateOf(false) }

        Scaffold(
            contentWindowInsets = WindowInsets(0),
            topBar = {
                AppTopBar(
                    title = stringResource(R.string.routing_settings_rule_title),
                    onBackClick = ::finish,
                    actions = {
                        if (form.initial != null) {
                            IconButton(onClick = { showDeleteConfirm = true }, enabled = !busy) {
                                Icon(painterResource(R.drawable.ic_delete_24dp), contentDescription = stringResource(R.string.acc_delete))
                            }
                        }
                        IconButton(onClick = onSave, enabled = !busy) {
                            Icon(painterResource(R.drawable.ic_fab_check), contentDescription = stringResource(R.string.acc_save))
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding).imePadding()
                    .verticalScroll(scrollState).verticalScrollbar(scrollState).padding(vertical = 8.dp)
            ) {
                RoutingRuleFields(
                    draft, form.canUseProcess, remarksError,
                    locked = { label -> SettingsSwitchItem(title = label, checked = draft.locked.value, onCheckedChange = { draft.locked.value = it }) },
                    outbound = { label ->
                        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            FormDropdownField(
                                label = label,
                                placeholder = stringResource(R.string.routing_settings_outbound_tag_hint, stringResource(R.string.server_lab_remarks)),
                                value = draft.outboundTag.value,
                                options = BUILTIN_OUTBOUND_TAGS.toList(),
                                onValueChange = { draft.outboundTag.value = it },
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showOutboundDialog = true }) {
                                Icon(painterResource(R.drawable.ic_search_24dp), contentDescription = stringResource(R.string.title_file_chooser))
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(36.dp))
                NavigationBarsSpacer()
            }
            if (showDeleteConfirm) {
                DeleteConfirmDialog(
                    message = stringResource(R.string.confirm_delete_routing_rule),
                    itemName = form.initial?.remarks.orEmpty(),
                    onConfirm = ::deleteRule,
                    onDismiss = { showDeleteConfirm = false }
                )
            }
            if (showOutboundDialog) ProfilePickerChoices({ SettingsManager.getProfileRemarks() }, { showOutboundDialog = false }) { options ->
                ServerSelectListDialog(
                    title = stringResource(R.string.routing_settings_outbound_tag),
                    options = options,
                    selectedOption = draft.outboundTag.value,
                    onSelected = { draft.outboundTag.value = it; showOutboundDialog = false },
                    onDismiss = { showOutboundDialog = false }
                )
            }
        }
    }
}

abstract class BaseRoutingEditActivity : BaseComponentActivity() {
    private val position by lazy { intent.getIntExtra("position", -1) }
    private val viewModel: RoutingEditViewModel by viewModels()
    private var forwardingToTv = false

    protected open fun televisionDestination(): Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val destination = televisionDestination()
        forwardingToTv = destination != null
        super.onCreate(savedInstanceState)
        if (destination != null) {
            startActivity(destination)
            finish()
        } else {
            viewModel.load(position)
        }
    }

    @Composable
    final override fun ScreenContent() {
        if (forwardingToTv) return
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val result = state.result
        LaunchedEffect(result) {
            when (result) {
                RoutingEditViewModel.Result.Saved -> { toastSuccess(R.string.toast_success); finish() }
                RoutingEditViewModel.Result.Deleted -> finish()
                RoutingEditViewModel.Result.LoadFailed -> { toastError(R.string.toast_failure); finish() }
                RoutingEditViewModel.Result.EditFailed -> { viewModel.dismissEditFailure(); toastError(R.string.toast_failure) }
                null -> Unit
            }
        }

        val loaded = state.form
        if (loaded == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }
        val draft = rememberSaveable(saver = RoutingRuleDraft.Saver) { RoutingRuleDraft(loaded.initial) }
        Editor(loaded, draft, state.remarksError, state.busy) {
            viewModel.save(draft.buildRuleset())
        }
    }

    protected fun deleteRule() = viewModel.delete()

    @Composable
    internal abstract fun Editor(form: RoutingEditViewModel.Form, draft: RoutingRuleDraft, remarksError: Boolean, busy: Boolean, onSave: () -> Unit)
}
