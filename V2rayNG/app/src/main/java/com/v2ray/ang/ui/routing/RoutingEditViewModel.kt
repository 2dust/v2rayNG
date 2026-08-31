package com.v2ray.ang.ui.routing

import android.app.Application
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class RoutingEditState(
    val initial: RulesetItem?,
    val outboundSuggestions: List<String>,
    val canUseProcess: Boolean
)

class RoutingEditViewModel(application: Application) : BaseViewModel(application) {
    private val _state = MutableStateFlow<RoutingEditState?>(null)
    val state: StateFlow<RoutingEditState?> = _state.asStateFlow()
    private var initialized = false
    private var rulesetId: String? = null

    fun initialize(id: String?) {
        if (initialized) return
        initialized = true
        rulesetId = id
        launchLoading {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val initial = id?.let { SettingsManager.getRoutingRuleset(it) }
                    // No ID is the Add action. Only a missing explicitly requested rule is invalid.
                    if (id != null && initial == null) return@withContext null
                    RoutingEditState(
                        initial = initial,
                        outboundSuggestions = (AppConfig.BUILTIN_OUTBOUND_TAGS.toList() + SettingsManager.getProfileRemarks()).distinct(),
                        canUseProcess = SettingsManager.canUseProcessRouting()
                    )
                }
                if (loaded == null) finishActivity() else _state.value = loaded
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to load routing rule id=$id", e)
                toastError(R.string.toast_failure)
                finishActivity()
            }
        }
    }

    fun save(ruleset: RulesetItem): Boolean {
        if (_state.value == null || isLoading.value) return false
        if (ruleset.remarks.isNullOrEmpty()) {
            toast(R.string.sub_setting_remarks)
            return false
        }
        persist("save") { SettingsManager.saveRoutingRuleset(rulesetId, ruleset) }
        return true
    }

    fun delete() {
        val id = rulesetId ?: return
        if (_state.value == null || isLoading.value) return
        persist("delete") { SettingsManager.removeRoutingRuleset(id) }
    }

    private fun persist(operation: String, write: () -> Unit) {
        launchLoading {
            try {
                withContext(Dispatchers.IO) { write() }
                if (operation == "save") toastSuccess(R.string.toast_success)
                finishActivity()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to $operation routing rule id=$rulesetId", e)
                toastError(R.string.toast_failure)
            }
        }
    }
}
