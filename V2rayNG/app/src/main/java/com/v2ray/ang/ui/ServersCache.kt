package com.v2ray.ang.ui

import android.content.Context
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.util.AngConfigManager
import com.v2ray.ang.util.MmkvManager

/**
 * Galaxy Tunnel UI - Servers Cache Wrapper
 * Wraps v2rayNG's existing server management for the Galaxy Tunnel UI
 */
class ServersCache private constructor() {

    companion object {
        @Volatile
        private var instance: ServersCache? = null

        fun getInstance(): ServersCache {
            return instance ?: synchronized(this) {
                instance ?: ServersCache().also { instance = it }
            }
        }
    }

    data class ServerItem(
        val guid: String,
        val configType: EConfigType,
        val remarks: String,
        val serverAddress: String,
        val serverPort: Int,
        val isActive: Boolean = false
    )

    fun getAllServers(): List<ServerItem> {
        val serverList = MmkvManager.decodeServerList()
        return serverList.mapNotNull { guid ->
            val profile = MmkvManager.decodeServerConfig(guid)
            profile?.let {
                ServerItem(
                    guid = guid,
                    configType = EConfigType.fromInt(it.configType) ?: EConfigType.VMESS,
                    remarks = it.remarks ?: "Unknown",
                    serverAddress = it.serverAddress ?: "",
                    serverPort = it.serverPort ?: 0,
                    isActive = guid == getActiveServerGuid()
                )
            }
        }
    }

    fun getActiveServerGuid(): String? {
        return MmkvManager.decodeSettingsString("activeServerGuid")
    }

    fun setActiveServer(guid: String) {
        MmkvManager.encodeSettings("activeServerGuid", guid)
    }

    fun getActiveServer(): ServerItem? {
        val guid = getActiveServerGuid() ?: return null
        val profile = MmkvManager.decodeServerConfig(guid) ?: return null
        return ServerItem(
            guid = guid,
            configType = EConfigType.fromInt(profile.configType) ?: EConfigType.VMESS,
            remarks = profile.remarks ?: "Unknown",
            serverAddress = profile.serverAddress ?: "",
            serverPort = profile.serverPort ?: 0,
            isActive = true
        )
    }

    fun importConfig(config: String): Boolean {
        return AngConfigManager.importBatchConfig(config, "" , true) > 0
    }

    fun removeServer(guid: String) {
        MmkvManager.removeServer(guid)
    }

    fun getServerCount(): Int {
        return MmkvManager.decodeServerList().size
    }
}
