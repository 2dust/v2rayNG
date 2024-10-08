package com.v2ray.ang.util

import android.content.Context
import android.text.TextUtils

import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.GEOIP_PRIVATE
import com.v2ray.ang.AppConfig.GEOSITE_PRIVATE
import com.v2ray.ang.AppConfig.TAG_DIRECT
import com.v2ray.ang.dto.RulesetItem
import com.v2ray.ang.dto.ServerConfig
import com.v2ray.ang.util.MmkvManager.decodeProfileConfig
import com.v2ray.ang.util.MmkvManager.decodeServerConfig
import com.v2ray.ang.util.MmkvManager.decodeServerList
import com.v2ray.ang.util.MmkvManager.settingsStorage
import com.v2ray.ang.util.Utils.parseInt
import java.util.Collections

object SettingsManager {

    fun initRoutingRulesets(context: Context) {
        val exist = MmkvManager.decodeRoutingRulesets()
        if (exist.isNullOrEmpty()) {
            val rulesetList = getPresetRoutingRulesets(context)
            MmkvManager.encodeRoutingRulesets(rulesetList)
        }
    }

    private fun getPresetRoutingRulesets(context: Context, index: Int = 0): MutableList<RulesetItem>? {
        val fileName = when (index) {
            0 -> "custom_routing_white"
            1 -> "custom_routing_black"
            2 -> "custom_routing_global"
            else -> "custom_routing_white"
        }
        val assets = Utils.readTextFromAssets(context, fileName)
        if (TextUtils.isEmpty(assets)) {
            return null
        }

        return JsonUtil.fromJson(assets, Array<RulesetItem>::class.java).toMutableList()
    }

    fun resetRoutingRulesets(context: Context, index: Int) {
        val rulesetList = getPresetRoutingRulesets(context, index) ?: return
        resetRoutingRulesetsCommon(rulesetList)
    }

    fun resetRoutingRulesetsFromClipboard(content: String?): Boolean {
        if (content.isNullOrEmpty()) {
            return false
        }

        try {
            val rulesetList = JsonUtil.fromJson(content, Array<RulesetItem>::class.java).toMutableList()
            if (rulesetList.isNullOrEmpty()) {
                return false
            }

            resetRoutingRulesetsCommon(rulesetList)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun resetRoutingRulesetsCommon(rulesetList: MutableList<RulesetItem>) {
        val rulesetNew: MutableList<RulesetItem> = mutableListOf()
        MmkvManager.decodeRoutingRulesets()?.forEach { key ->
            if (key.looked == true) {
                rulesetNew.add(key)
            }
        }

        rulesetNew.addAll(rulesetList)
        MmkvManager.encodeRoutingRulesets(rulesetNew)
    }

    fun getRoutingRuleset(index: Int): RulesetItem? {
        if (index < 0) return null

        val rulesetList = MmkvManager.decodeRoutingRulesets()
        if (rulesetList.isNullOrEmpty()) return null

        return rulesetList[index]
    }

    fun saveRoutingRuleset(index: Int, ruleset: RulesetItem?) {
        if (ruleset == null) return

        val rulesetList = MmkvManager.decodeRoutingRulesets()
        if (rulesetList.isNullOrEmpty()) return

        if (index < 0 || index >= rulesetList.count()) {
            rulesetList.add(ruleset)
        } else {
            rulesetList[index] = ruleset
        }
        MmkvManager.encodeRoutingRulesets(rulesetList)
    }

    fun removeRoutingRuleset(index: Int) {
        if (index < 0) return

        val rulesetList = MmkvManager.decodeRoutingRulesets()
        if (rulesetList.isNullOrEmpty()) return

        rulesetList.removeAt(index)
        MmkvManager.encodeRoutingRulesets(rulesetList)
    }

    fun routingRulesetsBypassLan(): Boolean {
        val rulesetItems = MmkvManager.decodeRoutingRulesets()
        val exist = rulesetItems?.filter { it.enabled && it.outboundTag == TAG_DIRECT }?.any {
            it.domain?.contains(GEOSITE_PRIVATE) == true || it.ip?.contains(GEOIP_PRIVATE) == true
        }
        return exist == true
    }

    fun swapRoutingRuleset(fromPosition: Int, toPosition: Int) {
        val rulesetList = MmkvManager.decodeRoutingRulesets()
        if (rulesetList.isNullOrEmpty()) return

        Collections.swap(rulesetList, fromPosition, toPosition)
        MmkvManager.encodeRoutingRulesets(rulesetList)
    }

    fun swapSubscriptions(fromPosition: Int, toPosition: Int) {
        val subsList = MmkvManager.decodeSubsList()
        if (subsList.isNullOrEmpty()) return

        Collections.swap(subsList, fromPosition, toPosition)
        MmkvManager.encodeSubsList(subsList)
    }

    fun getServerViaRemarks(remarks: String?): ServerConfig? {
        if (remarks == null) {
            return null
        }
        val serverList = decodeServerList()
        for (guid in serverList) {
            val profile = decodeProfileConfig(guid)
            if (profile != null && profile.remarks == remarks) {
                return decodeServerConfig(guid)
            }
        }
        return null
    }

    fun getSocksPort(): Int {
        return parseInt(settingsStorage?.decodeString(AppConfig.PREF_SOCKS_PORT), AppConfig.PORT_SOCKS.toInt())
    }

    fun getHttpPort(): Int {
        return parseInt(settingsStorage?.decodeString(AppConfig.PREF_HTTP_PORT), AppConfig.PORT_HTTP.toInt())
    }

}
