package com.v2ray.ang.ui.routing

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.RestoreTvDialogOpener
import com.v2ray.ang.ui.compose.TvEditorForm
import com.v2ray.ang.ui.compose.TvDeleteConfirmDialog
import com.v2ray.ang.ui.compose.TvIconButton
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.rememberEditorFocus
import com.v2ray.ang.ui.settings.TvSettingsSwitchRow

class TvRoutingEditActivity : BaseRoutingEditActivity() {
    @Composable
    override fun Editor(form: RoutingEditViewModel.Form, draft: RoutingRuleDraft, remarksError: Boolean, busy: Boolean, onSave: () -> Unit) {
        TvRoutingRuleEditor(form, draft, remarksError, busy, ::finish, onSave, ::deleteRule)
    }
}

@Composable
private fun TvRoutingRuleEditor(
    form: RoutingEditViewModel.Form, draft: RoutingRuleDraft, remarksError: Boolean, busy: Boolean,
    onBack: () -> Unit, onSave: () -> Unit, onDelete: () -> Unit
) {
    val back = remember { FocusRequester() }
    val delete = remember { FocusRequester() }
    val save = remember { FocusRequester() }
    val focus = rememberEditorFocus(fallback = back)
    val order = if (form.initial == null) listOf(back, save) else listOf(back, delete, save)
    var showDelete by rememberSaveable { mutableStateOf(false) }
    RestoreTvDialogOpener(showDelete, delete)
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.routing_settings_rule_title),
                onBackClick = onBack,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = focus.target(back)
                        .dpadOrderedFocusNavigation(back, order).dpadFocusOutline(cornerRadius = 24.dp)) {
                        Icon(painterResource(R.drawable.ic_tv_arrow_back_24dp), stringResource(R.string.acc_back))
                    }
                },
                actions = {
                    if (form.initial != null) {
                        TvIconButton(painterResource(R.drawable.ic_delete_24dp), stringResource(R.string.action_delete), { showDelete = true },
                            focus.target(delete).dpadOrderedFocusNavigation(delete, order), enabled = !busy)
                    }
                    TvIconButton(painterResource(R.drawable.ic_fab_check), stringResource(R.string.routing_settings_save), onSave,
                        focus.target(save).dpadOrderedFocusNavigation(save, order), enabled = !busy)
                }
            )
        }
    ) { innerPadding ->
        TvEditorForm(innerPadding, back, focus) {
            RoutingRuleFields(
                draft, form.canUseProcess, remarksError,
                locked = { label -> TvSettingsSwitchRow(label, draft.locked.value, { draft.locked.value = it }, back, focus.target()) },
                outbound = { label -> TvRoutingOutboundField(label, draft.outboundTag.value, { draft.outboundTag.value = it }, back) }
            )
        }
        if (showDelete) {
            TvDeleteConfirmDialog(
                message = stringResource(R.string.confirm_delete_routing_rule),
                itemName = form.initial?.remarks,
                onConfirm = onDelete,
                onDismiss = { showDelete = false }
            )
        }
    }
}
