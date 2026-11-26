package com.v2ray.ang.ui.routing

import android.app.Application
import android.webkit.URLUtil
import com.v2ray.ang.R
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.dto.entities.ensureUniqueIds
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.handler.CoreDownloadManager
import com.v2ray.ang.handler.CoreFetchResult
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.HttpUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class RoutingSettingsViewModel(application: Application) : BaseViewModel(application) {
    private val rulesets: MutableList<RulesetItem> = mutableListOf()

    private val _rulesetsFlow = MutableStateFlow<List<RulesetItem>>(emptyList())
    val rulesetsFlow: StateFlow<List<RulesetItem>> = _rulesetsFlow.asStateFlow()

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

    fun importRulesets(content: String?) {
        launchLoading {
            if (content.isNullOrBlank()) {
                toastError(R.string.toast_failure)
                return@launchLoading
            }

            val result = withContext(Dispatchers.IO) {
                val rulesetContent = resolveRulesetImportContent(content, coreFetch = { request ->
                    CoreDownloadManager.fetchText(app, request, includeDefaultUserAgent = false)
                })
                ensureActive()
                SettingsManager.resetRoutingRulesets(rulesetContent)
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

internal fun resolveRulesetImportContent(
    content: String,
    coreFetch: (UrlContentRequest) -> CoreFetchResult<String>,
    isNetworkUrl: (String) -> Boolean = URLUtil::isNetworkUrl,
    directFetch: (UrlContentRequest) -> String? = HttpUtil::getUrlContent,
    proxyFetch: (UrlContentRequest) -> String? = { request ->
        HttpUtil.getUrlContent(
            request.copy(
                httpPort = SettingsManager.getHttpPort(),
                proxyUsername = SettingsManager.getSocksUsername(),
                proxyPassword = SettingsManager.getSocksPassword(),
            )
        )
    },
): String? {
    val trimmedContent = content.trim()
    if (!isNetworkUrl(trimmedContent)) return trimmedContent

    val request = UrlContentRequest(url = trimmedContent, timeout = 10_000)
    return when (val result = coreFetch(request)) {
        is CoreFetchResult.Success -> result.value
        CoreFetchResult.NotApplicable -> directFetch(request).takeUnless { it.isNullOrEmpty() }
            ?: proxyFetch(request)
        // Like subscription imports, keep eligible user-provided URLs on the core path.
        CoreFetchResult.Unavailable, CoreFetchResult.Failed -> null
    }
}
