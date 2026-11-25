package com.v2ray.ang.ui.subscription

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
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
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.ServerSelectListDialog
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.verticalScrollbar

class SubEditActivity : BaseSubEditActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION) {
            startActivity(Intent(intent).setClass(this, TvSubEditActivity::class.java))
            finish()
        }
    }

    @Composable
    override fun Editor(form: SubscriptionEditViewModel.Form, busy: Boolean, save: () -> Unit, delete: () -> Unit,
        content: @Composable (SubscriptionControls) -> Unit) {
        val scroll = rememberScrollState()
        var confirm by rememberSaveable { mutableStateOf(false) }
        Scaffold(contentWindowInsets = WindowInsets(0), topBar = {
            AppTopBar(stringResource(R.string.title_sub_setting), ::finish, actions = {
                if (form.name != null) {
                    IconButton(onClick = { if (form.confirmRemove) confirm = true else delete() }, enabled = !busy) {
                        Icon(painterResource(R.drawable.ic_delete_24dp), stringResource(R.string.acc_delete))
                    }
                }
                IconButton(onClick = save, enabled = !busy) {
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
        if (confirm) DeleteConfirmDialog(stringResource(R.string.confirm_delete_subscription_group), itemName = form.name.orEmpty(),
            onConfirm = { confirm = false; delete() }, onDismiss = { confirm = false })
    }
}

private object PhoneSubscriptionControls : SubscriptionControls {
    @Composable
    override fun Field(label: String, value: String, change: (String) -> Unit, keyboard: KeyboardType, error: Boolean, hint: String?, announceValue: Boolean) {
        FormTextField(label, value, change, keyboardType = keyboard, isError = error, supportingText = hint)
    }

    @Composable
    override fun Toggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
        SettingsSwitchItem(title = label, checked = checked, onCheckedChange = change)
    }

    @Composable
    override fun Profile(label: String, value: String, hint: String, options: List<String>, change: (String) -> Unit) {
        var open by rememberSaveable { mutableStateOf(false) }
        ProfileSelectDialogField(label, value, { open = true }, { change("") }, hint)
        if (open) ServerSelectListDialog(options, onSelected = { change(it); open = false },
            onDismiss = { open = false }, title = label, selectedOption = value)
    }
}
