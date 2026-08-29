package com.v2ray.ang.ui.routing

import android.app.Application
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

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

    fun update(position: Int, item: RulesetItem) {
        if (position in rulesets.indices) {
            rulesets[position] = item
            SettingsManager.saveRoutingRuleset(position, item)
            _rulesetsFlow.value = rulesets.toList()
        }
    }

    fun remove(position: Int) {
        if (position in rulesets.indices) {
            SettingsManager.removeRoutingRuleset(position)
            rulesets.removeAt(position)
            _rulesetsFlow.value = rulesets.toList()
        }
    }

    fun move(fromPosition: Int, toPosition: Int) {
        if (rulesets.moveItem(fromPosition, toPosition)) {
            MmkvManager.encodeRoutingRulesets(rulesets)
            _rulesetsFlow.value = rulesets.toList()
        }
    }
}
