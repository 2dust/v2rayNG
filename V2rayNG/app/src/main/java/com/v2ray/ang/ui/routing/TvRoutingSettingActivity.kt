package com.v2ray.ang.ui.routing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DpadReorderItem
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.TvDeleteConfirmDialog
import com.v2ray.ang.ui.compose.TvIconButton
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.dpadClickable
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.dpadLongPressToMove
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.dpadPopupHorizontalNavigation
import com.v2ray.ang.ui.compose.dpadTopBarFocusNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.keepDpadReorderItemVisible
import com.v2ray.ang.ui.compose.rememberDpadFocusTargets
import com.v2ray.ang.ui.compose.rememberLazyDpadFocus
import com.v2ray.ang.ui.compose.rememberSyncedDpadReorderState
import com.v2ray.ang.ui.compose.requestFocusWhenReady
import com.v2ray.ang.ui.compose.verticalDpadReorderTarget
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.ui.settings.TvSettingsListDialog

class TvRoutingSettingActivity : BaseRoutingSettingActivity() {
    @Composable
    override fun ScreenContent() {
        val rules by viewModel.rulesetsFlow.collectAsStateWithLifecycle()
        val loading by viewModel.isLoading.collectAsStateWithLifecycle()
        val strategy by domainStrategyState.collectAsStateWithLifecycle()
        TvRoutingSettingScreen(rules, loading, strategy, ::dispatch)
    }

    private fun dispatch(action: RoutingListAction) {
        when (action) {
            RoutingListAction.Back -> finish()
            RoutingListAction.Add -> addRule()
            is RoutingListAction.Edit -> viewModel.positionOf(action.id).takeIf { it >= 0 }?.let(::editRule)
            is RoutingListAction.Enable -> viewModel.setRuleEnabled(action.id, action.enabled)
            is RoutingListAction.Delete -> viewModel.deleteRule(action.id)
            is RoutingListAction.Move -> viewModel.move(action.from, action.to)
            is RoutingListAction.Strategy -> setDomainStrategy(action.value)
            is RoutingListAction.Preset -> importPredefined(action.value.type)
            is RoutingListAction.Menu -> when (action.value) {
                RoutingMenuAction.ImportClipboard -> importFromClipboard()
                RoutingMenuAction.ImportQRCode -> importQRcode()
                RoutingMenuAction.ExportClipboard -> export2Clipboard()
                RoutingMenuAction.ImportPredefined -> Unit // The TV list asks which preset to import first.
            }
        }
    }
}

private sealed interface RoutingListAction {
    data object Back : RoutingListAction
    data object Add : RoutingListAction
    data class Edit(val id: String) : RoutingListAction
    data class Enable(val id: String, val enabled: Boolean) : RoutingListAction
    data class Delete(val id: String) : RoutingListAction
    data class Move(val from: Int, val to: Int) : RoutingListAction
    data class Strategy(val value: String) : RoutingListAction
    data class Preset(val value: RoutingPreset) : RoutingListAction
    data class Menu(val value: RoutingMenuAction) : RoutingListAction
}

private enum class RoutingControl { Back, Add, More, Strategy, Rule, Enable, Edit, Delete }

private val ruleControls = listOf(RoutingControl.Rule, RoutingControl.Enable, RoutingControl.Edit, RoutingControl.Delete)

@Composable
private fun TvRoutingSettingScreen(rules: List<RulesetItem>, loading: Boolean, strategy: String, onAction: (RoutingListAction) -> Unit) {
    val ids = remember(rules) { rules.map { it.id } }
    val rowFocus = rememberDpadFocusTargets(ids) { ruleControls.associateWith { FocusRequester() } }
    val topFocus = remember {
        listOf(RoutingControl.Back, RoutingControl.Add, RoutingControl.More, RoutingControl.Strategy).associateWith { FocusRequester() }
    }
    val back = topFocus.getValue(RoutingControl.Back)
    val add = topFocus.getValue(RoutingControl.Add)
    val more = topFocus.getValue(RoutingControl.More)
    val strategyFocus = topFocus.getValue(RoutingControl.Strategy)
    val listState = rememberLazyListState()
    val requestRow = rememberLazyDpadFocus(listOf(listOf(strategyFocus)) + rowFocus.values.map { it.values.toList() }) {
        listState.scrollToItem(it)
    }
    val reorder = rememberSyncedDpadReorderState(ids) { id, index ->
        val key = id as? String ?: return@rememberSyncedDpadReorderState
        listState.keepDpadReorderItemVisible(key, index + 1) // The domain strategy and heading share one lazy item.
        rowFocus[key]?.get(RoutingControl.Rule)?.requestFocus()
    }
    var focusedId by rememberSaveable { mutableStateOf<String?>(null) }
    var focusedControl by rememberSaveable { mutableStateOf(RoutingControl.Back) }
    var showMenu by remember { mutableStateOf(false) }
    var showStrategy by rememberSaveable { mutableStateOf(false) }
    var showPresets by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var returnTarget by remember { mutableStateOf<FocusRequester?>(null) }
    val initialId = remember { focusedId }
    val initialControl = remember { focusedControl }
    var needsInitialFocus by remember { mutableStateOf(true) }

    LaunchedEffect(rules, loading, showStrategy, showPresets, deleteId) {
        if (needsInitialFocus && !showStrategy && !showPresets && deleteId == null &&
            (initialId == null || initialId in rowFocus || !loading)) {
            needsInitialFocus = false
            val target = rowFocus[initialId]?.get(initialControl)
            if (target == null || !requestRow(target)) requestFocusWhenReady(topFocus[initialControl] ?: back)
        }
    }
    LaunchedEffect(returnTarget) {
        returnTarget?.let { requestRow(it); returnTarget = null }
    }

    fun focusTarget(control: RoutingControl, id: String? = null) = Modifier.onFocusChanged {
        if (it.isFocused) {
            focusedControl = control
            focusedId = id
        }
    }

    fun restore(target: FocusRequester) {
        needsInitialFocus = false
        returnTarget = target
    }

    fun closeMenu(target: FocusRequester = more) {
        showMenu = false
        restore(target)
    }

    val topOrder = listOf(back, add, more)
    val moveBelowToolbar = { requestRow(strategyFocus) }
    Scaffold(topBar = {
        Box(Modifier.padding(horizontal = 48.dp)) {
            AppTopBar(stringResource(R.string.routing_settings_title), { onAction(RoutingListAction.Back) }, isLoading = loading,
                navigationIcon = {
                    TvIconButton(painterResource(R.drawable.ic_tv_arrow_back_24dp), stringResource(R.string.action_back),
                        { onAction(RoutingListAction.Back) }, focusTarget(RoutingControl.Back).dpadTopBarFocusNavigation(back, topOrder, moveBelowToolbar),
                        focusRequester = back, contentDescription = stringResource(R.string.acc_back))
                }, actions = {
                    TvIconButton(painterResource(R.drawable.ic_add_24dp), stringResource(R.string.routing_settings_add_rule),
                        { onAction(RoutingListAction.Add) }, focusTarget(RoutingControl.Add).dpadTopBarFocusNavigation(add, topOrder, moveBelowToolbar),
                        focusRequester = add, contentDescription = stringResource(R.string.acc_add_rule))
                    Box {
                        TvIconButton(painterResource(R.drawable.ic_more_vert_24dp), stringResource(R.string.action_more), { showMenu = true },
                            focusTarget(RoutingControl.More).dpadTopBarFocusNavigation(more, topOrder, moveBelowToolbar),
                            focusRequester = more, contentDescription = stringResource(R.string.acc_more))
                        DropdownMenu(showMenu, { closeMenu() }, modifier = Modifier.dpadPopupHorizontalNavigation(
                            onMovePrevious = { closeMenu(add) }, onMoveNext = { closeMenu() })) {
                            val firstItem = remember { FocusRequester() }
                            RoutingMenuAction.entries.forEach { action ->
                                DropdownMenuItem(text = { Text(stringResource(action.labelRes)) }, onClick = {
                                    if (action == RoutingMenuAction.ImportPredefined) {
                                        showMenu = false
                                        showPresets = true
                                    } else {
                                        closeMenu()
                                        onAction(RoutingListAction.Menu(action))
                                    }
                                }, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    .dpadFocusOutline(firstItem.takeIf { action == RoutingMenuAction.ImportPredefined }))
                            }
                            LaunchedEffect(firstItem) { requestFocusWhenReady(firstItem) }
                        }
                    }
                })
        }
    }) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 48.dp).verticalScrollbar(listState),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item(key = RoutingControl.Strategy) {
                Column {
                    Row(focusTarget(RoutingControl.Strategy).fillMaxWidth().heightIn(min = 48.dp).dpadFocusOutline(strategyFocus)
                        .dpadOrderedFocusNavigation(strategyFocus, listOf(strategyFocus), onBeforeFirst = { back.requestFocus() })
                        .dpadVerticalFocusNavigation({ back.requestFocus() }, { rowFocus[ids.firstOrNull()]?.get(RoutingControl.Rule)?.let(requestRow) ?: true })
                        .dpadClickable { showStrategy = true }.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.routing_settings_domain_strategy))
                        Text(strategy, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.routing_settings_rule_title), Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium)
                }
            }
            itemsIndexed(rules, key = { _, rule -> rule.id }) { index, rule ->
                val targets = rowFocus.getValue(rule.id)
                val order = targets.values.toList()
                val previous = rowFocus[ids.getOrNull(index - 1)]
                val next = rowFocus[ids.getOrNull(index + 1)]
                val moving = reorder.isMoving(rule.id)
                val movement = DpadReorderItem(reorder, rule.id, index, rules.size, ::verticalDpadReorderTarget) { from, to ->
                    onAction(RoutingListAction.Move(from, to))
                }
                val actionFocus: @Composable (RoutingControl) -> Modifier = { control ->
                    focusTarget(control, rule.id)
                        .dpadOrderedFocusNavigation(targets.getValue(control), order,
                            onBeforeFirst = { back.requestFocus() })
                        .dpadVerticalFocusNavigation({ requestRow(previous?.get(control) ?: strategyFocus) },
                            { next?.get(control)?.let(requestRow) ?: true })
                }
                Column(Modifier.zIndex(if (moving) 1f else 0f)) {
                    TvRoutingRuleRow(rule, targets, actionFocus, moving, movement,
                        onEdit = { onAction(RoutingListAction.Edit(rule.id)) },
                        onEnable = { onAction(RoutingListAction.Enable(rule.id, it)) },
                        onDelete = { deleteId = rule.id })
                    ItemDivider()
                }
            }
        }
    }

    if (showStrategy) {
        val options = stringArrayResource(R.array.routing_domain_strategy).map { it to it }
        TvSettingsListDialog(stringResource(R.string.routing_settings_domain_strategy), options, strategy,
            onSelected = { showStrategy = false; onAction(RoutingListAction.Strategy(it)); restore(strategyFocus) },
            onDismiss = { showStrategy = false; restore(strategyFocus) })
    }
    if (showPresets) {
        val options = RoutingPreset.entries.map { stringResource(it.labelRes) to it.name }
        TvSettingsListDialog(stringResource(R.string.routing_settings_import_predefined_rulesets), options, selectedValue = null,
            onSelected = { showPresets = false; onAction(RoutingListAction.Preset(RoutingPreset.valueOf(it))); restore(more) },
            onDismiss = { showPresets = false; restore(more) })
    }
    deleteId?.let { id ->
        val rule = rules.find { it.id == id }
        if (rule != null) {
            TvDeleteConfirmDialog(stringResource(R.string.confirm_delete_routing_rule), itemName = rule.remarks,
                onConfirm = {
                    deleteId = null
                    val neighbor = routingRuleNeighbor(ids, id)
                    onAction(RoutingListAction.Delete(id))
                    restore(rowFocus[neighbor]?.get(RoutingControl.Rule) ?: strategyFocus)
                },
                onDismiss = { deleteId = null; restore(rowFocus.getValue(id).getValue(RoutingControl.Delete)) })
        } else if (!loading) {
            LaunchedEffect(id) { deleteId = null; restore(strategyFocus) }
        }
    }
}

@Composable
private fun TvRoutingRuleRow(
    rule: RulesetItem, targets: Map<RoutingControl, FocusRequester>, navigation: @Composable (RoutingControl) -> Modifier,
    moving: Boolean, movement: DpadReorderItem, onEdit: () -> Unit, onEnable: (Boolean) -> Unit, onDelete: () -> Unit
) {
    var rowFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val decoration = when {
        moving -> Modifier.shadow(12.dp, shape).background(MaterialTheme.colorScheme.surfaceContainerHigh, shape).border(2.dp, colorConfigType, shape)
        rowFocused -> Modifier.background(MaterialTheme.colorScheme.primaryContainer, shape).border(2.dp, MaterialTheme.colorScheme.primary, shape)
        else -> Modifier
    }
    Row(Modifier.fillMaxWidth().then(decoration).padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RoutingRuleSummary(rule, Modifier.weight(1f).heightIn(min = 56.dp)
            .then(navigation(RoutingControl.Rule)).focusRequester(targets.getValue(RoutingControl.Rule))
            .onFocusChanged { rowFocused = it.isFocused }.dpadLongPressToMove(movement, onEdit)
            .padding(horizontal = 16.dp, vertical = 10.dp))
        var toggleFocused by remember { mutableStateOf(false) }
        val label = stringResource(R.string.routing_settings_enable_rule)
        Row(navigation(RoutingControl.Enable).heightIn(min = 48.dp).dpadFocusOutline(targets.getValue(RoutingControl.Enable), cornerRadius = 24.dp)
            .onFocusChanged { toggleFocused = it.isFocused }
            .toggleable(rule.enabled == true, role = Role.Switch, onValueChange = onEnable)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(rule.enabled == true, onCheckedChange = null, modifier = Modifier.scale(0.65f))
            if (toggleFocused) {
                Spacer(Modifier.width(8.dp))
                Text(label, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
        TvIconButton(painterResource(R.drawable.ic_edit_24dp), stringResource(R.string.action_edit), onEdit, navigation(RoutingControl.Edit),
            focusRequester = targets.getValue(RoutingControl.Edit), contentDescription = stringResource(R.string.acc_edit))
        TvIconButton(painterResource(R.drawable.ic_delete_24dp), stringResource(R.string.action_delete), onDelete, navigation(RoutingControl.Delete),
            focusRequester = targets.getValue(RoutingControl.Delete), contentDescription = stringResource(R.string.acc_delete))
    }
}

internal fun routingRuleNeighbor(ids: List<String>, id: String): String? {
    val index = ids.indexOf(id)
    return if (index < 0) null else ids.getOrNull(index + 1) ?: ids.getOrNull(index - 1)
}
