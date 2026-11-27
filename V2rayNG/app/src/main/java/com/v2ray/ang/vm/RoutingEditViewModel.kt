package com.v2ray.ang.vm

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.handler.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class RoutingEditViewModel(application: Application) : BaseViewModel(application) {

    sealed interface RoutingEditViewModelEvent : ViewModelEvent {
        object SaveSuccess : RoutingEditViewModelEvent
        object DeleteSuccess : RoutingEditViewModelEvent
    }

    fun getRoutingRuleset(position: Int): RulesetItem? {
        return SettingsManager.getRoutingRuleset(position)
    }

    fun getOutboundSuggestions(): List<String> {
        return SettingsManager.getProfileRemarks()
    }

    fun canUseProcessRouting(): Boolean {
        return SettingsManager.canUseProcessRouting()
    }

    fun saveRuleset(position: Int, rulesetItem: RulesetItem) {
        if (rulesetItem.remarks.isNullOrEmpty()) {
            return
        }
        if (position < 0 && rulesetItem.id.isEmpty()) {
            rulesetItem.id = UUID.randomUUID().toString()
        }
        SettingsManager.saveRoutingRuleset(position, rulesetItem)
        toastSuccess(R.string.toast_success)
        viewModelScope.launch {
            _viewModelEvent.send(RoutingEditViewModelEvent.SaveSuccess)
        }
    }

    fun deleteRuleset(position: Int) {
        if (position >= 0) {
            viewModelScope.launch(Dispatchers.IO) {
                SettingsManager.removeRoutingRuleset(position)
                _viewModelEvent.send(RoutingEditViewModelEvent.DeleteSuccess)
            }
        }
    }
}
