package com.v2ray.ang.vm

import android.app.Application
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.SubscriptionUpdateMessage
import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SubscriptionUpdateViewModel(application: Application) : BaseViewModel(application) {

    fun updateSubscriptions(
        subId: String? = null,
        onLoadingStateChanged: ((Boolean) -> Unit)? = null,
        onUpdated: (() -> Unit)? = null
    ) {
        val autoTestAfterUpdateSubscription = MmkvManager.decodeSettingsBool(
            AppConfig.PREF_AUTO_TEST_AFTER_UPDATE_SUBSCRIPTION, false
        )

        when {
            // If auto test is enabled, trigger background service for long-running task
            autoTestAfterUpdateSubscription -> updateSubscriptionsMore(subId, onUpdated)
            // Perform local update with UI loading state
            else -> updateSubscriptionsOnly(subId, onLoadingStateChanged, onUpdated)
        }
    }

    fun updateSubscriptionsOnly(
        subId: String? = null,
        onLoadingStateChanged: ((Boolean) -> Unit)? = null,
        onUpdated: (() -> Unit)? = null
    ) {
        launchLoading {
            onLoadingStateChanged?.invoke(true)
            try {
                val result = withContext(Dispatchers.IO) {
                    if (subId != null) {
                        val subCache = MmkvManager.decodeSubscriptions().find { it.guid == subId }
                        if (subCache != null) {
                            AngConfigManager.updateConfigViaSub(subCache)
                        } else {
                            SubscriptionUpdateResult()
                        }
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
                        toast(
                            getString(
                                R.string.title_update_subscription_result,
                                result.configCount,
                                result.successCount,
                                result.failureCount,
                                result.skipCount
                            )
                        )
                }
                if (result.configCount > 0) {
                    SettingsChangeManager.makeSetupGroupTab()
                }
                onUpdated?.invoke()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Subscription update failed", e)
                toastError(R.string.toast_failure)
            } finally {
                onLoadingStateChanged?.invoke(false)
            }
        }
    }

    fun updateSubscriptionsMore(subId: String? = null, onUpdated: (() -> Unit)? = null) {
        SettingsChangeManager.makeSetupGroupTab()
        val subIds = if (subId != null) {
            listOf(subId)
        } else {
            MmkvManager.decodeSubscriptions()
                .filter { it.subscription.enabled && it.subscription.url.isNotEmpty() }
                .map { it.guid }
        }

        if (subIds.isNotEmpty()) {
            MessageHelper.sendMsg2SubscriptionService(
                app,
                SubscriptionUpdateMessage(AppConfig.MSG_SUB_UPDATE_START, false, subIds)
            )
        }

        toast(R.string.subscription_updater_job_tips)
        onUpdated?.invoke()
    }
}
