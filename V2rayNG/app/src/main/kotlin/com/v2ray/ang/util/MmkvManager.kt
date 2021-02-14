package com.v2ray.ang.util

import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import com.v2ray.ang.dto.ServerAffiliationInfo
import com.v2ray.ang.dto.ServerConfig

object MmkvManager {
    const val ID_MAIN = "MAIN"
    const val ID_SERVER_CONFIG = "SERVER_CONFIG"
    const val ID_SERVER_AFF = "SERVER_AFF"
    const val ID_SUB = "SUB"
    const val ID_SETTING = "SETTING"
    const val KEY_SELECTED_SERVER = "SELECTED_SERVER"
    const val KEY_ANG_CONFIGS = "ANG_CONFIGS"

    private val mainStorage by lazy { MMKV.mmkvWithID(ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val serverStorage by lazy { MMKV.mmkvWithID(ID_SERVER_CONFIG, MMKV.MULTI_PROCESS_MODE) }
    private val serverAffStorage by lazy { MMKV.mmkvWithID(ID_SERVER_AFF, MMKV.MULTI_PROCESS_MODE) }

    fun decodeServerConfig(guid: String): ServerConfig? {
        if (guid.isBlank()) {
            return null
        }
        val json = serverStorage?.decodeString(guid)
        if (json.isNullOrBlank()) {
            return null
        }
        return Gson().fromJson(json, ServerConfig::class.java)
    }

    fun encodeServerConfig(guid: String, config: ServerConfig): String {
        val key = if (guid.isBlank()) {
            Utils.getUuid()
        } else {
            guid
        }
        serverStorage?.encode(key, Gson().toJson(config))
        val serverList= Gson().fromJson(mainStorage?.decodeString(KEY_ANG_CONFIGS), Array<String>::class.java).toMutableList()
        serverList.add(guid)
        mainStorage?.encode(KEY_ANG_CONFIGS, Gson().toJson(serverList))
        if (mainStorage?.decodeString(KEY_SELECTED_SERVER).isNullOrBlank()) {
            mainStorage?.encode(KEY_SELECTED_SERVER, guid)
        }
        return key
    }

    fun removeServer(guid: String) {
        if (guid.isBlank()) {
            return
        }
        if (mainStorage?.decodeString(KEY_SELECTED_SERVER) == guid) {
            mainStorage?.remove(KEY_SELECTED_SERVER)
        }
        val serverList= Gson().fromJson(mainStorage?.decodeString(KEY_ANG_CONFIGS), Array<String>::class.java).toMutableList()
        serverList.remove(guid)
        mainStorage?.encode(KEY_ANG_CONFIGS, Gson().toJson(serverList))
        serverStorage?.remove(guid)
        serverAffStorage?.remove(guid)
    }

    fun removeServerViaSubid(subid: String) {
        if (subid.isBlank()) {
            return
        }
        serverStorage?.allKeys()?.forEach { key ->
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
        val json = serverAffStorage?.decodeString(guid)
        if (json.isNullOrBlank()) {
            return null
        }
        return Gson().fromJson(json, ServerAffiliationInfo::class.java)
    }

    fun clearAllTestDelayResults() {
        serverAffStorage?.allKeys()?.forEach { key ->
            decodeServerAffiliationInfo(key)?.let { aff ->
                aff.testDelayMillis = 0
                serverAffStorage?.encode(key, Gson().toJson(aff))
            }
        }
    }
}
