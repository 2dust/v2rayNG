package com.v2ray.ang.ui.subscription

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.SubscriptionUpdateMessage
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.ui.compose.ReorderCommand
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.QRCodeDecoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun subscriptionAutoUpdateChange(
    current: SubscriptionItem?,
    enabled: Boolean,
): SubscriptionItem? = current
    ?.takeIf { it.url.isNotEmpty() && it.autoUpdate != enabled }
    ?.copy(autoUpdate = enabled)

class SubscriptionsViewModel(application: Application) : BaseViewModel(application) {
    private val pendingAutoUpdates = mutableSetOf<String>()
    // Ephemeral acknowledgements: completion must not wait for a screen to resume or replay
    // an old result after an edit. Durable subscription state is exposed by subsFlow.
    private val _autoUpdateChanges = MutableSharedFlow<Boolean>()
    internal val autoUpdateChanges = _autoUpdateChanges.asSharedFlow()

    private var qrCodeJob: Job? = null
    private val _qrCode = MutableStateFlow<Bitmap?>(null)
    internal val qrCode = _qrCode.asStateFlow()
    private val subscriptions: MutableList<SubscriptionCache> =
        MmkvManager.decodeSubscriptions().toMutableList()

    private val _subsFlow = MutableStateFlow(subscriptions.toList())
    val subsFlow: StateFlow<List<SubscriptionCache>> = _subsFlow.asStateFlow()

    fun getAll(): List<SubscriptionCache> = subscriptions.toList()

    fun reload() {
        subscriptions.clear()
        subscriptions.addAll(MmkvManager.decodeSubscriptions())
        _subsFlow.value = subscriptions.toList()
    }

    fun remove(subId: String): Boolean {
        val changed = subscriptions.removeAll { it.guid == subId }
        if (changed) {
            SettingsManager.removeSubscriptionWithDefault(subId)
            SettingsChangeManager.makeSetupGroupTab()
        }
        _subsFlow.value = subscriptions.toList()
        return changed
    }

    fun update(subId: String, item: SubscriptionItem) {
        val idx = subscriptions.indexOfFirst { it.guid == subId }
        if (idx >= 0) {
            subscriptions[idx] = SubscriptionCache(subId, item)
            MmkvManager.encodeSubscription(subId, item)
        }
        _subsFlow.value = subscriptions.toList()
    }

    internal fun setAutoUpdate(subId: String, enabled: Boolean): Boolean {
        if (!pendingAutoUpdates.add(subId)) return false
        viewModelScope.launch {
            try {
                val current = withContext(Dispatchers.IO) {
                    MmkvManager.decodeSubscription(subId)
                }
                val updated = subscriptionAutoUpdateChange(current, enabled) ?: return@launch
                if (enabled && updated.updateInterval < AppConfig.SUBSCRIPTION_MIN_INTERVAL_MINUTES) {
                    toastError(R.string.toast_invalid_update_interval)
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    MmkvManager.encodeSubscription(subId, updated)
                    SubscriptionUpdater.syncOne(subId = subId)
                }
                val index = subscriptions.indexOfFirst { it.guid == subId }
                if (index >= 0) {
                    subscriptions[index] = SubscriptionCache(subId, updated)
                    _subsFlow.value = subscriptions.toList()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Subscription periodic update change failed: $subId", e)
                toastError(R.string.toast_failure)
                return@launch
            } finally {
                pendingAutoUpdates.remove(subId)
            }
            _autoUpdateChanges.emit(enabled)
        }
        return true
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

    fun move(fromPosition: Int, toPosition: Int): Boolean {
        if (subscriptions.moveItem(fromPosition, toPosition)) {
            MmkvManager.encodeSubsList(subscriptions.mapTo(mutableListOf()) { it.guid })
            SettingsChangeManager.makeSetupGroupTab()
            _subsFlow.value = subscriptions.toList()
            return true
        }
        return false
    }

    internal fun move(subId: String, command: ReorderCommand): Boolean {
        val fromPosition = subscriptions.indexOfFirst { it.guid == subId }
        val toPosition = command.targetIndex(fromPosition, subscriptions.size) ?: return false
        return move(fromPosition, toPosition)
    }

    fun updateSubscriptions() {
        val updateSubscription = MmkvManager.decodeSettingsBool(AppConfig.PREF_UPDATE_SUBSCRIPTION, false)
        val autoTestAfterUpdateSubscription = MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_TEST_AFTER_UPDATE_SUBSCRIPTION, false)

        when {
            // If auto test is enabled, trigger background service for long-running task
            autoTestAfterUpdateSubscription -> updateSubscriptionsMore()
            // If only update is enabled, perform local update with UI loading state
            updateSubscription -> updateSubscriptionsOnly()
        }
    }

    fun updateSubscriptionsOnly() {
        launchLoading {
            try {
                val result = withContext(Dispatchers.IO) {
                    AngConfigManager.updateConfigViaSubAll()
                }

                when {
                    result.successCount + result.failureCount + result.skipCount == 0 ->
                        toast(R.string.title_update_subscription_no_subscription)

                    result.successCount > 0 && result.failureCount + result.skipCount == 0 ->
                        toast(getString(R.string.title_update_config_count, result.configCount))

                    else ->
                        toast(getString(R.string.title_update_subscription_result, result.configCount, result.successCount, result.failureCount, result.skipCount))
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

    fun updateSubscriptionsMore() {
        SettingsChangeManager.makeSetupGroupTab()
        val subIds = MmkvManager.decodeSubscriptions()
            .filter { it.subscription.enabled && it.subscription.url.isNotEmpty() }
            .map { it.guid }

        if (subIds.isNotEmpty()) {
            MessageHelper.sendMsg2SubscriptionService(app, SubscriptionUpdateMessage(AppConfig.MSG_SUB_UPDATE_START, false, subIds))
        }

        toast(R.string.subscription_updater_job_tips)
    }
}
