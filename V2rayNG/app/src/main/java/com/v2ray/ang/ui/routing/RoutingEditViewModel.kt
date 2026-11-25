package com.v2ray.ang.ui.routing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

internal class RoutingEditViewModel : ViewModel() {
    internal data class Form(val initial: RulesetItem?, val outbounds: List<String>, val canUseProcess: Boolean)
    internal enum class Result { Saved, Deleted, LoadFailed, EditFailed }

    private val _form = MutableStateFlow<Form?>(null)
    val form = _form.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _result = MutableStateFlow<Result?>(null)
    val result = _result.asStateFlow()
    private var loaded = false

    fun load(position: Int) {
        if (loaded) return
        loaded = true
        runEdit("load", Result.LoadFailed) {
            val initial = if (position < 0) null else checkNotNull(MmkvManager.decodeRoutingRulesets()?.getOrNull(position))
            val form = Form(initial, SettingsManager.getProfileRemarks(), SettingsManager.canUseProcessRouting())
            withContext(Dispatchers.Main) { _form.value = form }
            null
        }
    }

    fun save(position: Int, rule: RulesetItem) {
        if (rule.remarks.isNullOrBlank()) return
        val form = _form.value ?: return
        runEdit("save", Result.EditFailed) {
            if (form.initial == null) {
                SettingsManager.saveRoutingRuleset(-1, rule.copy(id = UUID.randomUUID().toString()))
            } else {
                val index = currentIndex(position, form.initial)
                SettingsManager.saveRoutingRuleset(index, rule)
            }
            Result.Saved
        }
    }

    fun delete(position: Int) {
        val initial = _form.value?.initial ?: return
        runEdit("delete", Result.EditFailed) {
            SettingsManager.removeRoutingRuleset(currentIndex(position, initial))
            Result.Deleted
        }
    }

    fun clearResult() {
        _result.value = null
    }

    private fun currentIndex(position: Int, initial: RulesetItem): Int {
        val rules = MmkvManager.decodeRoutingRulesets().orEmpty()
        return findEditedRule(rules, position, initial).also { check(it >= 0) }
    }

    private fun runEdit(operation: String, onFailure: Result, block: suspend () -> Result?) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                _result.value = withContext(Dispatchers.IO) { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(message = "Routing rule editor: $operation failed", throwable = e)
                _result.value = onFailure
            } finally {
                _busy.value = false
            }
        }
    }
}

internal fun findEditedRule(rules: List<RulesetItem>, originalPosition: Int, initial: RulesetItem): Int {
    val id = initial.id
    // Older rules may have no ID. Only write to the original slot if its rule still matches.
    if (id.isEmpty()) return originalPosition.takeIf { rules.getOrNull(it) == initial } ?: -1
    return rules.indexOfFirst { it.id == id }
}
