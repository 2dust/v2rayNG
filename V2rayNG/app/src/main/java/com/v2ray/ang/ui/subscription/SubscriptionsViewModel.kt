package com.v2ray.ang.ui.subscription

import android.app.Application
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
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SubscriptionsViewModel @JvmOverloads constructor(
    application: Application,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BaseViewModel(application) {
    private val _subsFlow = MutableStateFlow<List<SubscriptionCache>>(emptyList())
    val subsFlow: StateFlow<List<SubscriptionCache>> = _subsFlow.asStateFlow()
    private var orderPersistenceJob: Job? = null
    private var reloadJob: Job? = null

    init {
        reload()
    }

    fun reload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            orderPersistenceJob?.join()
            val observedOrderJob = orderPersistenceJob
            val persisted = withContext(ioDispatcher) { MmkvManager.decodeSubscriptions() }
            if (orderPersistenceJob === observedOrderJob) _subsFlow.value = persisted
        }
    }

    fun remove(subId: String) {
        launchLoading {
            val result = withContext(ioDispatcher) {
                SettingsManager.removeSubscriptionWithDefault(subId)
            }
            if (result == SettingsManager.SubscriptionRemovalResult.FAILED) {
                toastError(R.string.toast_failure)
                return@launchLoading
            }

            reload()
            SettingsChangeManager.makeSetupGroupTab()
            if (result == SettingsManager.SubscriptionRemovalResult.REMOVED_WITHOUT_DEFAULT) {
                toastError(R.string.toast_failure)
            }
        }
    }

    fun update(subId: String, item: SubscriptionItem) {
        val expected = _subsFlow.value
            .firstOrNull { it.guid == subId }
            ?.subscription
            ?.copy()
            ?: return
        launchLoading {
            val saved = withContext(ioDispatcher) {
                MmkvManager.updateSubscription(subId, expected, item)
            }
            if (!saved) toastError(R.string.toast_failure)
            reload()
        }
    }

    fun move(fromPosition: Int, toPosition: Int) {
        val subscriptions = _subsFlow.value.toMutableList()
        if (!subscriptions.moveItem(fromPosition, toPosition)) return

        _subsFlow.value = subscriptions
        val previous = orderPersistenceJob
        orderPersistenceJob = viewModelScope.launch {
            previous?.join()
            val saved = withContext(ioDispatcher) {
                MmkvManager.reorderSubscriptions(subscriptions.map { it.guid })
            }
            if (saved) SettingsChangeManager.makeSetupGroupTab() else toastError(R.string.toast_failure)
            val thisJob = currentCoroutineContext()[Job]
            if (orderPersistenceJob === thisJob) {
                orderPersistenceJob = null
                reload()
            }
        }
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
