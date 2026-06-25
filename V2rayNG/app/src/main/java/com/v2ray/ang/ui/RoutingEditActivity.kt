package com.v2ray.ang.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig.BUILTIN_OUTBOUND_TAGS
import com.v2ray.ang.AppConfig.TAG_PROXY
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppTheme
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.compose.ConfirmDialog
import com.v2ray.ang.compose.FormDropdownField
import com.v2ray.ang.compose.FormTextField
import com.v2ray.ang.compose.SettingsSwitchItem
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RoutingEditActivity : ComponentActivity() {
    private val position by lazy { intent.getIntExtra("position", -1) }

    private var processState: androidx.compose.runtime.MutableState<String>? = null

    private val processPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectedPackages = AppPickerActivity.getSelectedPackages(result.data)
            processState?.value = selectedPackages.joinToString(",")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val rulesetItem = SettingsManager.getRoutingRuleset(position)
        val profileRemarks = SettingsManager.getProfileRemarks()
        val outboundSuggestions = (BUILTIN_OUTBOUND_TAGS.toList() + profileRemarks).distinct()
        val canUseProcess = SettingsManager.canUseProcessRouting()

        setContent {
            AppTheme {
                val processText = remember {
                    mutableStateOf(rulesetItem?.process?.joinToString(",") ?: "")
                }
                processState = processText

                RoutingEditScreen(
                    position = position,
                    initial = rulesetItem,
                    outboundSuggestions = outboundSuggestions,
                    canUseProcess = canUseProcess,
                    processText = processText,
                    onPickProcess = { current ->
                        processPickerLauncher.launch(
                            AppPickerActivity.createIntent(
                                context = this,
                                selectedPackages = current,
                                title = getString(R.string.routing_settings_process)
                            )
                        )
                    },
                    onBackClick = { finish() },
                    onSave = { saveServer(it) },
                    onDelete = { deleteServer() }
                )
            }
        }
    }

    private fun saveServer(rulesetItem: RulesetItem): Boolean {
        if (rulesetItem.remarks.isNullOrEmpty()) {
            toast(R.string.sub_setting_remarks)
            return false
        }
        SettingsManager.saveRoutingRuleset(position, rulesetItem)
        toastSuccess(R.string.toast_success)
        finish()
        return true
    }

    private fun deleteServer(): Boolean {
        if (position >= 0) {
            lifecycleScope.launch(Dispatchers.IO) {
                SettingsManager.removeRoutingRuleset(position)
                launch(Dispatchers.Main) { finish() }
            }
        }
        return true
    }
}

@Composable
fun RoutingEditScreen(
    position: Int,
    initial: RulesetItem?,
    outboundSuggestions: List<String>,
    canUseProcess: Boolean,
    processText: androidx.compose.runtime.MutableState<String>,
    onPickProcess: (List<String>) -> Unit,
    onBackClick: () -> Unit,
    onSave: (RulesetItem) -> Boolean,
    onDelete: () -> Unit
) {
    var remarks by remember { mutableStateOf(initial?.remarks ?: "") }
    var locked by remember { mutableStateOf(initial?.locked == true) }
    var domain by remember { mutableStateOf(initial?.domain?.joinToString(",") ?: "") }
    var ip by remember { mutableStateOf(initial?.ip?.joinToString(",") ?: "") }
    var port by remember { mutableStateOf(initial?.port ?: "") }
    var protocol by remember { mutableStateOf(initial?.protocol?.joinToString(",") ?: "") }
    var network by remember { mutableStateOf(initial?.network ?: "") }
    var outboundTag by remember { mutableStateOf(initial?.outboundTag ?: BUILTIN_OUTBOUND_TAGS.first()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun buildRuleset(): RulesetItem {
        val rulesetItem = SettingsManager.getRoutingRuleset(position) ?: RulesetItem()
        rulesetItem.apply {
            this.remarks = remarks
            this.locked = locked
            this.domain = domain.nullIfBlank()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            this.ip = ip.nullIfBlank()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            this.process = processText.value.nullIfBlank()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            this.protocol = protocol.nullIfBlank()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            this.port = port.nullIfBlank()
            this.network = network.nullIfBlank()
            this.outboundTag = outboundTag.trim().ifEmpty { TAG_PROXY }
        }
        return rulesetItem
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.routing_settings_rule_title),
                onBackClick = onBackClick,
                actions = {
                    if (position >= 0) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(painterResource(R.drawable.ic_delete_24dp), contentDescription = stringResource(R.string.menu_item_del_config))
                        }
                    }
                    IconButton(onClick = { onSave(buildRuleset()) }) {
                        Icon(painterResource(R.drawable.ic_fab_check), contentDescription = stringResource(R.string.menu_item_save_config))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
                .padding(bottom = 36.dp)
        ) {
            FormTextField(stringResource(R.string.sub_setting_remarks), remarks, { remarks = it })
            SettingsSwitchItem(
                title = stringResource(R.string.routing_settings_locked),
                checked = locked,
                onCheckedChange = { locked = it }
            )
            FormDropdownField(
                label = stringResource(R.string.routing_settings_outbound_tag),
                value = outboundTag,
                options = outboundSuggestions,
                onValueChange = { outboundTag = it },
                editable = true
            )
            FormTextField(stringResource(R.string.routing_settings_domain), domain, { domain = it }, singleLine = false)
            FormTextField(stringResource(R.string.routing_settings_ip), ip, { ip = it }, singleLine = false)
            FormTextField(stringResource(R.string.routing_settings_port), port, { port = it })
            FormTextField(stringResource(R.string.routing_settings_protocol), protocol, { protocol = it })
            FormTextField(stringResource(R.string.routing_settings_network), network, { network = it })
            FormTextField(
                label = stringResource(R.string.routing_settings_process),
                value = processText.value,
                onValueChange = { processText.value = it },
                singleLine = false,
                enabled = canUseProcess
            )
            if (canUseProcess) {
                TextButton(
                    onClick = {
                        val current = processText.value.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                        onPickProcess(current)
                    },
                    modifier = Modifier.padding(start = 16.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_per_apps_24dp), contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.routing_settings_process))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            message = stringResource(R.string.del_config_comfirm),
            confirmText = stringResource(android.R.string.ok),
            dismissText = stringResource(android.R.string.cancel),
            onConfirm = onDelete,
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
