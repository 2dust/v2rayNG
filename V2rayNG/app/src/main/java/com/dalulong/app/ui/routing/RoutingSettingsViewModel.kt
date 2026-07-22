package com.dalulong.app.ui.routing

import android.app.Application
import com.dalulong.app.dto.entities.RulesetItem
import com.dalulong.app.handler.MmkvManager
import com.dalulong.app.handler.SettingsManager
import com.dalulong.app.ui.base.BaseViewModel
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

    fun swap(fromPosition: Int, toPosition: Int) {
        if (fromPosition in rulesets.indices && toPosition in rulesets.indices) {
            SettingsManager.swapRoutingRuleset(fromPosition, toPosition)
            val item = rulesets.removeAt(fromPosition)
            rulesets.add(toPosition, item)
            _rulesetsFlow.value = rulesets.toList()
        }
    }
}