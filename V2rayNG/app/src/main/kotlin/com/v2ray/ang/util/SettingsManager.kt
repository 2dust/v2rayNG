package com.v2ray.ang.util

import android.content.Context
import android.text.TextUtils
import android.util.Log
import com.google.gson.Gson
import com.v2ray.ang.dto.RulesetItem

object SettingsManager {

    fun initRoutingRulesets(context: Context) {
        Log.d("=====", "initRoutingRuleset")
        val exist = MmkvManager.decodeRoutingRulesets()

        if (exist.isNullOrEmpty()) {
            Log.d("=====", "isNullOrEmpty")
            val assets = Utils.readTextFromAssets(context, "custom_routing_white")
            if (TextUtils.isEmpty(assets)) {
                return
            }

            val rulesetList = Gson().fromJson(assets, Array<RulesetItem>::class.java).toMutableList()
            Log.d("=====", "rulesetList==" + rulesetList.count())
            MmkvManager.encodeRoutingRulesets(rulesetList)
        }
    }

    fun resetRoutingRulesets(context: Context) {
        MmkvManager.encodeRoutingRulesets(null)
        initRoutingRulesets(context)
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
        val exist = rulesetItems?.any { it.enabled && it.domain?.contains(":private") == true }
        return exist == true
    }


}
