package com.v2ray.ang.ui.routing

import android.app.Application
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.ui.compose.ReorderCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

internal data class RoutingRuleRemoval(
    val position: Int,
    val remainingRules: List<RulesetItem>
)

internal fun removeRoutingRule(
    rules: List<RulesetItem>,
    ruleId: String
): RoutingRuleRemoval? {
    val position = rules.indexOfFirst { it.id == ruleId }
    if (position < 0) return null

    return RoutingRuleRemoval(
        position = position,
        remainingRules = rules.toMutableList().apply { removeAt(position) }
    )
}

class RoutingSettingsViewModel(application: Application) : BaseViewModel(application) {
    private val rulesets: MutableList<RulesetItem> = mutableListOf()

    private val _rulesetsFlow = MutableStateFlow<List<RulesetItem>>(emptyList())
    val rulesetsFlow: StateFlow<List<RulesetItem>> = _rulesetsFlow.asStateFlow()

    fun getAll(): List<RulesetItem> = rulesets.toList()

    fun reload() {
        val loaded = MmkvManager.decodeRoutingRulesets()?.toMutableList() ?: mutableListOf()
        var needsSave = false
        loaded.forEachIndexed { index, item ->
            if (item.id.isEmpty()) {
                item.id = UUID.randomUUID().toString()
                SettingsManager.saveRoutingRuleset(index, item)
                needsSave = true
            }
        }
        rulesets.clear()
        rulesets.addAll(loaded)
        _rulesetsFlow.value = rulesets.toList()
    }

    fun update(ruleId: String, item: RulesetItem) {
        val position = rulesets.indexOfFirst { it.id == ruleId }
        if (position >= 0) {
            rulesets[position] = item
            SettingsManager.saveRoutingRuleset(position, item)
            _rulesetsFlow.value = rulesets.toList()
        }
    }

    fun remove(ruleId: String) {
        val removal = removeRoutingRule(rulesets, ruleId) ?: return
        SettingsManager.removeRoutingRuleset(ruleId)
        rulesets.clear()
        rulesets.addAll(removal.remainingRules)
        _rulesetsFlow.value = rulesets.toList()
    }

    fun move(fromPosition: Int, toPosition: Int): Boolean {
        if (rulesets.moveItem(fromPosition, toPosition)) {
            MmkvManager.encodeRoutingRulesets(rulesets)
            _rulesetsFlow.value = rulesets.toList()
            return true
        }
        return false
    }

    internal fun move(ruleId: String, command: ReorderCommand): Boolean {
        val fromPosition = rulesets.indexOfFirst { it.id == ruleId }
        val toPosition = command.targetIndex(fromPosition, rulesets.size) ?: return false
        return move(fromPosition, toPosition)
    }
}
