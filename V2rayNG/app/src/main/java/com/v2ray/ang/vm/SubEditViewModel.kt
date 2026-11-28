package com.v2ray.ang.vm

import android.app.Application
import android.text.TextUtils
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SubEditViewModel(application: Application) : BaseViewModel(application) {

    sealed interface SubEditViewModelEvent : ViewModelEvent {
        object SaveSuccess : SubEditViewModelEvent
        object DeleteSuccess : SubEditViewModelEvent
    }

    fun getProfileSuggestions(): List<String> {
        return SettingsManager.getProfileRemarks(
            excludeConfigTypes = setOf(
                EConfigType.CUSTOM,
                EConfigType.CUSTOM_OUTBOUND,
                EConfigType.POLICYGROUP,
                EConfigType.PROXYCHAIN,
            )
        )
    }

    fun loadSubscription(subId: String): SubscriptionItem {
        return MmkvManager.decodeSubscription(subId) ?: SubscriptionItem()
    }

    fun saveSubscription(editSubId: String, subItem: SubscriptionItem) {
        if (TextUtils.isEmpty(subItem.remarks)) {
            return
        }
        if (subItem.url.isNotEmpty()) {
            if (!Utils.isValidUrl(subItem.url) || (!Utils.isValidSubUrl(subItem.url) && !subItem.allowInsecureUrl)) {
                return
            }
        }
        if (subItem.autoUpdate && subItem.updateInterval < AppConfig.SUBSCRIPTION_MIN_INTERVAL_MINUTES) {
            return
        }

        MmkvManager.encodeSubscription(editSubId, subItem)
        SubscriptionUpdater.syncOne(subId = editSubId)
        SettingsChangeManager.makeSetupGroupTab()
        toastSuccess(R.string.toast_success)
        viewModelScope.launch {
            _viewModelEvent.send(SubEditViewModelEvent.SaveSuccess)
        }
    }

    fun deleteSubscription(editSubId: String) {
        if (editSubId.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                SettingsManager.removeSubscriptionWithDefault(editSubId)
                SettingsChangeManager.makeSetupGroupTab()
                _viewModelEvent.send(SubEditViewModelEvent.DeleteSuccess)
            }
        }
    }
}
