package com.v2ray.ang.ui.routing

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig.BUILTIN_OUTBOUND_TAGS
import com.v2ray.ang.AppConfig.TAG_PROXY
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.apppicker.AppPickerActivity
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.verticalScrollbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private val ROUTING_NETWORK_OPTIONS = listOf("tcp", "udp", "tcp,udp")

class RoutingEditActivity : BaseComponentActivity() {
    private val position by lazy { intent.getIntExtra("position", -1) }

    private var initial: RulesetItem? = null
    private lateinit var outboundSuggestions: List<String>
    private var canUseProcess: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initial = SettingsManager.getRoutingRuleset(position)
        val profileRemarks = SettingsManager.getProfileRemarks()
        outboundSuggestions = (BUILTIN_OUTBOUND_TAGS.toList() + profileRemarks).distinct()
        canUseProcess = SettingsManager.canUseProcessRouting()
    }

    @Composable
    override fun ScreenContent() {
        RoutingEditScreen(
            position = position,
            initial = initial,
            outboundSuggestions = outboundSuggestions,
            canUseProcess = canUseProcess,
            onBackClick = { finish() },
            onSave = { saveServer(it) },
            onDelete = { deleteServer() }
        )
    }

    private fun saveServer(rulesetItem: RulesetItem): Boolean {
        if (rulesetItem.remarks.isNullOrEmpty()) {
            toast(R.string.sub_setting_remarks)
            return false
        }
        if (position < 0 && rulesetItem.id.isEmpty()) {
            rulesetItem.id = UUID.randomUUID().toString()
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
                withContext(Dispatchers.Main) { finish() }
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
    onBackClick: () -> Unit,
    onSave: (RulesetItem) -> Boolean,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val processSelectTitle = stringResource(R.string.routing_settings_process_select)
    val scrollState = rememberScrollState()

    var remarks by rememberSaveable { mutableStateOf(initial?.remarks ?: "") }
    var locked by rememberSaveable { mutableStateOf(initial?.locked == true) }
    var domain by rememberSaveable { mutableStateOf(initial?.domain?.joinToString(",") ?: "") }
    var ip by rememberSaveable { mutableStateOf(initial?.ip?.joinToString(",") ?: "") }
    var processText by rememberSaveable { mutableStateOf(initial?.process?.joinToString(",") ?: "") }
    var protocol by rememberSaveable { mutableStateOf(initial?.protocol?.joinToString(",") ?: "") }
    var network by rememberSaveable { mutableStateOf(initial?.network ?: "") }
    var port by rememberSaveable { mutableStateOf(initial?.port ?: "") }
    var outboundTag by rememberSaveable {
        mutableStateOf(initial?.outboundTag ?: BUILTIN_OUTBOUND_TAGS.first())
    }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val selectedNetwork = network.ifBlank { "tcp,udp" }
    val deleteRuleName = initial?.remarks.orEmpty().ifBlank { remarks }

    val processPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedPackages = AppPickerActivity.getSelectedPackages(result.data)
            processText = selectedPackages.joinToString(",")
        }
    }

    fun buildRuleset(): RulesetItem {
        val rulesetItem = SettingsManager.getRoutingRuleset(position) ?: RulesetItem()
        rulesetItem.apply {
            this.remarks = remarks
            this.locked = locked
            this.domain = domain.nullIfBlank()
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() }
            this.ip = ip.nullIfBlank()
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() }
            this.process = processText.nullIfBlank()
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() }
            this.protocol = protocol.nullIfBlank()
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() }
            this.port = port.nullIfBlank()
            this.network = network.nullIfBlank()
            this.outboundTag = outboundTag.trim().ifEmpty { TAG_PROXY }
        }
        return rulesetItem
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.routing_settings_rule_title),
                onBackClick = onBackClick,
                actions = {
                    if (position >= 0) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                painterResource(R.drawable.ic_delete_24dp),
                                contentDescription = stringResource(
                                    R.string.acc_delete_routing_rule_named,
                                    deleteRuleName
                                )
                            )
                        }
                    }
                    IconButton(onClick = { onSave(buildRuleset()) }) {
                        Icon(
                            painterResource(R.drawable.ic_fab_check),
                            contentDescription = stringResource(R.string.acc_save)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(scrollState)
                .verticalScrollbar(scrollState)
                .padding(vertical = 8.dp)
        ) {
            FormTextField(
                label = stringResource(R.string.sub_setting_remarks),
                value = remarks,
                onValueChange = { remarks = it }
            )
            SettingsSwitchItem(
                title = stringResource(R.string.routing_settings_locked),
                checked = locked,
                onCheckedChange = { locked = it }
            )
            Text(
                text = stringResource(R.string.routing_settings_tips),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FormTextField(
                label = stringResource(R.string.routing_settings_domain),
                placeholder = stringResource(R.string.routing_settings_comma_tip),
                value = domain,
                onValueChange = { domain = it }
            )
            FormTextField(
                label = stringResource(R.string.routing_settings_ip),
                placeholder = stringResource(R.string.routing_settings_comma_tip),
                value = ip,
                onValueChange = { ip = it }
            )
            FormTextField(
                label = stringResource(R.string.routing_settings_process),
                placeholder = stringResource(R.string.routing_settings_comma_tip),
                value = processText,
                onValueChange = { processText = it },
                enabled = canUseProcess
            )
            if (canUseProcess) {
                TextButton(
                    onClick = {
                        val current = processText
                            .split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .distinct()
                        processPickerLauncher.launch(
                            AppPickerActivity.createIntent(
                                context = context,
                                selectedPackages = current,
                                title = processSelectTitle
                            )
                        )
                    },
                    modifier = Modifier.padding(start = 16.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_per_apps_24dp),
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(processSelectTitle)
                }
            }
            FormTextField(
                label = stringResource(R.string.routing_settings_port),
                value = port,
                onValueChange = { port = it }
            )
            FormTextField(
                label = stringResource(R.string.routing_settings_protocol),
                placeholder = stringResource(R.string.routing_settings_protocol_tip),
                value = protocol,
                onValueChange = { protocol = it }
            )
            FormDropdownField(
                label = stringResource(R.string.routing_settings_network),
                value = selectedNetwork,
                options = ROUTING_NETWORK_OPTIONS,
                onValueChange = { network = it }
            )
            FormDropdownField(
                label = stringResource(R.string.routing_settings_outbound_tag),
                placeholder = stringResource(
                    R.string.routing_settings_outbound_tag_hint,
                    stringResource(R.string.server_lab_remarks)
                ),
                value = outboundTag,
                options = outboundSuggestions,
                onValueChange = { outboundTag = it },
                editable = true
            )
            Spacer(modifier = Modifier.height(36.dp))
            NavigationBarsSpacer()
        }

        if (showDeleteConfirm) {
            DeleteConfirmDialog(
                message = stringResource(
                    R.string.confirm_delete_routing_rule_named,
                    deleteRuleName
                ),
                onConfirm = onDelete,
                onDismiss = { showDeleteConfirm = false }
            )
        }
    }
}
