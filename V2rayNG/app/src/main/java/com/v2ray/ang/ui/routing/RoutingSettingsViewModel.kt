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

    fun move(fromPosition: Int, toPosition: Int): Boolean {
        if (rulesets.moveItem(fromPosition, toPosition)) {
            revision++
            MmkvManager.encodeRoutingRulesets(rulesets)
            _rulesetsFlow.value = rulesets.toList()
            return true
        }
        return false
    }

    internal fun move(ruleId: String, command: ReorderCommand): Boolean {
        val fromPosition = rulesets.indexOfFirst { it.id == ruleId }
        val toPosition = command.targetIndex(fromPosition, rulesets.size) ?: return false
        return move(fromPosition, toPosition)
    }

    suspend fun remove(ruleId: String) {
        revision++
        withContext(Dispatchers.IO) {
            MmkvManager.removeRoutingRuleset(ruleId)
        }
        revision++
        if (rulesets.removeAll { it.id == ruleId }) {
            _rulesetsFlow.value = rulesets.toList()
        }
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
