package com.v2ray.ang.viewmodel

import androidx.lifecycle.ViewModel
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class RoutingSettingsViewModel : ViewModel() {
    private val rulesets: MutableList<RulesetItem> = mutableListOf()

    private val _rulesetsFlow = MutableStateFlow<List<RulesetItem>>(emptyList())
    val rulesetsFlow = _rulesetsFlow.asStateFlow()

    @Synchronized
    fun getAll(): List<RulesetItem> = rulesets.toList()

    @Synchronized
    fun reload() {
        rulesets.clear()
        rulesets.addAll(MmkvManager.decodeRoutingRulesets() ?: mutableListOf())
        _rulesetsFlow.value = rulesets.toList()
    }

    @Synchronized
    fun update(position: Int, item: RulesetItem) {
        if (position in rulesets.indices) {
            rulesets[position] = item
            SettingsManager.saveRoutingRuleset(position, item)
        }
    }

    @Synchronized
    fun swap(fromPosition: Int, toPosition: Int) {
        if (fromPosition in rulesets.indices && toPosition in rulesets.indices) {
            java.util.Collections.swap(rulesets, fromPosition, toPosition)
            SettingsManager.swapRoutingRuleset(fromPosition, toPosition)
            _rulesetsFlow.value = rulesets.toList()
        }
    }
}

