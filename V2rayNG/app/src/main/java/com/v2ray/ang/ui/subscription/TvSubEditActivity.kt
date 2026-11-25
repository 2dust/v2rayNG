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
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.EditorFocus
import com.v2ray.ang.ui.compose.TvEditorForm
import com.v2ray.ang.ui.compose.TvDeleteConfirmDialog
import com.v2ray.ang.ui.compose.TvEditorTextField
import com.v2ray.ang.ui.compose.TvIconButton
import com.v2ray.ang.ui.compose.RestoreTvDialogOpener
import com.v2ray.ang.ui.compose.TvSearchableOptionsDialog
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.rememberEditorFocus
import com.v2ray.ang.ui.settings.TvSettingsRows

class TvSubEditActivity : BaseSubEditActivity() {
    @Composable
    override fun Editor(form: SubscriptionEditViewModel.Form, busy: Boolean, save: () -> Unit, delete: () -> Unit,
        content: @Composable (SubscriptionControls) -> Unit) {
        val back = remember { FocusRequester() }
        val saveButton = remember { FocusRequester() }
        val deleteButton = remember { FocusRequester() }
        val order = if (form.name == null) listOf(back, saveButton) else listOf(back, saveButton, deleteButton)
        val focus = rememberEditorFocus(back)
        val controls = remember(back, focus) { TvSubscriptionControls(back, focus) }
        var confirm by rememberSaveable { mutableStateOf(false) }
        Scaffold(contentWindowInsets = WindowInsets(0), topBar = {
            Box(Modifier.padding(horizontal = 48.dp)) {
                AppTopBar(stringResource(R.string.title_sub_setting), ::finish, navigationIcon = {
                    TvIconButton(painterResource(R.drawable.ic_tv_arrow_back_24dp), stringResource(R.string.action_back), ::finish,
                        focus.target(back).dpadOrderedFocusNavigation(back, order))
                }, actions = {
                    TvIconButton(painterResource(R.drawable.ic_fab_check), stringResource(R.string.routing_settings_save), save,
                        focus.target(saveButton).dpadOrderedFocusNavigation(saveButton, order), enabled = !busy)
                    if (form.name != null) TvIconButton(painterResource(R.drawable.ic_delete_24dp), stringResource(R.string.action_delete),
                        { confirm = true }, focus.target(deleteButton).dpadOrderedFocusNavigation(deleteButton, order), enabled = !busy)
                })
            }
        }) { padding ->
            TvEditorForm(padding, back, focus) { content(controls) }
        }
        if (confirm) TvDeleteConfirmDialog(stringResource(R.string.confirm_delete_subscription_group), itemName = form.name,
            onConfirm = { confirm = false; delete() }, onDismiss = { confirm = false })
    }
}

private class TvSubscriptionControls(private val back: FocusRequester, private val focus: EditorFocus) : SubscriptionControls {
    private val rows = TvSettingsRows(back) {}

    @Composable
    override fun Field(label: String, value: String, change: (String) -> Unit, keyboard: KeyboardType, error: Boolean, hint: String?) {
        TvEditorTextField(label, value, change, Modifier, enabled = true, keyboardType = keyboard, placeholder = null,
            maxLines = 5, isError = error, supportingText = hint)
    }

    @Composable
    override fun Toggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
        Box(focus.target()) {
            rows.SettingsSwitchItem(label, null, checked, change)
        }
    }

    @Composable
    override fun Profile(label: String, value: String, hint: String, options: List<String>, change: (String) -> Unit) {
        val pick = remember { FocusRequester() }
        val clear = remember { FocusRequester() }
        val order = listOf(pick, clear)
        var open by rememberSaveable { mutableStateOf(false) }
        RestoreTvDialogOpener(open, pick)
        ProfileSelectDialogField(label, value, { open = true }, { change("") }, hint,
            focus.target(pick).dpadOrderedFocusNavigation(pick, order, onBeforeFirst = { back.requestFocus() }).dpadFocusOutline(),
            focus.target(clear).dpadOrderedFocusNavigation(clear, order).dpadFocusOutline(cornerRadius = 24.dp))
        if (open) {
            TvSearchableOptionsDialog(label, options, value, onSelect = { change(it); open = false }, onDismiss = { open = false })
        }
    }
}
