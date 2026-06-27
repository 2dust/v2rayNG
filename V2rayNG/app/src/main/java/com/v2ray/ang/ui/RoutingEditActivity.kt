package com.v2ray.ang.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RoutingEditActivity : BaseActivity() {
    private val position by lazy { intent.getIntExtra("position", -1) }
    
    private var rulesetState = mutableStateOf(RulesetItem())
    private val suggestions = mutableStateListOf<String>()

    private val processPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectedPackages = AppPickerActivity.getSelectedPackages(result.data)
            rulesetState.value = rulesetState.value.copy(process = selectedPackages)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val rulesetItem = SettingsManager.getRoutingRuleset(position) ?: RulesetItem(outboundTag = BUILTIN_OUTBOUND_TAGS.first())
        rulesetState.value = rulesetItem
        
        suggestions.addAll((BUILTIN_OUTBOUND_TAGS.toList() + SettingsManager.getProfileRemarks()).distinct())

        setContent {
            MaterialTheme {
                RoutingEditScreen(
                    ruleset = rulesetState.value,
                    suggestions = suggestions,
                    canDelete = position >= 0,
                    canUseProcessRouting = SettingsManager.canUseProcessRouting(),
                    onBack = { finish() },
                    onDelete = { deleteServer() },
                    onSave = { saveServer() },
                    onRulesetChange = { rulesetState.value = it },
                    onPickProcess = {
                        processPickerLauncher.launch(
                            AppPickerActivity.createIntent(
                                context = this,
                                selectedPackages = rulesetState.value.process.orEmpty(),
                                title = getString(R.string.routing_settings_process)
                            )
                        )
                    }
                )
            }
        }
    }

    private fun saveServer() {
        val rulesetItem = rulesetState.value
        if (rulesetItem.remarks.isNullOrEmpty()) {
            toast(R.string.sub_setting_remarks)
            return
        }

        SettingsManager.saveRoutingRuleset(position, rulesetItem)
        toastSuccess(R.string.toast_success)
        finish()
    }

    private fun deleteServer() {
        if (position >= 0) {
            AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        SettingsManager.removeRoutingRuleset(position)
                        launch(Dispatchers.Main) { finish() }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingEditScreen(
    ruleset: RulesetItem,
    suggestions: List<String>,
    canDelete: Boolean,
    canUseProcessRouting: Boolean,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    onRulesetChange: (RulesetItem) -> Unit,
    onPickProcess: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.routing_settings_rule_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (canDelete) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    IconButton(onClick = onSave) {
                        Icon(Icons.Default.Done, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = ruleset.remarks.orEmpty(),
                onValueChange = { onRulesetChange(ruleset.copy(remarks = it)) },
                label = { Text(stringResource(R.string.sub_setting_remarks)) },
                modifier = Modifier.fillMaxWidth()
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = ruleset.locked == true,
                    onCheckedChange = { onRulesetChange(ruleset.copy(locked = it)) }
                )
                Text(text = "Locked")
            }

            OutboundTagSelector(
                selectedTag = ruleset.outboundTag.orEmpty(),
                suggestions = suggestions,
                onTagSelected = { onRulesetChange(ruleset.copy(outboundTag = it)) }
            )

            OutlinedTextField(
                value = ruleset.port.orEmpty(),
                onValueChange = { onRulesetChange(ruleset.copy(port = it)) },
                label = { Text(stringResource(R.string.routing_settings_port)) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = ruleset.domain?.joinToString(",") ?: "",
                onValueChange = { onRulesetChange(ruleset.copy(domain = it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() })) },
                label = { Text(stringResource(R.string.routing_settings_domain)) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = ruleset.ip?.joinToString(",") ?: "",
                onValueChange = { onRulesetChange(ruleset.copy(ip = it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() })) },
                label = { Text(stringResource(R.string.routing_settings_ip)) },
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = ruleset.process?.joinToString(",") ?: "",
                    onValueChange = { onRulesetChange(ruleset.copy(process = it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() })) },
                    label = { Text(stringResource(R.string.routing_settings_process)) },
                    modifier = Modifier.weight(1f),
                    enabled = canUseProcessRouting
                )
                IconButton(onClick = onPickProcess, enabled = canUseProcessRouting) {
                    Icon(painterResource(R.drawable.ic_per_apps_24dp), contentDescription = "Pick Apps")
                }
            }

            OutlinedTextField(
                value = ruleset.protocol?.joinToString(",") ?: "",
                onValueChange = { onRulesetChange(ruleset.copy(protocol = it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() })) },
                label = { Text(stringResource(R.string.routing_settings_protocol)) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = ruleset.network.orEmpty(),
                onValueChange = { onRulesetChange(ruleset.copy(network = it)) },
                label = { Text(stringResource(R.string.routing_settings_network)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutboundTagSelector(
    selectedTag: String,
    suggestions: List<String>,
    onTagSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedTag,
            onValueChange = onTagSelected,
            label = { Text(stringResource(R.string.routing_settings_outbound_tag)) },
            modifier = Modifier.menuAnchor(),
            readOnly = false,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onTagSelected(suggestion)
                        expanded = false
                    }
                )
            }
        }
    }
}
