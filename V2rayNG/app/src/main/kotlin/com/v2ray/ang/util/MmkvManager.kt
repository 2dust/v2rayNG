package com.v2ray.ang.util

import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig.PREF_ROUTING_RULESET
import com.v2ray.ang.dto.AssetUrlItem
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.dto.RulesetItem
import com.v2ray.ang.dto.ServerAffiliationInfo
import com.v2ray.ang.dto.ServerConfig
import com.v2ray.ang.dto.SubscriptionItem

object MmkvManager {

    //region private

    private const val ID_MAIN = "MAIN"
    private const val ID_SERVER_CONFIG = "SERVER_CONFIG"
    private const val ID_PROFILE_CONFIG = "PROFILE_CONFIG"
    private const val ID_SERVER_RAW = "SERVER_RAW"
    private const val ID_SERVER_AFF = "SERVER_AFF"
    private const val ID_SUB = "SUB"
    private const val ID_ASSET = "ASSET"
    private const val ID_SETTING = "SETTING"
    private const val KEY_SELECTED_SERVER = "SELECTED_SERVER"
    private const val KEY_ANG_CONFIGS = "ANG_CONFIGS"
    private const val KEY_SUB_IDS = "SUB_IDS"

    private val mainStorage by lazy { MMKV.mmkvWithID(ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    val settingsStorage by lazy { MMKV.mmkvWithID(ID_SETTING, MMKV.MULTI_PROCESS_MODE) }
    private val serverStorage by lazy { MMKV.mmkvWithID(ID_SERVER_CONFIG, MMKV.MULTI_PROCESS_MODE) }
    private val profileStorage by lazy { MMKV.mmkvWithID(ID_PROFILE_CONFIG, MMKV.MULTI_PROCESS_MODE) }
    private val serverAffStorage by lazy { MMKV.mmkvWithID(ID_SERVER_AFF, MMKV.MULTI_PROCESS_MODE) }
    private val subStorage by lazy { MMKV.mmkvWithID(ID_SUB, MMKV.MULTI_PROCESS_MODE) }
    private val assetStorage by lazy { MMKV.mmkvWithID(ID_ASSET, MMKV.MULTI_PROCESS_MODE) }
    private val serverRawStorage by lazy { MMKV.mmkvWithID(ID_SERVER_RAW, MMKV.MULTI_PROCESS_MODE) }

    //endregion

    //region Server

    fun getSelectServer(): String? {
        return mainStorage.decodeString(KEY_SELECTED_SERVER)
    }

    fun setSelectServer(guid: String) {
        mainStorage.encode(KEY_SELECTED_SERVER, guid)
    }

    fun encodeServerList(serverList: MutableList<String>) {
        mainStorage.encode(KEY_ANG_CONFIGS, Gson().toJson(serverList))
    }

    fun decodeServerList(): MutableList<String> {
        val json = mainStorage.decodeString(KEY_ANG_CONFIGS)
        return if (json.isNullOrBlank()) {
            mutableListOf()
        } else {
            Gson().fromJson(json, Array<String>::class.java).toMutableList()
        }
    }

    fun decodeServerConfig(guid: String): ServerConfig? {
        if (guid.isBlank()) {
            return null
        }
        val json = serverStorage.decodeString(guid)
        if (json.isNullOrBlank()) {
            return null
        }
        return Gson().fromJson(json, ServerConfig::class.java)
    }

    fun decodeProfileConfig(guid: String): ProfileItem? {
        if (guid.isBlank()) {
            return null
        }
        val json = profileStorage.decodeString(guid)
        if (json.isNullOrBlank()) {
            return null
        }
        return Gson().fromJson(json, ProfileItem::class.java)
    }

    fun encodeServerConfig(guid: String, config: ServerConfig): String {
        val key = guid.ifBlank { Utils.getUuid() }
        serverStorage.encode(key, Gson().toJson(config))
        val serverList = decodeServerList()
        if (!serverList.contains(key)) {
            serverList.add(0, key)
            encodeServerList(serverList)
            if (getSelectServer().isNullOrBlank()) {
                mainStorage.encode(KEY_SELECTED_SERVER, key)
            }
        }
        val profile = ProfileItem(
            configType = config.configType,
            subscriptionId = config.subscriptionId,
            remarks = config.remarks,
            server = config.getProxyOutbound()?.getServerAddress(),
            serverPort = config.getProxyOutbound()?.getServerPort(),
        )
        profileStorage.encode(key, Gson().toJson(profile))
        return key
    }

    fun removeServer(guid: String) {
        if (guid.isBlank()) {
            return
        }
        if (getSelectServer() == guid) {
            mainStorage.remove(KEY_SELECTED_SERVER)
        }
        val serverList = decodeServerList()
        serverList.remove(guid)
        encodeServerList(serverList)
        serverStorage.remove(guid)
        profileStorage.remove(guid)
        serverAffStorage.remove(guid)
    }

    fun removeServerViaSubid(subid: String) {
        if (subid.isBlank()) {
            return
        }
        serverStorage.allKeys()?.forEach { key ->
            decodeServerConfig(key)?.let { config ->
                if (config.subscriptionId == subid) {
                    removeServer(key)
                }
            }
        }
    }

    fun decodeServerAffiliationInfo(guid: String): ServerAffiliationInfo? {
        if (guid.isBlank()) {
            return null
        }
        val json = serverAffStorage.decodeString(guid)
        if (json.isNullOrBlank()) {
            return null
        }
        return Gson().fromJson(json, ServerAffiliationInfo::class.java)
    }

    fun encodeServerTestDelayMillis(guid: String, testResult: Long) {
        if (guid.isBlank()) {
            return
        }
        val aff = decodeServerAffiliationInfo(guid) ?: ServerAffiliationInfo()
        aff.testDelayMillis = testResult
        serverAffStorage.encode(guid, Gson().toJson(aff))
    }

    fun clearAllTestDelayResults(keys: List<String>?) {
        keys?.forEach { key ->
            decodeServerAffiliationInfo(key)?.let { aff ->
                aff.testDelayMillis = 0
                serverAffStorage.encode(key, Gson().toJson(aff))
            }
        }
    }

    fun removeAllServer() {
        mainStorage.clearAll()
        serverStorage.clearAll()
        profileStorage.clearAll()
        serverAffStorage.clearAll()
    }

    fun removeInvalidServer(guid: String) {
        if (guid.isNotEmpty()) {
            decodeServerAffiliationInfo(guid)?.let { aff ->
                if (aff.testDelayMillis < 0L) {
                    removeServer(guid)
                }
            }
        } else {
            serverAffStorage.allKeys()?.forEach { key ->
                decodeServerAffiliationInfo(key)?.let { aff ->
                    if (aff.testDelayMillis < 0L) {
                        removeServer(key)
                    }
                }
            }
        }
    }

    fun encodeServerRaw(guid: String, config: String) {
        serverRawStorage.encode(guid, config)
    }

    fun decodeServerRaw(guid: String): String? {
        return serverRawStorage.decodeString(guid) ?: return null
    }

    //endregion

    //region Subscriptions

    private fun initSubsList() {
        val subsList = decodeSubsList()
        if (subsList.isNotEmpty()) {
            return
        }
        subStorage.allKeys()?.forEach { key ->
            subsList.add(key)
        }
        encodeSubsList(subsList)
    }

    fun decodeSubscriptions(): List<Pair<String, SubscriptionItem>> {
        initSubsList()

        val subscriptions = mutableListOf<Pair<String, SubscriptionItem>>()
        decodeSubsList().forEach { key ->
            val json = subStorage.decodeString(key)
            if (!json.isNullOrBlank()) {
                subscriptions.add(Pair(key, Gson().fromJson(json, SubscriptionItem::class.java)))
            }
        }
        return subscriptions
    }

    fun removeSubscription(subid: String) {
        subStorage.remove(subid)
        val subsList = decodeSubsList()
        subsList.remove(subid)
        encodeSubsList(subsList)

        removeServerViaSubid(subid)
    }

    fun encodeSubscription(guid: String, subItem: SubscriptionItem) {
        val key = guid.ifBlank { Utils.getUuid() }
        subStorage.encode(key, Gson().toJson(subItem))

        val subsList = decodeSubsList()
        if (!subsList.contains(key)) {
            subsList.add(key)
            encodeSubsList(subsList)
        }
    }

    fun decodeSubscription(subscriptionId: String): SubscriptionItem? {
        val json = subStorage.decodeString(subscriptionId) ?: return null
        return Gson().fromJson(json, SubscriptionItem::class.java)
    }

    fun encodeSubsList(subsList: MutableList<String>) {
        mainStorage.encode(KEY_SUB_IDS, Gson().toJson(subsList))
    }

    fun decodeSubsList(): MutableList<String> {
        val json = mainStorage.decodeString(KEY_SUB_IDS)
        return if (json.isNullOrBlank()) {
            mutableListOf()
        } else {
            Gson().fromJson(json, Array<String>::class.java).toMutableList()
        }
    }

    //endregion

    //region Asset

    fun decodeAssetUrls(): List<Pair<String, AssetUrlItem>> {
        val assetUrlItems = mutableListOf<Pair<String, AssetUrlItem>>()
        assetStorage.allKeys()?.forEach { key ->
            val json = assetStorage.decodeString(key)
            if (!json.isNullOrBlank()) {
                assetUrlItems.add(Pair(key, Gson().fromJson(json, AssetUrlItem::class.java)))
            }
        }
        return assetUrlItems.sortedBy { (_, value) -> value.addedTime }
    }

    fun removeAssetUrl(assetid: String) {
        assetStorage.remove(assetid)
    }

    fun encodeAsset(assetid: String, assetItem: AssetUrlItem) {
        val key = assetid.ifBlank { Utils.getUuid() }
        assetStorage.encode(key, Gson().toJson(assetItem))
    }

    fun decodeAsset(assetid: String): AssetUrlItem? {
        val json = assetStorage.decodeString(assetid) ?: return null
        return Gson().fromJson(json, AssetUrlItem::class.java)
    }

    //endregion

    //region Routing

    fun decodeRoutingRulesets(): MutableList<RulesetItem>? {
        val ruleset = settingsStorage.decodeString(PREF_ROUTING_RULESET)
        if (ruleset.isNullOrEmpty()) return null
        return Gson().fromJson(ruleset, Array<RulesetItem>::class.java).toMutableList()
    }

    fun encodeRoutingRulesets(rulesetList: MutableList<RulesetItem>?) {
        if (rulesetList.isNullOrEmpty())
            settingsStorage.encode(PREF_ROUTING_RULESET, "")
        else
            settingsStorage.encode(PREF_ROUTING_RULESET, Gson().toJson(rulesetList))
    }

    //endregion
}
