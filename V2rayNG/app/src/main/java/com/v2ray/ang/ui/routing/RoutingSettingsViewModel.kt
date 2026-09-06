package com.v2ray.ang.ui.routing

import android.app.Application
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class RoutingSettingsViewModel(application: Application) : BaseViewModel(application) {
    private val rulesets: MutableList<RulesetItem> = mutableListOf()
    private var revision = 0

    private val _rulesetsFlow = MutableStateFlow<List<RulesetItem>>(emptyList())
    val rulesetsFlow: StateFlow<List<RulesetItem>> = _rulesetsFlow.asStateFlow()

    fun getAll(): List<RulesetItem> = rulesets.toList()

    suspend fun reload() {
        val requestedRevision = ++revision
        val loaded = withContext(Dispatchers.IO) {
            MmkvManager.decodeRoutingRulesetsForEditing().orEmpty()
        }
        if (requestedRevision != revision) return
        rulesets.clear()
        rulesets.addAll(loaded)
        _rulesetsFlow.value = rulesets.toList()
    }

    fun update(position: Int, item: RulesetItem) {
        if (position in rulesets.indices) {
            revision++
            rulesets[position] = item
            SettingsManager.saveRoutingRuleset(position, item)
            _rulesetsFlow.value = rulesets.toList()
        }
    }

    suspend fun remove(ruleId: String) {
        revision++
        withContext(Dispatchers.IO) {
            MmkvManager.removeRoutingRuleset(ruleId)
        }
        if (rulesets.removeAll { it.id == ruleId }) {
            _rulesetsFlow.value = rulesets.toList()
        }
    }

    fun move(fromPosition: Int, toPosition: Int) {
        if (rulesets.moveItem(fromPosition, toPosition)) {
            revision++
            MmkvManager.encodeRoutingRulesets(rulesets)
            _rulesetsFlow.value = rulesets.toList()
        }
    }
}
