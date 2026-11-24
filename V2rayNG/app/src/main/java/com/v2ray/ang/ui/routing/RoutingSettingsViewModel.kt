package com.v2ray.ang.ui.routing

import android.app.Application
import android.webkit.URLUtil
import com.v2ray.ang.R
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.ui.compose.ReorderCommand
import com.v2ray.ang.util.HttpUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RoutingSettingsViewModel(application: Application) : BaseViewModel(application) {
    private val changes = Mutex()

    private val _rulesetsFlow = MutableStateFlow<List<RulesetItem>>(emptyList())
    val rulesetsFlow: StateFlow<List<RulesetItem>> = _rulesetsFlow.asStateFlow()

    fun getAll(): List<RulesetItem> = _rulesetsFlow.value

    suspend fun reload() = changes.withLock {
        _rulesetsFlow.value = withContext(Dispatchers.IO) {
            MmkvManager.decodeRoutingRulesetsForEditing().orEmpty()
        }
    }

    suspend fun setEnabled(ruleId: String, enabled: Boolean) = changeRulesets {
        val rule = firstOrNull { it.id == ruleId } ?: return@changeRulesets false
        if (rule.enabled == enabled) return@changeRulesets false
        rule.enabled = enabled
        true
    }

    suspend fun move(fromId: String, toId: String): Boolean = changeRulesets {
        moveItem(indexOfFirst { it.id == fromId }, indexOfFirst { it.id == toId })
    }

    internal suspend fun move(ruleId: String, command: ReorderCommand): Boolean = changeRulesets {
        val from = indexOfFirst { it.id == ruleId }
        val to = command.targetIndex(from, size) ?: return@changeRulesets false
        moveItem(from, to)
    }

    suspend fun remove(ruleId: String) = changes.withLock {
        _rulesetsFlow.value = withContext(Dispatchers.IO) {
            MmkvManager.removeRoutingRuleset(ruleId)
        }
    }

    private suspend fun changeRulesets(change: MutableList<RulesetItem>.() -> Boolean): Boolean = changes.withLock {
        val saved = withContext(Dispatchers.IO) { MmkvManager.changeRoutingRulesets(change) }
            ?: return@withLock false
        _rulesetsFlow.value = saved
        true
    }

    fun importRulesets(content: String?) {
        launchLoading {
            if (content.isNullOrBlank()) {
                toastError(R.string.toast_failure)
                return@launchLoading
            }

            val result = withContext(Dispatchers.IO) {
                var realContent = content.trim()
                if (URLUtil.isNetworkUrl(realContent)) {
                    val fetched = HttpUtil.getUrlContent(UrlContentRequest(url = realContent, timeout = 10000))
                    if (fetched.isNullOrEmpty()) {
                        val proxyUsername = SettingsManager.getSocksUsername()
                        val proxyPassword = SettingsManager.getSocksPassword()
                        val httpPort = SettingsManager.getHttpPort()
                        realContent = HttpUtil.getUrlContent(
                            UrlContentRequest(
                                url = realContent,
                                timeout = 10000,
                                httpPort = httpPort,
                                proxyUsername = proxyUsername,
                                proxyPassword = proxyPassword
                            )
                        ).orEmpty()
                    } else {
                        realContent = fetched
                    }
                }

                SettingsManager.resetRoutingRulesets(realContent)
            }

            if (result) {
                reload()
                toastSuccess(R.string.toast_success)
            } else {
                toastError(R.string.toast_failure)
            }
        }
    }
}
