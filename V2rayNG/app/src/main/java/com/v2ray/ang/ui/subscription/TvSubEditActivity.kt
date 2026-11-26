package com.v2ray.ang.ui.subscription

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.EditorFocus
import com.v2ray.ang.ui.compose.ProfilePickerChoices
import com.v2ray.ang.ui.compose.TvEditorForm
import com.v2ray.ang.ui.compose.TvDeleteConfirmDialog
import com.v2ray.ang.ui.compose.TvEditorTextField
import com.v2ray.ang.ui.compose.TvIconButton
import com.v2ray.ang.ui.compose.RestoreTvDialogOpener
import com.v2ray.ang.ui.compose.TvSearchableOptionsDialog
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.rememberEditorFocus
import com.v2ray.ang.ui.settings.TvSettingsSwitchRow

class TvSubEditActivity : BaseSubEditActivity() {
    @Composable
    override fun EditorLayout(form: SubscriptionEditViewModel.Form, isLoading: Boolean, onSave: () -> Unit, onDelete: () -> Unit,
        content: @Composable (SubscriptionControls) -> Unit) {
        val back = remember { FocusRequester() }
        val save = remember { FocusRequester() }
        val delete = remember { FocusRequester() }
        val order = if (form.name == null) listOf(back, save) else listOf(back, save, delete)
        val focus = rememberEditorFocus(back)
        val controls = remember(back, focus) { TvSubscriptionControls(back, focus) }
        var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
        Scaffold(contentWindowInsets = WindowInsets(0), topBar = {
            Box(Modifier.padding(horizontal = 48.dp)) {
                AppTopBar(stringResource(R.string.title_sub_setting), ::finish, navigationIcon = {
                    TvIconButton(painterResource(R.drawable.ic_tv_arrow_back_24dp), stringResource(R.string.action_back), ::finish,
                        focus.target(back).dpadOrderedFocusNavigation(back, order))
                }, actions = {
                    TvIconButton(painterResource(R.drawable.ic_fab_check), stringResource(R.string.routing_settings_save), onSave,
                        focus.target(save).dpadOrderedFocusNavigation(save, order), enabled = !isLoading)
                    if (form.name != null) TvIconButton(painterResource(R.drawable.ic_delete_24dp), stringResource(R.string.action_delete),
                        { showDeleteConfirm = true }, focus.target(delete).dpadOrderedFocusNavigation(delete, order), enabled = !isLoading)
                })
            }
        }) { padding ->
            TvEditorForm(padding, back, focus) { content(controls) }
        }
        if (showDeleteConfirm) TvDeleteConfirmDialog(stringResource(R.string.confirm_delete_subscription_group), itemName = form.name,
            onConfirm = { showDeleteConfirm = false; onDelete() }, onDismiss = { showDeleteConfirm = false })
    }
}

private class TvSubscriptionControls(private val back: FocusRequester, private val focus: EditorFocus) : SubscriptionControls {
    @Composable
    override fun Field(label: String, value: String, onValueChange: (String) -> Unit, keyboard: KeyboardType, error: Boolean, hint: String?, announceValue: Boolean) {
        TvEditorTextField(label, value, onValueChange, keyboardType = keyboard, isError = error, supportingText = hint, announceValue = announceValue)
    }

    @Composable
    override fun Toggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        TvSettingsSwitchRow(label, checked, onCheckedChange, back, focus.target())
    }

    @Composable
    override fun Profile(label: String, value: String, hint: String, onSelect: (String) -> Unit) {
        val pick = remember { FocusRequester() }
        val clear = remember { FocusRequester() }
        val order = listOf(pick, clear)
        var open by rememberSaveable { mutableStateOf(false) }
        RestoreTvDialogOpener(open, pick)
        ProfileSelectDialogField(label, value, { open = true }, { onSelect("") }, hint,
            focus.target(pick).dpadOrderedFocusNavigation(pick, order, onBeforeFirst = { back.requestFocus() }).dpadFocusOutline(),
            focus.target(clear).dpadOrderedFocusNavigation(clear, order).dpadFocusOutline(cornerRadius = 24.dp))
        if (open) ProfilePickerChoices(SettingsManager::getSimpleProfileRemarks, { open = false }) { options ->
            TvSearchableOptionsDialog(label, options, value, onSelect = { onSelect(it); open = false }, onDismiss = { open = false })
        }
    }
}
