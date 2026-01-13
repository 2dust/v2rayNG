package com.v2ray.ang.viewmodel

import androidx.lifecycle.ViewModel
import com.v2ray.ang.dto.RulesetItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager

class RoutingSettingsViewModel : ViewModel() {
    private val rulesets: MutableList<RulesetItem> = mutableListOf()

    fun getAll(): List<RulesetItem> = rulesets.toList()

    fun reload() {
        rulesets.clear()
        rulesets.addAll(MmkvManager.decodeRoutingRulesets() ?: mutableListOf())
    }

    fun update(position: Int, item: RulesetItem) {
        if (position in rulesets.indices) {
            rulesets[position] = item
            SettingsManager.saveRoutingRuleset(position, item)
        }
    }

    fun swap(fromPosition: Int, toPosition: Int) {
        if (fromPosition in rulesets.indices && toPosition in rulesets.indices) {
            SettingsManager.swapRoutingRuleset(fromPosition, toPosition)
        }
    }
}

