package com.v2ray.ang.ui.routing

import android.content.Intent
import android.content.res.Configuration
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.enums.RoutingType
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.compose.AppDropdownMenuItems
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.SelectListDialog
import com.v2ray.ang.ui.compose.SettingsListItem
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.flow.MutableStateFlow
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

internal enum class RoutingMenuAction(@StringRes val labelRes: Int) {
    ImportPredefined(R.string.routing_settings_import_predefined_rulesets),
    ImportClipboard(R.string.routing_settings_import_rulesets_from_clipboard),
    ImportQRCode(R.string.routing_settings_import_rulesets_from_qrcode),
    ExportClipboard(R.string.routing_settings_export_rulesets_to_clipboard)
}

internal enum class RoutingPreset(val type: RoutingType, @StringRes val labelRes: Int) {
    ChinaWhitelist(RoutingType.WHITE, R.string.routing_preset_china_whitelist),
    ChinaBlacklist(RoutingType.BLACK, R.string.routing_preset_china_blacklist),
    Global(RoutingType.GLOBAL, R.string.routing_preset_global),
    IranWhitelist(RoutingType.WHITE_IRAN, R.string.routing_preset_iran_whitelist),
    RussiaWhitelist(RoutingType.WHITE_RUSSIA, R.string.routing_preset_russia_whitelist)
}

class RoutingSettingActivity : BaseRoutingSettingActivity() {
    override fun televisionDestination(): Intent? {
        val uiMode = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return if (uiMode == Configuration.UI_MODE_TYPE_TELEVISION) Intent(this, TvRoutingSettingActivity::class.java) else null
    }

    @Composable
    override fun ScreenContent() {
        if (forwardingToTv) return
        RoutingSettingScreen(
            viewModel = viewModel,
            domainStrategyState = domainStrategyState,
            onBackClick = { finish() },
            onAddRule = ::addRule,
            onEditRule = ::editRule,
            onDomainStrategySelected = ::setDomainStrategy,
            onImportPredefined = ::importPredefined,
            onImportClipboard = ::importFromClipboard,
            onImportQRcode = ::importQRcode,
            onExportClipboard = ::export2Clipboard
        )
    }
}

abstract class BaseRoutingSettingActivity : HelperBaseComponentActivity() {
    protected val viewModel: RoutingSettingsViewModel by viewModels()
    protected val domainStrategyState = MutableStateFlow("")
    protected var forwardingToTv = false
        private set

    protected open fun televisionDestination(): Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val destination = televisionDestination()
        forwardingToTv = destination != null
        super.onCreate(savedInstanceState)
        if (destination != null) {
            startActivity(destination)
            finish()
            return
        }
        domainStrategyState.value = getDomainStrategy()
    }

    override fun onResume() {
        super.onResume()
        if (!forwardingToTv) viewModel.reloadIfIdle()
    }

    protected fun addRule() {
        if (!viewModel.isLoading.value) startActivity(Intent(this, RoutingEditActivity::class.java))
    }

    protected fun editRule(id: String) {
        val position = viewModel.editablePosition(id)
        if (position < 0) return
        startActivity(Intent(this, RoutingEditActivity::class.java).putExtra("position", position))
    }

    protected fun setDomainStrategy(value: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY, value)
        domainStrategyState.value = value
    }

    private fun getDomainStrategy(): String {
        val strategies = resources.getStringArray(R.array.routing_domain_strategy)
        return MmkvManager.decodeSettingsString(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY) ?: strategies.first()
    }

    protected fun importPredefined(type: RoutingType) = viewModel.importPredefined(type)

    protected fun importFromClipboard() {
        val clipboard = try {
            Utils.getClipboard(this)
        } catch (e: Exception) {
            toastError(R.string.toast_failure)
            return
        }
        viewModel.importRulesets(clipboard)
    }

    protected fun importQRcode() {
        if (viewModel.isLoading.value) return
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                viewModel.importRulesets(scanResult)
            }
        }
    }

    protected fun export2Clipboard() {
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
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val domainStrategy by domainStrategyState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showPresetDialog by remember { mutableStateOf(false) }

    val domainStrategies = stringArrayResource(R.array.routing_domain_strategy).toList()
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromId = from.key as? String ?: return@rememberReorderableLazyListState
        val toId = to.key as? String ?: return@rememberReorderableLazyListState
        viewModel.move(fromId, toId)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.routing_settings_title),
                onBackClick = onBackClick,
                isLoading = isLoading,
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

            items(
                items = rulesets,
                key = { it.id }
            ) { ruleset ->
                ReorderableItem(reorderableState, key = ruleset.id) { isDragging ->
                    ReorderableListItem(
                        scope = this,
                        isDragging = isDragging
                    ) {
                        RoutingRulesetItem(
                            ruleset = ruleset,
                            onEdit = { onEditRule(ruleset.id) },
                            onEnabledChange = { viewModel.setRuleEnabled(ruleset.id, it) }
                        )
                    }
                    ItemDivider()
                }
            }
        }
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
private fun RoutingRulesetItem(
    ruleset: RulesetItem,
    onEdit: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoutingRuleSummary(ruleset, Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            IconButton(onClick = onEdit) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit_24dp),
                    contentDescription = stringResource(R.string.acc_edit)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Switch(
                checked = ruleset.enabled ?: false,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.scale(0.7f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                    checkedTrackColor = MaterialTheme.colorScheme.secondary
                )
            )
        }
    }
}

@Composable
internal fun RoutingRuleSummary(ruleset: RulesetItem, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = ruleset.remarks ?: "",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (ruleset.locked == true) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_lock_24dp),
                    contentDescription = stringResource(R.string.acc_locked),
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
                style = MaterialTheme.typography.labelMedium,
                color = colorConfigType
            )
        }
    }
}
