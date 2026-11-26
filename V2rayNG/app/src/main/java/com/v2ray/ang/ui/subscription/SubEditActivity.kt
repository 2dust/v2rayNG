package com.v2ray.ang.ui.subscription

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.ProfilePickerChoices
import com.v2ray.ang.ui.compose.ServerSelectListDialog
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.verticalScrollbar

class SubEditActivity : BaseSubEditActivity() {
    override fun televisionDestination(): Intent? {
        val uiMode = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return if (uiMode == Configuration.UI_MODE_TYPE_TELEVISION) Intent(intent).setClass(this, TvSubEditActivity::class.java) else null
    }

    @Composable
    override fun EditorLayout(form: SubscriptionEditViewModel.Form, isLoading: Boolean, onSave: () -> Unit, onDelete: () -> Unit,
        content: @Composable (SubscriptionControls) -> Unit) {
        val scroll = rememberScrollState()
        var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
        Scaffold(contentWindowInsets = WindowInsets(0), topBar = {
            AppTopBar(stringResource(R.string.title_sub_setting), ::finish, actions = {
                if (form.name != null) {
                    IconButton(onClick = { if (form.confirmRemove) showDeleteConfirm = true else onDelete() }, enabled = !isLoading) {
                        Icon(painterResource(R.drawable.ic_delete_24dp), stringResource(R.string.acc_delete))
                    }
                }
                IconButton(onClick = onSave, enabled = !isLoading) {
                    Icon(painterResource(R.drawable.ic_fab_check), stringResource(R.string.acc_save))
                }
            })
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(scroll)
                .verticalScrollbar(scroll).padding(vertical = 8.dp).padding(bottom = 36.dp)) {
                content(PhoneSubscriptionControls)
                NavigationBarsSpacer()
            }
        }
        if (showDeleteConfirm) DeleteConfirmDialog(stringResource(R.string.confirm_delete_subscription_group), itemName = form.name.orEmpty(),
            onConfirm = { showDeleteConfirm = false; onDelete() }, onDismiss = { showDeleteConfirm = false })
    }
}

private object PhoneSubscriptionControls : SubscriptionControls {
    @Composable
    override fun Field(label: String, value: String, onValueChange: (String) -> Unit, keyboard: KeyboardType, error: Boolean, hint: String?, announceValue: Boolean) {
        FormTextField(label, value, onValueChange, keyboardType = keyboard, isError = error, supportingText = hint)
    }

    @Composable
    override fun Toggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        SettingsSwitchItem(title = label, checked = checked, onCheckedChange = onCheckedChange)
    }

    @Composable
    override fun Profile(label: String, value: String, hint: String, onSelect: (String) -> Unit) {
        var open by rememberSaveable { mutableStateOf(false) }
        ProfileSelectDialogField(label, value, { open = true }, { onSelect("") }, hint)
        if (open) ProfilePickerChoices(SettingsManager::getSimpleProfileRemarks, { open = false }) { options ->
            ServerSelectListDialog(options, onSelected = { onSelect(it); open = false },
                onDismiss = { open = false }, title = label, selectedOption = value)
        }
    }
}
