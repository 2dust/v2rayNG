package com.v2ray.ang.viewmodel

import androidx.lifecycle.ViewModel
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SubscriptionsViewModel : ViewModel() {
    private val subscriptions: MutableList<SubscriptionCache> =
        MmkvManager.decodeSubscriptions().toMutableList()

    private val _subscriptionsFlow = MutableStateFlow<List<SubscriptionCache>>(emptyList())
    val subscriptionsFlow = _subscriptionsFlow.asStateFlow()

    init {
        _subscriptionsFlow.value = subscriptions.toList()
    }

    @Synchronized
    fun getAll(): List<SubscriptionCache> = subscriptions.toList()

    @Synchronized
    fun reload() {
        subscriptions.clear()
        subscriptions.addAll(MmkvManager.decodeSubscriptions())
        _subscriptionsFlow.value = subscriptions.toList()
    }

    @Synchronized
    fun remove(subId: String): Boolean {
        val changed = subscriptions.removeAll { it.guid == subId }
        if (changed) {
            SettingsManager.removeSubscriptionWithDefault(subId)
            SettingsChangeManager.makeSetupGroupTab()
            _subscriptionsFlow.value = subscriptions.toList()
        }
        return changed
    }

    @Synchronized
    fun update(subId: String, item: SubscriptionItem) {
        val idx = subscriptions.indexOfFirst { it.guid == subId }
        if (idx >= 0) {
            subscriptions[idx] = SubscriptionCache(subId, item)
            MmkvManager.encodeSubscription(subId, item)
            _subscriptionsFlow.value = subscriptions.toList()
        }
    }

    @Synchronized
    fun swap(fromPosition: Int, toPosition: Int) {
        if (fromPosition in subscriptions.indices && toPosition in subscriptions.indices) {
            val item = subscriptions.removeAt(fromPosition)
            subscriptions.add(toPosition, item)
            SettingsManager.swapSubscriptions(fromPosition, toPosition)
            SettingsChangeManager.makeSetupGroupTab()
            _subscriptionsFlow.value = subscriptions.toList()
        }
    }
}

