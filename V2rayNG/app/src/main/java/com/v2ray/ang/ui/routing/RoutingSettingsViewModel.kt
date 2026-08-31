package com.v2ray.ang.ui.routing

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoutingSettingsViewModel(application: Application) : BaseViewModel(application) {
    private val rulesets: MutableList<RulesetItem> = mutableListOf()

    private val _rulesetsFlow = MutableStateFlow<List<RulesetItem>>(emptyList())
    val rulesetsFlow: StateFlow<List<RulesetItem>> = _rulesetsFlow.asStateFlow()
    private var reloadJob: Job? = null

    fun getAll(): List<RulesetItem> = rulesets.toList()

    fun reload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) { MmkvManager.decodeRoutingRulesets().orEmpty() }
                rulesets.clear()
                rulesets.addAll(loaded)
                _rulesetsFlow.value = rulesets.toList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to load routing rules", e)
                toastError(R.string.toast_failure)
            }
        }
    }

    fun update(position: Int, item: RulesetItem) {
        if (position in rulesets.indices) {
            reloadJob?.cancel()
            rulesets[position] = item
            SettingsManager.saveRoutingRuleset(position, item)
            _rulesetsFlow.value = rulesets.toList()
        }
    }

    fun move(fromPosition: Int, toPosition: Int) {
        if (rulesets.moveItem(fromPosition, toPosition)) {
            reloadJob?.cancel()
            MmkvManager.encodeRoutingRulesets(rulesets)
            _rulesetsFlow.value = rulesets.toList()
        }
    }
}
