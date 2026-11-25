package com.v2ray.ang.ui.routing

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig.BUILTIN_OUTBOUND_TAGS
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.LocalEditorFocus
import com.v2ray.ang.ui.compose.TvEditorForm
import com.v2ray.ang.ui.compose.TvDeleteConfirmDialog
import com.v2ray.ang.ui.compose.TvIconButton
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.rememberEditorFocus
import com.v2ray.ang.ui.compose.requestFocusWhenReady

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
    val outbounds = remember(form.outbounds) { (BUILTIN_OUTBOUND_TAGS + form.outbounds).toList() }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var returnToDelete by remember { mutableStateOf(false) }
    LaunchedEffect(showDelete) {
        if (!showDelete && returnToDelete) {
            requestFocusWhenReady(delete)
            returnToDelete = false
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            AppTopBar(
                title = stringResource(R.string.routing_settings_rule_title),
                onBackClick = onBack,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = focus.target(back)
                        .dpadOrderedFocusNavigation(back, order).dpadFocusOutline(cornerRadius = 24.dp)) {
                        Icon(painterResource(R.drawable.ic_arrow_back_24dp), stringResource(R.string.acc_back),
                            modifier = Modifier.scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f))
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
                locked = { label -> TvRoutingLock(label, draft.locked.value, { draft.locked.value = it }, back) },
                outbound = { label -> TvRoutingOutboundField(label, draft.outboundTag.value, outbounds, { draft.outboundTag.value = it }, back) }
            )
        }
        if (showDelete) {
            TvDeleteConfirmDialog(
                message = stringResource(R.string.confirm_delete_routing_rule),
                itemName = form.initial?.remarks,
                onConfirm = onDelete,
                onDismiss = { returnToDelete = true; showDelete = false }
            )
        }
    }
}

@Composable
private fun TvRoutingLock(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, back: FocusRequester) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .then(LocalEditorFocus.current?.target() ?: Modifier)
            .focusProperties { start = back }.dpadFocusOutline()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 48.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics {})
    }
}
