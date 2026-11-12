package com.v2ray.ang.ui.routing

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig.BUILTIN_OUTBOUND_TAGS
import com.v2ray.ang.AppConfig.TAG_PROXY
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTopBarAction
import com.v2ray.ang.ui.compose.tvSafeAreaPadding
import com.v2ray.ang.ui.compose.tvAwareImePadding
import com.v2ray.ang.ui.compose.TvTextFieldNavigation
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.isTelevisionDevice
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.apppicker.AppPickerActivity
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppIconButton
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.ServerSelectButton
import com.v2ray.ang.ui.compose.ServerSelectListDialog
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
        outboundSuggestions = SettingsManager.getProfileRemarks()
        canUseProcess = SettingsManager.canUseProcessRouting()
    }

    @Composable
    override fun ScreenContent() {
        RoutingEditScreen(
            initial = initial,
            outboundSuggestions = outboundSuggestions,
            canUseProcess = canUseProcess,
            onBackClick = { finish() },
            onSave = { saveServer(it) },
            onDelete = { deleteServer() }
        )
    }

    private fun saveServer(rulesetItem: RulesetItem): Boolean {
        if (rulesetItem.remarks.isNullOrEmpty() || (position >= 0 && initial == null)) {
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
    val isTelevision = isTelevisionDevice()
    val processFieldFocusRequester = if (isTelevision) remember { FocusRequester() } else null
    val processPickerFocusRequester = if (isTelevision) remember { FocusRequester() } else null
    val portFocusRequester = if (isTelevision) remember { FocusRequester() } else null

    var remarks by rememberSaveable { mutableStateOf(initial?.remarks ?: "") }
    var isRemarksError by rememberSaveable { mutableStateOf(false) }
    var locked by rememberSaveable { mutableStateOf(initial?.locked == true) }
    var domain by rememberSaveable { mutableStateOf(initial?.domain?.joinToString(",") ?: "") }
    var ip by rememberSaveable { mutableStateOf(initial?.ip?.joinToString(",") ?: "") }
    var processText by rememberSaveable { mutableStateOf(initial?.process?.joinToString(",") ?: "") }
    var protocol by rememberSaveable { mutableStateOf(initial?.protocol?.joinToString(",") ?: "") }
    var network by rememberSaveable { mutableStateOf(initial?.network.orEmpty()) }
    var port by rememberSaveable { mutableStateOf(initial?.port ?: "") }
    var outboundTag by rememberSaveable {
        mutableStateOf(initial?.outboundTag ?: BUILTIN_OUTBOUND_TAGS.first())
    }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showOutboundDialog by rememberSaveable { mutableStateOf(false) }

    val processPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedPackages = AppPickerActivity.getSelectedPackages(result.data)
            processText = selectedPackages.joinToString(",")
        }
    }

    fun buildRuleset(): RulesetItem {
        val rulesetItem = initial?.copy() ?: RulesetItem()
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
                actionItems = buildList {
                    if (initial != null) add(
                        AppTopBarAction(
                            icon = painterResource(R.drawable.ic_delete_24dp),
                            label = stringResource(R.string.action_delete),
                            contentDescription = stringResource(R.string.acc_delete),
                            onClick = { showDeleteConfirm = true }
                        )
                    )
                    add(AppTopBarAction(
                        icon = painterResource(R.drawable.ic_fab_check),
                        label = stringResource(R.string.routing_settings_save),
                        contentDescription = stringResource(R.string.acc_save),
                        onClick = {
                            isRemarksError = remarks.isBlank()
                            if (!isRemarksError) onSave(buildRuleset())
                        }
                    ))
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .tvAwareImePadding()
                .tvSafeAreaPadding()
                .verticalScroll(scrollState)
                .verticalScrollbar(scrollState)
                .padding(vertical = 8.dp)
        ) {
            FormTextField(
                label = stringResource(R.string.sub_setting_remarks),
                value = remarks,
                onValueChange = { remarks = it },
                isError = isRemarksError
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
                enabled = canUseProcess,
                modifier = Modifier.focusProperties {
                    processPickerFocusRequester?.let { down = it }
                },
                tvNavigation = TvTextFieldNavigation(
                    focusRequester = processFieldFocusRequester
                )
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
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .dpadFocusOutline(focusRequester = processPickerFocusRequester, cornerRadius = 20.dp)
                        .focusProperties {
                            processFieldFocusRequester?.let { up = it }
                            portFocusRequester?.let { down = it }
                        }
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
                onValueChange = { port = it },
                modifier = Modifier.focusProperties {
                    processPickerFocusRequester?.let { up = it }
                },
                tvNavigation = TvTextFieldNavigation(
                    focusRequester = portFocusRequester
                )
            )
            FormTextField(
                label = stringResource(R.string.routing_settings_protocol),
                placeholder = stringResource(R.string.routing_settings_protocol_tip),
                value = protocol,
                onValueChange = { protocol = it }
            )
            FormDropdownField(
                label = stringResource(R.string.routing_settings_network),
                value = network.ifBlank { ROUTING_NETWORK_OPTIONS.last() },
                options = ROUTING_NETWORK_OPTIONS,
                onValueChange = { network = it }
            )
            if (isTelevision) {
                val outboundLabel = stringResource(R.string.routing_settings_outbound_tag)
                Text(
                    text = outboundLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                )
                ServerSelectButton(
                    label = outboundLabel,
                    value = outboundTag,
                    onClick = { showOutboundDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FormDropdownField(
                        label = stringResource(R.string.routing_settings_outbound_tag),
                        placeholder = stringResource(
                            R.string.routing_settings_outbound_tag_hint,
                            stringResource(R.string.server_lab_remarks)
                        ),
                        value = outboundTag,
                        options = BUILTIN_OUTBOUND_TAGS.toList(),
                        onValueChange = { outboundTag = it },
                        modifier = Modifier.weight(1f)
                    )
                    AppIconButton(
                        icon = painterResource(R.drawable.ic_search_24dp),
                        label = stringResource(R.string.menu_item_search),
                        contentDescription = stringResource(R.string.acc_search),
                        onClick = { showOutboundDialog = true }
                    )
                }
            }
            Spacer(modifier = Modifier.height(36.dp))
            NavigationBarsSpacer()
        }

        if (showDeleteConfirm) {
            DeleteConfirmDialog(
                message = stringResource(R.string.confirm_delete_routing_rule),
                itemName = initial?.remarks.orEmpty(),
                onConfirm = onDelete,
                onDismiss = { showDeleteConfirm = false }
            )
        }
        if (showOutboundDialog) {
            ServerSelectListDialog(
                title = stringResource(R.string.routing_settings_outbound_tag),
                options = if (isTelevision) BUILTIN_OUTBOUND_TAGS.toList() + outboundSuggestions else outboundSuggestions,
                selectedOption = outboundTag,
                onSelected = {
                    outboundTag = it
                    showOutboundDialog = false
                },
                onDismiss = { showOutboundDialog = false }
            )
        }
    }
}
