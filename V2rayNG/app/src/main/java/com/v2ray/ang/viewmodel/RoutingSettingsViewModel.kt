package com.v2ray.ang.viewmodel

import androidx.lifecycle.ViewModel
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.handler.SettingsManager

class RoutingSettingsViewModel : ViewModel() {
    private val rulesets: MutableList<RulesetItem> = mutableListOf()
    private var profileId: String = ""

    fun getAll(): List<RulesetItem> = rulesets.toList()

    fun setProfileId(id: String) {
        profileId = id
    }

    fun getProfileId(): String = profileId

    fun reload() {
        rulesets.clear()
        val profile = SettingsManager.getRoutingProfile(profileId)
            ?: SettingsManager.getActiveRoutingProfile()
        rulesets.addAll(profile?.rulesets ?: mutableListOf())
    }

    fun update(position: Int, item: RulesetItem) {
        if (position in rulesets.indices) {
            rulesets[position] = item
            SettingsManager.saveRoutingRulesetForProfile(profileId, position, item)
        }
    }

    fun swap(fromPosition: Int, toPosition: Int) {
        if (fromPosition in rulesets.indices && toPosition in rulesets.indices) {
            SettingsManager.swapRoutingRulesetForProfile(profileId, fromPosition, toPosition)
        }
    }
}
