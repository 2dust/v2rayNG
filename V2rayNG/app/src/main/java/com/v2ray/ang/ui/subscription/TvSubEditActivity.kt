package com.v2ray.ang.ui.subscription

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import com.v2ray.ang.ui.compose.LocalOutboundEditorFocus
import com.v2ray.ang.ui.compose.LocalTvEditor
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.TvDeleteConfirmDialog
import com.v2ray.ang.ui.compose.TvEditorTextField
import com.v2ray.ang.ui.compose.TvIconButton
import com.v2ray.ang.ui.compose.TvProfileSelectDialog
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.rememberOutboundEditorFocus
import com.v2ray.ang.ui.compose.rememberTvEditorIme
import com.v2ray.ang.ui.compose.requestFocusWhenReady
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.ui.settings.TvSettingsRows

class TvSubEditActivity : BaseSubEditActivity() {
    @Composable
    override fun Editor(form: SubscriptionEditViewModel.Form, busy: Boolean, save: () -> Unit, delete: () -> Unit,
        content: @Composable (SubscriptionControls) -> Unit) {
        val back = remember { FocusRequester() }
        val saveButton = remember { FocusRequester() }
        val deleteButton = remember { FocusRequester() }
        val order = if (form.name == null) listOf(back, saveButton) else listOf(back, saveButton, deleteButton)
        val focus = rememberOutboundEditorFocus(back)
        var confirm by rememberSaveable { mutableStateOf(false) }
        var restoreDelete by remember { mutableStateOf(false) }
        LaunchedEffect(confirm) {
            if (!confirm && restoreDelete) { requestFocusWhenReady(deleteButton); restoreDelete = false }
        }
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
            val scroll = rememberScrollState()
            val ime = rememberTvEditorIme()
            CompositionLocalProvider(LocalTvEditor provides back, LocalOutboundEditorFocus provides focus) {
                Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).then(ime.padding)
                    .padding(horizontal = 48.dp).verticalScroll(scroll).verticalScrollbar(scroll).padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    content(TvSubscriptionControls)
                    NavigationBarsSpacer()
                }
            }
        }
        if (confirm) TvDeleteConfirmDialog(stringResource(R.string.confirm_delete_subscription_group), itemName = form.name,
            onConfirm = { confirm = false; delete() }, onDismiss = { restoreDelete = true; confirm = false })
    }
}

private object TvSubscriptionControls : SubscriptionControls {
    @Composable
    override fun Field(label: String, value: String, change: (String) -> Unit, keyboard: KeyboardType, error: Boolean, hint: String?) {
        TvEditorTextField(label, value, change, Modifier, true, keyboard, null, 5, error, hint)
    }

    @Composable
    override fun Toggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
        val back = checkNotNull(LocalTvEditor.current)
        val rows = remember(back) { TvSettingsRows(back) {} }
        Box(checkNotNull(LocalOutboundEditorFocus.current).target()) {
            rows.SettingsSwitchItem(label, null, checked, change)
        }
    }

    @Composable
    override fun Profile(label: String, value: String, hint: String, options: List<String>, change: (String) -> Unit) {
        val focus = checkNotNull(LocalOutboundEditorFocus.current)
        val back = checkNotNull(LocalTvEditor.current)
        val pick = remember { FocusRequester() }
        val clear = remember { FocusRequester() }
        val order = listOf(pick, clear)
        var open by rememberSaveable { mutableStateOf(false) }
        var restore by remember { mutableStateOf(false) }
        fun close() { restore = true; open = false }
        LaunchedEffect(open) {
            if (!open && restore) { requestFocusWhenReady(pick); restore = false }
        }
        ProfileSelectDialogField(label, value, { open = true }, { change("") }, hint,
            focus.target(pick).dpadOrderedFocusNavigation(pick, order, onBeforeFirst = { back.requestFocus() }).dpadFocusOutline(),
            focus.target(clear).dpadOrderedFocusNavigation(clear, order).dpadFocusOutline(cornerRadius = 24.dp))
        if (open) CompositionLocalProvider(LocalOutboundEditorFocus provides null) {
            TvProfileSelectDialog(label, options, value, onSelect = { change(it); close() }, onDismiss = ::close)
        }
    }
}
