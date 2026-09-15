package com.v2ray.ang.ui.main

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.fmt.CustomFmt
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.util.LogUtil

internal data class ServerRowUiModel(
    val guid: String,
    val profile: ProfileItem,
    val remarks: String,
    val statistics: String,
    val typeDescription: String,
    val testDelayMillis: Long,
    val subscriptionBadge: String,
)

internal data class ServerGroupUiState(
    val servers: List<ServersCache> = emptyList(),
    val rows: List<ServerRowUiModel> = emptyList(),
)

internal fun ServersCache.withCustomMetadata(raw: String?): ServersCache {
    if (profile.configType != EConfigType.CUSTOM) return this
    val metadata = try {
        raw?.let(CustomFmt::parseMetadata)
    } catch (error: Exception) {
        LogUtil.e(AppConfig.TAG, "Failed to read custom server metadata for $guid", error)
        null
    }
    // Re-read raw JSON so profiles saved by older versions also get accurate metadata.
    val refreshedProfile = profile.copy(server = metadata?.server, serverPort = metadata?.serverPort).apply {
        description = AngConfigManager.generateDescription(this)
    }
    return copy(
        profile = refreshedProfile,
        customServerCount = metadata?.serverCount ?: 0,
    )
}

internal fun buildServerRowUiModel(
    server: ServersCache,
    subscriptionRemarks: String,
    multipleServersText: String,
): ServerRowUiModel {
    val profile = server.profile
    return ServerRowUiModel(
        guid = server.guid,
        profile = profile,
        remarks = profile.remarks,
        statistics = if (profile.configType == EConfigType.CUSTOM && server.customServerCount > 1) {
            multipleServersText
        } else {
            profile.description.nullIfBlank() ?: AngConfigManager.generateDescription(profile)
        },
        typeDescription = serverProtocolDescription(profile),
        testDelayMillis = server.testDelayMillis,
        subscriptionBadge = subscriptionRemarks.firstOrNull()?.toString().orEmpty(),
    )
}

private fun serverProtocolDescription(profile: ProfileItem): String {
    if (profile.configType.isComplexType()) return profile.configType.name
    val parts = mutableListOf(profile.configType.name)
    profile.network?.let { network ->
        if (network.isNotBlank() && !network.equals("tcp", ignoreCase = true)) {
            parts.add(network)
        }
    }
    profile.security?.let { security ->
        if (security.isNotBlank()) {
            parts.add(
                if (profile.insecure == true && security.equals("tls", ignoreCase = true)) {
                    "$security insecure"
                } else {
                    security
                }
            )
        }
    }
    return parts.joinToString(" / ")
}
