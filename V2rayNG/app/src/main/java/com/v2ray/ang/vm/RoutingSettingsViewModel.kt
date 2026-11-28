package com.v2ray.ang.vm

import android.app.Application
import android.webkit.URLUtil
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.dto.entities.ensureUniqueIds
import com.v2ray.ang.enums.RoutingType
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class RoutingSettingsViewModel(application: Application) : BaseViewModel(application) {
    private val rulesets: MutableList<RulesetItem> = mutableListOf()

    private val _rulesetsFlow = MutableStateFlow<List<RulesetItem>>(emptyList())
    val rulesetsFlow: StateFlow<List<RulesetItem>> = _rulesetsFlow.asStateFlow()

    private val _domainStrategy = MutableStateFlow(loadDomainStrategy())
    val domainStrategy: StateFlow<String> = _domainStrategy.asStateFlow()

    private fun loadDomainStrategy(): String {
        val strategies = localizedContext.resources.getStringArray(R.array.routing_domain_strategy)
        return MmkvManager.decodeSettingsString(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY) ?: strategies.first()
    }

    fun setDomainStrategy(value: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY, value)
        _domainStrategy.value = value
    }

    fun getAll(): List<RulesetItem> = rulesets.toList()

    fun reload() {
        val loaded = MmkvManager.decodeRoutingRulesets() ?: mutableListOf()
        if (loaded.ensureUniqueIds()) MmkvManager.encodeRoutingRulesets(loaded)
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

    fun move(fromPosition: Int, toPosition: Int) {
        if (rulesets.moveItem(fromPosition, toPosition)) {
            MmkvManager.encodeRoutingRulesets(rulesets)
            _rulesetsFlow.value = rulesets.toList()
        }
    }

    fun importPredefined(type: RoutingType) {
        launchLoading {
            try {
                withContext(Dispatchers.IO) {
                    SettingsManager.resetRoutingRulesetsFromPresets(app, type)
                }
                reload()
                toastSuccess(R.string.toast_success)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to import predefined ruleset", e)
                toastError(R.string.toast_failure)
            }
        }
    }

    fun importFromClipboard() {
        val clipboard = try {
            Utils.getClipboard(app)
        } catch (e: Exception) {
            toastError(R.string.toast_failure)
            return
        }
        importRulesets(clipboard)
    }

    fun exportToClipboard() {
        val rulesetList = MmkvManager.decodeRoutingRulesets()
        if (rulesetList.isNullOrEmpty()) {
            toastError(R.string.toast_failure)
        } else {
            Utils.setClipboard(app, JsonUtil.toJson(rulesetList))
            toastSuccess(R.string.toast_success)
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
