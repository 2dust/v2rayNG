package com.v2ray.ang.ui.subscription

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.SubscriptionUpdateMessage
import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.extension.moveGuid
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.QRCodeDecoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class SubscriptionListState(
    val groups: List<SubscriptionCache>? = null,
    val updateOptions: Set<SubscriptionUpdateOption> = emptySet(),
    val confirmRemove: Boolean = false
)

/** Reuse loaded groups only if storage still contains exactly those IDs. */
internal fun List<SubscriptionCache>.inStoredOrder(ids: List<String>): List<SubscriptionCache>? {
    if (size != ids.size) return null
    val byId = associateByTo(mutableMapOf()) { it.guid }
    return ids.map { byId.remove(it) ?: return null }
}

class SubscriptionsViewModel(application: Application) : BaseViewModel(application) {
    private var qrCodeJob: Job? = null
    private val _qrCode = MutableStateFlow<Bitmap?>(null)
    internal val qrCode = _qrCode.asStateFlow()
    private val _state = MutableStateFlow(SubscriptionListState())
    internal val state = _state.asStateFlow()

    // An onResume reload must not publish an older list over a move, toggle or deletion.
    private val storage = Mutex()
    private fun withStorage(operation: String, block: () -> Unit) = viewModelScope.launch {
        try {
            storage.withLock { withContext(Dispatchers.IO) { block() } }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Subscription list: $operation failed", e)
            toastError(R.string.toast_failure)
        }
    }

    fun reload() = withStorage("reload") {
        _state.value = SubscriptionListState(
            groups = MmkvManager.decodeSubscriptions(),
            updateOptions = SubscriptionUpdateOption.entries.filterTo(mutableSetOf()) { MmkvManager.decodeSettingsBool(it.key, false) },
            confirmRemove = MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE, false)
        )
    }

    internal fun setOption(option: SubscriptionUpdateOption, enabled: Boolean) = withStorage("set update option") {
        check(MmkvManager.encodeSettings(option.key, enabled))
        val options = _state.value.updateOptions
        _state.value = _state.value.copy(updateOptions = if (enabled) options + option else options - option)
    }

    private fun refreshGroups() {
        _state.value = _state.value.copy(groups = MmkvManager.decodeSubscriptions())
    }

    fun remove(subId: String) = withStorage("remove group") {
        if (MmkvManager.decodeSubscription(subId) == null) return@withStorage
        SettingsManager.removeSubscriptionWithDefault(subId)
        SettingsChangeManager.makeSetupGroupTab()
        refreshGroups()
    }

    fun setEnabled(subId: String, enabled: Boolean) = withStorage("toggle group") {
        val current = MmkvManager.decodeSubscription(subId) ?: return@withStorage
        MmkvManager.encodeSubscription(subId, current.copy(enabled = enabled))
        refreshGroups()
    }

    internal fun shareQRCode(url: String) {
        dismissQRCode()
        qrCodeJob = viewModelScope.launch {
            val bitmap = withContext(Dispatchers.Default) { QRCodeDecoder.createQRCode(url) }
            if (bitmap == null) toastError(R.string.toast_failure)
            _qrCode.value = bitmap
        }
    }

    internal fun dismissQRCode() {
        qrCodeJob?.cancel()
        qrCodeJob = null
        _qrCode.value = null
    }

    fun move(fromId: String, toId: String) = withStorage("move group") {
        val ids = MmkvManager.decodeSubsList()
        if (ids.moveGuid(fromId, toId)) {
            MmkvManager.encodeSubsList(ids)
            SettingsChangeManager.makeSetupGroupTab()
            val state = _state.value
            _state.value = state.copy(groups = state.groups?.inStoredOrder(ids) ?: MmkvManager.decodeSubscriptions())
        }
    }

    fun updateSubscriptions(subId: String? = null) {
        viewModelScope.launch {
            val enabled = storage.withLock { _state.value.updateOptions }
            when {
                SubscriptionUpdateOption.Test in enabled -> updateSubscriptionsMore(subId)
                subId != null || SubscriptionUpdateOption.Update in enabled -> updateSubscriptionsOnly(subId)
            }
        }
    }

    private fun updateSubscriptionsOnly(subId: String?) {
        launchLoading {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (subId != null) {
                        MmkvManager.decodeSubscription(subId)?.let {
                            AngConfigManager.updateConfigViaSub(SubscriptionCache(subId, it))
                        } ?: SubscriptionUpdateResult()
                    } else {
                        AngConfigManager.updateConfigViaSubAll()
                    }
                }

                when {
                    result.successCount + result.failureCount + result.skipCount == 0 ->
                        toast(R.string.title_update_subscription_no_subscription)

                    result.successCount > 0 && result.failureCount + result.skipCount == 0 ->
                        toast(
                            getQuantityString(
                                R.plurals.title_update_config_count,
                                result.configCount,
                                result.configCount,
                            )
                        )

                    else ->
                        toast(getString(R.string.title_update_subscription_result, result.configCount, result.successCount, result.failureCount, result.skipCount))
                }
                if (result.configCount > 0) {
                    SettingsChangeManager.makeSetupGroupTab()
                }
                reload()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Subscription update failed", e)
                toastError(R.string.toast_failure)
            }
        }
    }

    private suspend fun updateSubscriptionsMore(subId: String?) {
        SettingsChangeManager.makeSetupGroupTab()
        val subIds = if (subId != null) {
            listOf(subId)
        } else {
            withContext(Dispatchers.IO) { MmkvManager.decodeSubscriptions() }
                .filter { it.subscription.enabled && it.subscription.url.isNotEmpty() }
                .map { it.guid }
        }

        if (subIds.isNotEmpty()) {
            MessageHelper.sendMsg2SubscriptionService(app, SubscriptionUpdateMessage(AppConfig.MSG_SUB_UPDATE_START, false, subIds))
        }

        toast(R.string.subscription_updater_job_tips)
    }
}
