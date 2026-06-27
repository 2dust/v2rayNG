package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.RoutingSettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoutingSettingActivity : HelperBaseActivity() {
    private val viewModel: RoutingSettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                RoutingSettingScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onAddRule = { startActivity(Intent(this, RoutingEditActivity::class.java)) },
                    onImportPredefined = { importPredefined() },
                    onImportClipboard = { importFromClipboard() },
                    onImportQRcode = { importQRcode() },
                    onExportClipboard = { export2Clipboard() },
                    onEditRule = { position ->
                        startActivity(Intent(this, RoutingEditActivity::class.java).putExtra("position", position))
                    },
                    getDomainStrategy = { getDomainStrategy() },
                    onSetDomainStrategy = { setDomainStrategy() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload()
    }

    private fun getDomainStrategy(): String {
        val routing_domain_strategy = resources.getStringArray(R.array.routing_domain_strategy)
        return MmkvManager.decodeSettingsString(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY) ?: routing_domain_strategy.first()
    }

    private fun setDomainStrategy() {
        val routing_domain_strategy = resources.getStringArray(R.array.routing_domain_strategy)
        AlertDialog.Builder(this).setItems(routing_domain_strategy) { _, i ->
            try {
                val value = routing_domain_strategy[i]
                MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY, value)
                viewModel.reload() // Trigger UI refresh if needed, though strategy isn't in VM flow
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to set domain strategy", e)
            }
        }.show()
    }

    private fun importPredefined() {
        val preset_rulesets = resources.getStringArray(R.array.preset_rulesets)
        AlertDialog.Builder(this).setItems(preset_rulesets) { _, i ->
            AlertDialog.Builder(this).setMessage(R.string.routing_settings_import_rulesets_tip)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            SettingsManager.resetRoutingRulesetsFromPresets(this@RoutingSettingActivity, i)
                            withContext(Dispatchers.Main) {
                                viewModel.reload()
                                toastSuccess(R.string.toast_success)
                            }
                        } catch (e: Exception) {
                            LogUtil.e(AppConfig.TAG, "Failed to import predefined ruleset", e)
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }.show()
    }

    private fun importFromClipboard() {
        AlertDialog.Builder(this).setMessage(R.string.routing_settings_import_rulesets_tip)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val clipboard = try {
                    Utils.getClipboard(this)
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to get clipboard content", e)
                    toastError(R.string.toast_failure)
                    return@setPositiveButton
                }
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = SettingsManager.resetRoutingRulesets(clipboard)
                    withContext(Dispatchers.Main) {
                        if (result) {
                            viewModel.reload()
                            toastSuccess(R.string.toast_success)
                        } else {
                            toastError(R.string.toast_failure)
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun importQRcode(): Boolean {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                importRulesetsFromQRcode(scanResult)
            }
        }
        return true
    }

    private fun export2Clipboard() {
        val rulesetList = MmkvManager.decodeRoutingRulesets()
        if (rulesetList.isNullOrEmpty()) {
            toastError(R.string.toast_failure)
        } else {
            Utils.setClipboard(this, JsonUtil.toJson(rulesetList))
            toastSuccess(R.string.toast_success)
        }
    }

    private fun importRulesetsFromQRcode(qrcode: String?): Boolean {
        AlertDialog.Builder(this).setMessage(R.string.routing_settings_import_rulesets_tip)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = SettingsManager.resetRoutingRulesets(qrcode)
                    withContext(Dispatchers.Main) {
                        if (result) {
                            viewModel.reload()
                            toastSuccess(R.string.toast_success)
                        } else {
                            toastError(R.string.toast_failure)
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        return true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingSettingScreen(
    viewModel: RoutingSettingsViewModel,
    onBack: () -> Unit,
    onAddRule: () -> Unit,
    onImportPredefined: () -> Unit,
    onImportClipboard: () -> Unit,
    onImportQRcode: () -> Unit,
    onExportClipboard: () -> Unit,
    onEditRule: (Int) -> Unit,
    getDomainStrategy: () -> String,
    onSetDomainStrategy: () -> Unit
) {
    val rulesets by viewModel.rulesetsFlow.collectAsStateWithLifecycle()
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.routing_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onAddRule) {
                        Icon(Icons.Default.Add, contentDescription = "Add Rule")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.routing_settings_import_predefined_rulesets)) },
                                onClick = { showMenu = false; onImportPredefined() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.routing_settings_import_rulesets_from_clipboard)) },
                                onClick = { showMenu = false; onImportClipboard() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.routing_settings_import_rulesets_from_qrcode)) },
                                onClick = { showMenu = false; onImportQRcode() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.routing_settings_export_rulesets_to_clipboard)) },
                                onClick = { showMenu = false; onExportClipboard() }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.routing_settings_domain_strategy)) },
                    supportingContent = { Text(getDomainStrategy()) },
                    modifier = Modifier.clickable(onClick = onSetDomainStrategy)
                )
                HorizontalDivider()
            }
            
            itemsIndexed(rulesets) { index, item ->
                ListItem(
                    headlineContent = { Text(item.remarks.orEmpty()) },
                    supportingContent = { Text("${item.outboundTag} (${item.port})") },
                    modifier = Modifier.clickable { onEditRule(index) }
                )
                HorizontalDivider()
            }
        }
    }
}
