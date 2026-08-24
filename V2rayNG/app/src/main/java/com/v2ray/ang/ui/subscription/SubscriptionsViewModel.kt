package com.v2ray.ang.ui.subscription

import android.app.Application
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SubscriptionsViewModel(application: Application) : BaseViewModel(application) {
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

    fun remove(subId: String) {
        launchLoading {
            val (result, persistedSubscriptions) = withContext(Dispatchers.IO) {
                val removal = SettingsManager.removeSubscriptionWithDefault(subId)
                removal to if (removal.removed) {
                    MmkvManager.decodeSubscriptions()
                } else {
                    emptyList()
                }
            }
            if (!result.removed) {
                toastError(R.string.toast_failure)
                return@launchLoading
            }

            subscriptions.clear()
            subscriptions.addAll(persistedSubscriptions)
            SettingsChangeManager.makeSetupGroupTab()
            _subsFlow.value = subscriptions.toList()
            if (!result.defaultCreated) {
                toastError(R.string.toast_failure)
            }
        }
    }

    fun update(subId: String, item: SubscriptionItem) {
        val idx = subscriptions.indexOfFirst { it.guid == subId }
        if (idx >= 0) {
            val expected = subscriptions[idx].subscription.copy()
            launchLoading {
                val saved = withContext(Dispatchers.IO) {
                    MmkvManager.updateSubscription(subId, expected, item)
                }
                if (!saved) {
                    toastError(R.string.toast_failure)
                    return@launchLoading
                }

                val currentIndex = subscriptions.indexOfFirst { it.guid == subId }
                if (currentIndex >= 0) {
                    subscriptions[currentIndex] = SubscriptionCache(subId, item)
                    _subsFlow.value = subscriptions.toList()
                }
            }
        }
    }

    fun move(fromPosition: Int, toPosition: Int) {
        val previous = subscriptions.toList()
        if (subscriptions.moveItem(fromPosition, toPosition)) {
            val saved = MmkvManager.encodeSubsList(subscriptions.mapTo(mutableListOf()) { it.guid })
            if (saved) {
                SettingsChangeManager.makeSetupGroupTab()
                _subsFlow.value = subscriptions.toList()
            } else {
                subscriptions.clear()
                subscriptions.addAll(previous)
                _subsFlow.value = subscriptions.toList()
                toastError(R.string.toast_failure)
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
