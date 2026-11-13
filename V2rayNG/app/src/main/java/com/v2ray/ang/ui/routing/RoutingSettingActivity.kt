package com.v2ray.ang.ui.routing

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.enums.RoutingType
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.compose.AppIconButton
import com.v2ray.ang.ui.compose.AppRowSwitch
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.AppTopBarAction
import com.v2ray.ang.ui.compose.AppSelectionMenuAction
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.DpadReorderItem
import com.v2ray.ang.ui.compose.DpadRowActionFocus
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.SelectListDialog
import com.v2ray.ang.ui.compose.SettingsListItem
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.tvSafeAreaPadding
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.dpadLongPressToMove
import com.v2ray.ang.ui.compose.dpadMovePreviousNavigation
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.isTelevisionDevice
import com.v2ray.ang.ui.compose.keepDpadReorderItemVisible
import com.v2ray.ang.ui.compose.rememberLazyDpadFocus
import com.v2ray.ang.ui.compose.rememberDpadFocusTargets
import com.v2ray.ang.ui.compose.rememberDpadFocusRequester
import com.v2ray.ang.ui.compose.rememberSyncedDpadReorderState
import com.v2ray.ang.ui.compose.reorderIndicesForKeys
import com.v2ray.ang.ui.compose.verticalDpadReorderTarget
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
private const val ROUTING_LIST_HEADER_COUNT = 1

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
            onEditRule = { position ->
                startActivity(Intent(this, RoutingEditActivity::class.java).putExtra("position", position))
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
private class RoutingRowFocusTargets {
    val row = FocusRequester()
    val edit = FocusRequester()
    val delete = FocusRequester()
    val toggle = FocusRequester()
    val order = listOf(row, edit, delete, toggle)
}

@Composable
fun RoutingSettingScreen(
    viewModel: RoutingSettingsViewModel,
    domainStrategyState: MutableStateFlow<String>,
    onBackClick: () -> Unit,
    onAddRule: () -> Unit,
    onEditRule: (Int) -> Unit,
    onDomainStrategySelected: (String) -> Unit,
    onImportPredefined: (RoutingType) -> Unit,
    onImportClipboard: () -> Unit,
    onImportQRcode: () -> Unit,
    onExportClipboard: () -> Unit
) {
    val isTelevision = isTelevisionDevice()
    // Keep lazy content and its focus map on one snapshot: TalkBack can measure before recomposition.
    val rulesets = viewModel.rulesetsFlow.collectAsStateWithLifecycle().value
    val rulesetIds = rulesets.map { it.id }
    val rowFocusTargets = rememberDpadFocusTargets(rulesetIds) { RoutingRowFocusTargets() }
    val domainStrategy by domainStrategyState.collectAsState()
    var showPresetDialog by remember { mutableStateOf(false) }
    var deleteRuleId by remember { mutableStateOf<String?>(null) }
    val navigationFocusRequester = rememberDpadFocusRequester()
    val domainStrategyFocusRequester = remember { FocusRequester() }
    val domainStrategies = stringArrayResource(R.array.routing_domain_strategy).toList()
    val lazyListState = rememberLazyListState()
    val requestRowFocus = rememberLazyDpadFocus(
        listOf(listOf(domainStrategyFocusRequester)) + rulesetIds.map { rowFocusTargets.getValue(it).order }
    ) { lazyListState.scrollToItem(it) }
    val focusFirstRule = {
        rulesets.firstOrNull()?.let { rowFocusTargets[it.id]?.row?.let(requestRowFocus) } ?: false
    }
    val dpadReorderState = rememberSyncedDpadReorderState(rulesetIds, isTelevision) { key, index ->
        val id = key as? String ?: return@rememberSyncedDpadReorderState
        if (index >= 0) {
            lazyListState.keepDpadReorderItemVisible(id, index + ROUTING_LIST_HEADER_COUNT)
        }
        rowFocusTargets[id]?.row?.requestFocus()
    }
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        reorderIndicesForKeys(rulesetIds, from.key, to.key)?.let { (fromIndex, toIndex) ->
            viewModel.move(fromIndex, toIndex)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.routing_settings_title),
                onBackClick = onBackClick,
                navigationFocusRequester = navigationFocusRequester,
                onMoveDown = { requestRowFocus(domainStrategyFocusRequester) },
                actionItems = listOf(
                    AppTopBarAction(painterResource(R.drawable.ic_add_24dp),
                        stringResource(R.string.routing_settings_add_rule), onAddRule),
                    AppTopBarAction(
                        painterResource(R.drawable.ic_more_vert_24dp), stringResource(R.string.action_more),
                        menuActions = RoutingMenuAction.entries.map { action ->
                            AppSelectionMenuAction(stringResource(action.labelRes)) {
                                when (action) {
                                    RoutingMenuAction.ImportPredefined -> showPresetDialog = true
                                    RoutingMenuAction.ImportClipboard -> onImportClipboard()
                                    RoutingMenuAction.ImportQRCode -> onImportQRcode()
                                    RoutingMenuAction.ExportClipboard -> onExportClipboard()
                                }
                            }
                        }
                    )
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .tvSafeAreaPadding()
                .dpadMovePreviousNavigation(enabled = !dpadReorderState.isMoving) {
                    navigationFocusRequester.requestFocus()
                }
                .verticalScrollbar(lazyListState),
            contentPadding = NavigationBarsBottomPadding()
        ) {
            item(key = "domain_strategy") {
                SettingsListItem(
                    title = stringResource(R.string.routing_settings_domain_strategy),
                    entries = domainStrategies,
                    values = domainStrategies,
                    selectedValue = domainStrategy,
                    onSelected = { onDomainStrategySelected(it) },
                    focusRequester = domainStrategyFocusRequester,
                    modifier = Modifier.dpadVerticalFocusNavigation(
                        onMoveUp = { navigationFocusRequester.requestFocus() },
                        onMoveDown = focusFirstRule
                    )
                )
            }

            itemsIndexed(items = rulesets, key = { _, ruleset -> ruleset.id }) { index, ruleset ->
                val editRule = {
                    val position = viewModel.positionOf(ruleset.id)
                    if (position >= 0) onEditRule(position)
                }
                val setRuleEnabled: (Boolean) -> Unit = { checked ->
                    viewModel.update(viewModel.positionOf(ruleset.id), ruleset.copy(enabled = checked))
                }
                val focusTargets = rowFocusTargets.getValue(ruleset.id)
                val previousTargets = rulesets.getOrNull(index - 1)?.let { rowFocusTargets[it.id] }
                val nextTargets = rulesets.getOrNull(index + 1)?.let { rowFocusTargets[it.id] }
                val dpadReorderItem = DpadReorderItem(
                    state = dpadReorderState,
                    key = ruleset.id,
                    index = index,
                    itemCount = rulesets.size,
                    targetIndex = ::verticalDpadReorderTarget,
                    onMove = viewModel::move
                )
                val isMoving = dpadReorderState.isMoving(ruleset.id)
                val actionFocus = DpadRowActionFocus(focusTargets, previousTargets, nextTargets, focusTargets.order, requestRowFocus)
                ReorderableItem(reorderableState, key = ruleset.id, modifier = Modifier.zIndex(if (isMoving) 1f else 0f)) { isDragging ->
                    ReorderableListItem(scope = this, isDragging = isDragging, isMoving = isMoving) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isTelevision) {
                                        Modifier
                                            .padding(vertical = 8.dp)
                                            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                                            .dpadFocusOutline(
                                                focusRequester = focusTargets.row,
                                                cornerRadius = 16.dp,
                                                showFocus = !isMoving
                                            )
                                            .dpadOrderedFocusNavigation(
                                                current = focusTargets.row,
                                                order = focusTargets.order,
                                                onBeforeFirst = { navigationFocusRequester.requestFocus() }
                                            )
                                            .dpadVerticalFocusNavigation(
                                                onMoveUp = {
                                                    previousTargets?.row?.let(requestRowFocus)
                                                        ?: requestRowFocus(domainStrategyFocusRequester)
                                                },
                                                onMoveDown = {
                                                    nextTargets?.row?.let(requestRowFocus) ?: true
                                                }
                                            )
                                            .dpadLongPressToMove(
                                                enabled = isTelevision,
                                                item = dpadReorderItem,
                                                onClick = editRule
                                            )
                                            .padding(horizontal = 24.dp, vertical = 16.dp)
                                    } else {
                                        Modifier.padding(horizontal = 16.dp)
                                    }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
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
                                val domainIpInfo =
                                    listOfNotNull(ruleset.domain, ruleset.ip, ruleset.process, ruleset.port).joinToString(" • ")
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
                                    Text(ruleset.outboundTag, style = MaterialTheme.typography.labelMedium, color = colorConfigType)
                                }
                            }

                            if (isTelevision) {
                                Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AppIconButton(
                                        icon = painterResource(R.drawable.ic_edit_24dp),
                                        label = stringResource(R.string.action_edit),
                                        focusRequester = focusTargets.edit,
                                        modifier = actionFocus.modifier(RoutingRowFocusTargets::edit),
                                        onClick = editRule
                                    )
                                    AppIconButton(
                                        icon = painterResource(R.drawable.ic_delete_24dp),
                                        label = stringResource(R.string.action_delete),
                                        focusRequester = focusTargets.delete,
                                        modifier = actionFocus.modifier(RoutingRowFocusTargets::delete),
                                        onClick = { deleteRuleId = ruleset.id }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    AppRowSwitch(
                                        checked = ruleset.enabled,
                                        onCheckedChange = setRuleEnabled,
                                        label = stringResource(R.string.routing_settings_enable_rule),
                                        focusRequester = focusTargets.toggle,
                                        modifier = actionFocus.modifier(RoutingRowFocusTargets::toggle)
                                    )
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                                    AppIconButton(
                                        icon = painterResource(R.drawable.ic_edit_24dp),
                                        label = stringResource(R.string.action_edit),
                                        onClick = editRule
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Switch(
                                        checked = ruleset.enabled,
                                        onCheckedChange = setRuleEnabled,
                                        modifier = Modifier.scale(0.7f),
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                                            checkedTrackColor = MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                }
                            }
                        }
                    }
                    if (!isTelevision) {
                        ItemDivider()
                    }
                }
            }
        }
    }


    deleteRuleId?.let { ruleId ->
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_routing_rule),
            onConfirm = {
                viewModel.remove(viewModel.positionOf(ruleId))
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
