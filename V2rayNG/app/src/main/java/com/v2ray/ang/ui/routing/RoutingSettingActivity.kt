package com.v2ray.ang.ui.routing

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.enums.RoutingType
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.compose.AppDropdownMenuItems
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.ReorderCommand
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.SelectListDialog
import com.v2ray.ang.ui.compose.SettingsListItem
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.reorderAccessibilityActions
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private enum class RoutingMenuAction(@StringRes val labelRes: Int) {
    ImportPredefined(R.string.routing_settings_import_predefined_rulesets),
    ImportClipboard(R.string.routing_settings_import_rulesets_from_clipboard),
    ImportQRCode(R.string.routing_settings_import_rulesets_from_qrcode),
    ExportClipboard(R.string.routing_settings_export_rulesets_to_clipboard)
}

private enum class RoutingPreset(val type: RoutingType, @StringRes val labelRes: Int) {
    ChinaWhitelist(RoutingType.WHITE, R.string.routing_preset_china_whitelist),
    ChinaBlacklist(RoutingType.BLACK, R.string.routing_preset_china_blacklist),
    Global(RoutingType.GLOBAL, R.string.routing_preset_global),
    IranWhitelist(RoutingType.WHITE_IRAN, R.string.routing_preset_iran_whitelist),
    RussiaWhitelist(RoutingType.WHITE_RUSSIA, R.string.routing_preset_russia_whitelist)
}

class RoutingSettingActivity : HelperBaseComponentActivity() {
    private val viewModel: RoutingSettingsViewModel by viewModels()
    private val domainStrategyState = MutableStateFlow("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        domainStrategyState.value = getDomainStrategy()
    }

    @Composable
    override fun ScreenContent() {
        RoutingSettingScreen(
            viewModel = viewModel,
            domainStrategyState = domainStrategyState,
            onBackClick = { finish() },
            onAddRule = { startActivity(Intent(this, RoutingEditActivity::class.java)) },
            onEditRule = { rulesetId ->
                startActivity(Intent(this, RoutingEditActivity::class.java).putExtra("ruleset_id", rulesetId))
            },
            onDomainStrategySelected = { value ->
                MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY, value)
                domainStrategyState.value = value
            },
            onImportPredefined = { type -> importPredefined(type) },
            onImportClipboard = { importFromClipboard() },
            onImportQRcode = { importQRcode() },
            onExportClipboard = { export2Clipboard() }
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload()
    }

    private fun getDomainStrategy(): String {
        val strategies = resources.getStringArray(R.array.routing_domain_strategy)
        return MmkvManager.decodeSettingsString(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY) ?: strategies.first()
    }

    private fun importPredefined(type: RoutingType) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SettingsManager.resetRoutingRulesetsFromPresets(this@RoutingSettingActivity, type)
                launch(Dispatchers.Main) {
                    viewModel.reload()
                    toastSuccess(R.string.toast_success)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to import predefined ruleset", e)
            }
        }
    }

    private fun importFromClipboard() {
        val clipboard = try {
            Utils.getClipboard(this)
        } catch (e: Exception) {
            toastError(R.string.toast_failure)
            return
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

    private fun importQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = SettingsManager.resetRoutingRulesets(scanResult)
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
        }
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
}

@Composable
fun RoutingSettingScreen(
    viewModel: RoutingSettingsViewModel,
    domainStrategyState: MutableStateFlow<String>,
    onBackClick: () -> Unit,
    onAddRule: () -> Unit,
    onEditRule: (String) -> Unit,
    onDomainStrategySelected: (String) -> Unit,
    onImportPredefined: (RoutingType) -> Unit,
    onImportClipboard: () -> Unit,
    onImportQRcode: () -> Unit,
    onExportClipboard: () -> Unit
) {
    val rulesets by viewModel.rulesetsFlow.collectAsStateWithLifecycle()
    val domainStrategy by domainStrategyState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showPresetDialog by remember { mutableStateOf(false) }
    var deleteRuleId by remember { mutableStateOf<String?>(null) }

    val domainStrategies = stringArrayResource(R.array.routing_domain_strategy).toList()
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // Lazy list indices include the preceding non-rule content, so resolve the stable rule keys.
        val fromIndex = rulesets.indexOfFirst { it.id == from.key }
        val toIndex = rulesets.indexOfFirst { it.id == to.key }
        viewModel.move(fromIndex, toIndex)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.routing_settings_title),
                onBackClick = onBackClick,
                actions = {
                    IconButton(onClick = onAddRule) {
                        Icon(
                            painterResource(R.drawable.ic_add_24dp),
                            contentDescription = stringResource(R.string.acc_add_rule)
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                painterResource(R.drawable.ic_more_vert_24dp),
                                contentDescription = stringResource(R.string.acc_more)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            AppDropdownMenuItems(RoutingMenuAction.entries, { it.labelRes }) { action ->
                                showMenu = false
                                when (action) {
                                    RoutingMenuAction.ImportPredefined -> showPresetDialog = true
                                    RoutingMenuAction.ImportClipboard -> onImportClipboard()
                                    RoutingMenuAction.ImportQRCode -> onImportQRcode()
                                    RoutingMenuAction.ExportClipboard -> onExportClipboard()
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScrollbar(lazyListState),
            contentPadding = NavigationBarsBottomPadding()
        ) {
            item(key = "domain_strategy") {
                SettingsListItem(
                    title = stringResource(R.string.routing_settings_domain_strategy),
                    entries = domainStrategies,
                    values = domainStrategies,
                    selectedValue = domainStrategy,
                    onSelected = { onDomainStrategySelected(it) }
                )
            }
            item {
                Text(
                    text = stringResource(R.string.routing_settings_rule_title),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }

            itemsIndexed(
                items = rulesets,
                key = { _, ruleset -> ruleset.id }
            ) { index, ruleset ->
                ReorderableItem(reorderableState, key = ruleset.id) { isDragging ->
                    ReorderableListItem(
                        scope = this,
                        isDragging = isDragging
                    ) {
                        RoutingRulesetItem(
                            ruleset = ruleset,
                            onEdit = { onEditRule(ruleset.id) },
                            onEnabledChange = { checked ->
                                val updated = ruleset.copy(enabled = checked)
                                viewModel.update(ruleset.id, updated)
                            },
                            onDelete = { deleteRuleId = ruleset.id },
                            reorderIndex = index,
                            itemCount = rulesets.size,
                            onMove = { command -> viewModel.move(ruleset.id, command) },
                        )
                    }
                    ItemDivider()
                }
            }
        }
    }

    deleteRuleId?.let { ruleId ->
        val ruleName = rulesets.firstOrNull { it.id == ruleId }?.remarks.orEmpty()
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_routing_rule_named, ruleName),
            onConfirm = {
                viewModel.remove(ruleId)
                deleteRuleId = null
            },
            onDismiss = { deleteRuleId = null }
        )
    }

    if (showPresetDialog) {
        SelectListDialog(
            title = stringResource(R.string.routing_settings_import_predefined_rulesets),
            options = RoutingPreset.entries,
            optionText = { stringResource(it.labelRes) },
            onSelected = { preset ->
                showPresetDialog = false
                onImportPredefined(preset.type)
            },
            onDismiss = { showPresetDialog = false }
        )
    }
}

@Composable
internal fun RoutingRulesetItem(
    ruleset: RulesetItem,
    onEdit: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    reorderIndex: Int? = null,
    itemCount: Int = 0,
    onMove: (ReorderCommand) -> Boolean = { false },
) {
    val enabled = ruleset.enabled
    val ruleName = ruleset.remarks.orEmpty()
    val outboundTag = ruleset.outboundTag.ifBlank { AppConfig.TAG_PROXY }
    val routeDescription = when {
        outboundTag.equals(AppConfig.TAG_BLOCKED, ignoreCase = true) ->
            stringResource(R.string.acc_routing_rule_blocked)

        outboundTag.equals(AppConfig.TAG_DIRECT, ignoreCase = true) ->
            stringResource(R.string.acc_routing_rule_routed_directly)

        else -> stringResource(R.string.acc_routing_rule_routed_through, outboundTag)
    }
    val ruleState = stringResource(
        if (enabled) R.string.acc_routing_rule_enabled else R.string.acc_routing_rule_disabled
    )
    val ruleSummary = stringResource(
        R.string.acc_routing_rule_summary,
        ruleName,
        routeDescription
    )
    val accessibilitySummary = if (ruleset.locked == true) {
        stringResource(R.string.acc_routing_rule_locked_summary, ruleSummary)
    } else {
        ruleSummary
    }
    val itemActions = listOf(
        CustomAccessibilityAction(
            label = stringResource(R.string.acc_edit_routing_rule_named, ruleName),
            action = { onEdit(); true },
        ),
        CustomAccessibilityAction(
            label = stringResource(R.string.acc_delete_routing_rule_named, ruleName),
            action = { onDelete(); true },
        ),
    )
    val accessibilityActions = itemActions + if (reorderIndex != null) {
        reorderAccessibilityActions(reorderIndex, itemCount, onMove)
    } else {
        emptyList()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilitySummary
                stateDescription = ruleState
                customActions = accessibilityActions
            }
            .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabledChange)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ruleset.remarks ?: "",
                    modifier = Modifier.semantics { hideFromAccessibility() },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (ruleset.locked == true) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_lock_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val domainIpInfo = (ruleset.domain ?: ruleset.ip ?: ruleset.process ?: ruleset.port)?.toString() ?: ""
            if (domainIpInfo.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = domainIpInfo,
                    modifier = Modifier.semantics { hideFromAccessibility() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!ruleset.outboundTag.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = ruleset.outboundTag,
                    modifier = Modifier.semantics { hideFromAccessibility() },
                    style = MaterialTheme.typography.labelMedium,
                    color = colorConfigType
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Row {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.clearAndSetSemantics {},
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit_24dp),
                        contentDescription = stringResource(
                            R.string.acc_edit_routing_rule_named,
                            ruleName
                        )
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.clearAndSetSemantics {},
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete_24dp),
                        contentDescription = stringResource(
                            R.string.acc_delete_routing_rule_named,
                            ruleName
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Switch(
                checked = enabled,
                onCheckedChange = null,
                modifier = Modifier.scale(0.7f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                    checkedTrackColor = MaterialTheme.colorScheme.secondary
                )
            )
        }
    }
}
