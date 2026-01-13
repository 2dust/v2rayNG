package com.v2ray.ang.viewmodel

import androidx.lifecycle.ViewModel
import com.v2ray.ang.dto.SubscriptionItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager

class SubscriptionsViewModel : ViewModel() {
    private val subscriptions: MutableList<Pair<String, SubscriptionItem>> =
        MmkvManager.decodeSubscriptions().toMutableList()

    fun getAll(): List<Pair<String, SubscriptionItem>> = subscriptions.toList()

    fun reload() {
        subscriptions.clear()
        subscriptions.addAll(MmkvManager.decodeSubscriptions())
    }

    fun remove(subId: String): Boolean {
        val changed = subscriptions.removeAll { it.first == subId }
        if (changed) {
            MmkvManager.removeSubscription(subId)
            SettingsChangeManager.makeSetupGroupTab()
        }
        return changed
    }

    fun update(subId: String, item: SubscriptionItem) {
        val idx = subscriptions.indexOfFirst { it.first == subId }
        if (idx >= 0) {
            subscriptions[idx] = Pair(subId, item)
            MmkvManager.encodeSubscription(subId, item)
        }
    }

    fun swap(fromPosition: Int, toPosition: Int) {
        if (fromPosition in subscriptions.indices && toPosition in subscriptions.indices) {
            val item = subscriptions.removeAt(fromPosition)
            subscriptions.add(toPosition, item)
            SettingsManager.swapSubscriptions(fromPosition, toPosition)
            SettingsChangeManager.makeSetupGroupTab()
        }
    }
}

