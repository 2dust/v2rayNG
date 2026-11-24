package com.v2ray.ang.ui.routing

import android.app.Application
import android.webkit.URLUtil
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.enums.RoutingType
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

class RoutingSettingsViewModel(application: Application) : BaseViewModel(application) {
    private val rulesets: MutableList<RulesetItem> = mutableListOf()

    private val _rulesetsFlow = MutableStateFlow<List<RulesetItem>>(emptyList())
    val rulesetsFlow: StateFlow<List<RulesetItem>> = _rulesetsFlow.asStateFlow()

    fun getAll(): List<RulesetItem> = rulesets.toList()

    internal fun editablePosition(id: String): Int = if (isLoading.value) -1 else rulesets.indexOfFirst { it.id == id }

    internal fun setRuleEnabled(id: String, enabled: Boolean) {
        val position = editablePosition(id)
        val rule = rulesets.getOrNull(position) ?: return
        val updated = rule.copy(enabled = enabled)
        SettingsManager.saveRoutingRuleset(position, updated)
        rulesets[position] = updated
        _rulesetsFlow.value = rulesets.toList()
    }

    internal fun deleteRule(id: String) {
        val position = editablePosition(id)
        if (position < 0) return
        launchLoading {
            withContext(Dispatchers.IO) { SettingsManager.removeRoutingRuleset(position) }
            reload()
        }
    }

    internal fun reloadIfIdle() {
        if (!isLoading.value) launchLoading { reload() }
    }

    private suspend fun reload() {
        val loaded = withContext(Dispatchers.IO) {
            val rules = MmkvManager.decodeRoutingRulesets() ?: mutableListOf()
            val missingIds = rules.filter { it.id.isEmpty() }
            missingIds.forEach { it.id = UUID.randomUUID().toString() }
            if (missingIds.isNotEmpty()) MmkvManager.encodeRoutingRulesets(rules)
            rules
        }
        rulesets.clear()
        rulesets.addAll(loaded)
        _rulesetsFlow.value = rulesets.toList()
    }

    internal fun move(fromId: String, toId: String) {
        val fromPosition = editablePosition(fromId)
        val toPosition = editablePosition(toId)
        if (rulesets.moveItem(fromPosition, toPosition)) {
            MmkvManager.encodeRoutingRulesets(rulesets)
            _rulesetsFlow.value = rulesets.toList()
        }
    }

    internal fun importPredefined(type: RoutingType) = importRules {
        SettingsManager.resetRoutingRulesetsFromPresets(getApplication(), type)
    }

    fun importRulesets(content: String?) = importRules {
        if (content.isNullOrBlank()) return@importRules false
        var realContent = content.trim()
        if (URLUtil.isNetworkUrl(realContent)) {
            val fetched = HttpUtil.getUrlContent(UrlContentRequest(url = realContent, timeout = 10000))
            realContent = if (fetched.isNullOrEmpty()) {
                HttpUtil.getUrlContent(
                    UrlContentRequest(
                        url = realContent,
                        timeout = 10000,
                        httpPort = SettingsManager.getHttpPort(),
                        proxyUsername = SettingsManager.getSocksUsername(),
                        proxyPassword = SettingsManager.getSocksPassword()
                    )
                ).orEmpty()
            } else {
                fetched
            }
        }
        SettingsManager.resetRoutingRulesets(realContent)
    }

    private fun importRules(replaceRules: suspend () -> Boolean) {
        if (isLoading.value) return
        launchLoading {
            // Keep edits blocked until the displayed list matches the persisted replacement.
            try {
                if (withContext(Dispatchers.IO) { replaceRules() }) {
                    reload()
                    toastSuccess(R.string.toast_success)
                } else {
                    toastError(R.string.toast_failure)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to import routing rules", e)
                toastError(R.string.toast_failure)
            }
        }
    }
}
