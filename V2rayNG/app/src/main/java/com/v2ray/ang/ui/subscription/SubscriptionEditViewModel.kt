package com.v2ray.ang.ui.subscription

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.toLongEx
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Serializable

internal data class SubscriptionDraft(
    val remarks: String, val url: String, val userAgent: String, val requestHeaders: String, val filter: String,
    val enabled: Boolean, val autoUpdate: Boolean, val interval: String, val allowInsecure: Boolean,
    val entry: String, val exit: String
) : Serializable {
    constructor(item: SubscriptionItem) : this(item.remarks, item.url, item.userAgent.orEmpty(), item.requestHeaders.orEmpty(),
        item.filter.orEmpty(), item.enabled, item.autoUpdate, item.updateInterval.toString(), item.allowInsecureUrl,
        item.prevProfile.orEmpty(), item.nextProfile.orEmpty())

    fun errors() = SubscriptionErrors(remarks.isBlank(),
        url.isNotEmpty() && (!Utils.isValidUrl(url) || (!allowInsecure && !Utils.isValidSubUrl(url))),
        autoUpdate && interval.toLongEx() < AppConfig.SUBSCRIPTION_MIN_INTERVAL_MINUTES)

    // A subscription refresh may have updated usage and timestamps while the editor was open.
    fun applyTo(current: SubscriptionItem) = current.copy(remarks = remarks, url = url, userAgent = userAgent,
        requestHeaders = requestHeaders, filter = filter, enabled = enabled, autoUpdate = autoUpdate,
        updateInterval = interval.toLongEx(), allowInsecureUrl = allowInsecure, prevProfile = entry, nextProfile = exit)
}

internal data class SubscriptionErrors(val remarks: Boolean = false, val url: Boolean = false, val interval: Boolean = false) : Serializable

internal class SubscriptionEditViewModel(application: Application, private val saved: SavedStateHandle) : BaseViewModel(application) {
    data class Form(val name: String?, val suggestions: List<String>, val confirmRemove: Boolean)

    private val id = saved.get<String>("subId").orEmpty()
    private val targetId = id.ifEmpty { Utils.getUuid() }
    private val _form = MutableStateFlow<Form?>(null)
    val form = _form.asStateFlow()
    val draft = saved.getStateFlow<SubscriptionDraft?>("draft", null)
    val errors = saved.getStateFlow("errors", SubscriptionErrors())

    init {
        runOperation("load") {
            val (item, form) = withContext(Dispatchers.IO) {
                val item = readSubscription()
                val suggestions = SettingsManager.getProfileRemarks(setOf(EConfigType.CUSTOM, EConfigType.CUSTOM_OUTBOUND,
                    EConfigType.POLICYGROUP, EConfigType.PROXYCHAIN))
                item to Form(item.remarks.takeIf { id.isNotEmpty() }, suggestions, MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE, false))
            }
            if (draft.value == null) saved["draft"] = SubscriptionDraft(item)
            _form.value = form
        }
    }

    fun updateDraft(edit: SubscriptionDraft.() -> SubscriptionDraft) {
        draft.value?.let { saved["draft"] = it.edit() }
    }

    fun save() {
        val value = draft.value ?: return
        val invalid = value.errors()
        saved["errors"] = invalid
        if (invalid.remarks || invalid.url || invalid.interval) return
        runOperation("save", finishOnSuccess = true) {
            withContext(Dispatchers.IO) {
                MmkvManager.encodeSubscription(targetId, value.applyTo(readSubscription()))
                SubscriptionUpdater.syncOne(subId = targetId)
                SettingsChangeManager.makeSetupGroupTab()
            }
            toastSuccess(R.string.toast_success)
        }
    }

    fun delete() {
        if (id.isEmpty()) return
        runOperation("delete", finishOnSuccess = true) {
            withContext(Dispatchers.IO) {
                SettingsManager.removeSubscriptionWithDefault(id)
                SettingsChangeManager.makeSetupGroupTab()
            }
        }
    }

    private fun readSubscription() = if (id.isEmpty()) SubscriptionItem() else checkNotNull(MmkvManager.decodeSubscription(id))

    private fun runOperation(operation: String, finishOnSuccess: Boolean = false, block: suspend () -> Unit) {
        if (_isLoading.value) return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                block()
                if (finishOnSuccess) finishActivity() else _isLoading.value = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogUtil.e(message = "Subscription editor: $operation failed (group=$targetId)", throwable = e)
                toastError(R.string.toast_failure)
                if (_form.value == null) finishActivity() else _isLoading.value = false
            }
        }
    }
}
