package com.dalulong.app.ui.subscription

import android.app.Application
import com.dalulong.app.R
import com.dalulong.app.dto.entities.SubscriptionCache
import com.dalulong.app.dto.entities.SubscriptionItem
import com.dalulong.app.handler.MmkvManager
import com.dalulong.app.handler.SettingsChangeManager
import com.dalulong.app.handler.SettingsManager
import com.dalulong.app.handler.SubscriptionUpdater
import com.dalulong.app.ui.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SubscriptionsViewModel(application: Application) : BaseViewModel(application) {
    private val subscriptions: MutableList<SubscriptionCache> =
        MmkvManager.decodeSubscriptions().toMutableList()

    private val _subsFlow = MutableStateFlow<List<SubscriptionCache>>(subscriptions.toList())
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

    fun swap(fromPosition: Int, toPosition: Int) {
        if (fromPosition in subscriptions.indices && toPosition in subscriptions.indices) {
            val item = subscriptions.removeAt(fromPosition)
            subscriptions.add(toPosition, item)
            SettingsManager.swapSubscriptions(fromPosition, toPosition)
            SettingsChangeManager.makeSetupGroupTab()
            _subsFlow.value = subscriptions.toList()
        }
    }

    fun updateSubscriptions() {
        SettingsChangeManager.makeSetupGroupTab()
        SubscriptionUpdater.updateAllByManual(app)

        toast(R.string.subscription_updater_job_tips)
    }
}