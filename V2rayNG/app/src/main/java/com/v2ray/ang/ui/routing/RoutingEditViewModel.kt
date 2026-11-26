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
    internal data class Form(val initial: RulesetItem?, val canUseProcess: Boolean)
    internal enum class Result { Saved, Deleted, LoadFailed, EditFailed }

    internal data class UiState(
        val form: Form? = null,
        val busy: Boolean = false,
        val remarksError: Boolean = false,
        val result: Result? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()
    private var openedPosition: Int? = null

    fun load(position: Int) {
        if (openedPosition != null) return
        openedPosition = position
        runEdit("load", Result.LoadFailed) {
            val initial = if (position < 0) null else checkNotNull(MmkvManager.decodeRoutingRulesets()?.getOrNull(position))
            val form = Form(initial, SettingsManager.canUseProcessRouting())
            copy(form = form)
        }
    }

    fun save(rule: RulesetItem) {
        val state = _uiState.value
        if (state.busy) return
        if (rule.remarks.isNullOrBlank()) {
            _uiState.value = state.copy(remarksError = true)
            return
        }
        val form = state.form ?: return
        val position = openedPosition ?: return
        runEdit("save", Result.EditFailed, state.copy(remarksError = false)) {
            val rules = MmkvManager.decodeRoutingRulesets() ?: mutableListOf()
            saveEditedRule(rules, position, form.initial, rule)
            MmkvManager.encodeRoutingRulesets(rules)
            copy(result = Result.Saved)
        }
    }

    fun delete() {
        val initial = _uiState.value.form?.initial ?: return
        val position = openedPosition ?: return
        runEdit("delete", Result.EditFailed) {
            val rules = MmkvManager.decodeRoutingRulesets() ?: mutableListOf()
            val index = findEditedRule(rules, position, initial)
            check(index >= 0)
            rules.removeAt(index)
            MmkvManager.encodeRoutingRulesets(rules)
            copy(result = Result.Deleted)
        }
    }

    fun dismissEditFailure() {
        val state = _uiState.value
        if (state.result == Result.EditFailed) _uiState.value = state.copy(result = null)
    }

    private fun runEdit(operation: String, onFailure: Result, state: UiState = _uiState.value, block: UiState.() -> UiState) {
        if (state.busy || state.result == Result.Saved || state.result == Result.Deleted) return
        val next = state.copy(result = null)
        _uiState.value = next.copy(busy = true)
        viewModelScope.launch {
            _uiState.value = try {
                withContext(Dispatchers.IO) { next.block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(message = "Routing rule editor: $operation failed", throwable = e)
                next.copy(result = onFailure)
            }
        }
    }
}

/** Resolve and update the same snapshot; only the form fields belong to the draft. */
internal fun saveEditedRule(rules: MutableList<RulesetItem>, originalPosition: Int, initial: RulesetItem?, draft: RulesetItem) {
    if (initial == null) {
        rules.add(0, draft.copy(id = UUID.randomUUID().toString()))
    } else {
        val index = findEditedRule(rules, originalPosition, initial)
        check(index >= 0)
        val current = rules[index]
        rules[index] = draft.copy(id = current.id, enabled = current.enabled)
    }
}

internal fun findEditedRule(rules: List<RulesetItem>, originalPosition: Int, initial: RulesetItem): Int {
    if (rules.getOrNull(originalPosition) == initial) return originalPosition
    val id = initial.id
    if (id.isBlank()) return -1
    // A preset import can preserve a locked rule and bring in another with the same ID.
    val matches = rules.indices.filter { rules[it].id == id }
    return matches.singleOrNull() ?: matches.singleOrNull { rules[it] == initial } ?: -1
}
