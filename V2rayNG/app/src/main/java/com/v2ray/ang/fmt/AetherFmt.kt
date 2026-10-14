package com.v2ray.ang.fmt

import com.google.gson.Gson
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.AetherSettings
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import kotlin.io.encoding.Base64

/** Versioned profile URI. Native WARP keys and device identity are never exported. */
object AetherFmt {
    private data class SharedProfile(val version: Int = 1, val remarks: String = "", val settings: AetherSettings = AetherSettings())
    private val gson = Gson()

    fun toUri(profile: ProfileItem): String {
        require(profile.configType == EConfigType.AETHER)
        val settings = profile.aetherSettings ?: AetherSettings()
        require(settings.isValid())
        val json = gson.toJson(SharedProfile(remarks = profile.remarks, settings = settings))
        return Base64.UrlSafe.encode(json.toByteArray(Charsets.UTF_8))
    }

    fun parse(uri: String): ProfileItem? = runCatching {
        require(uri.startsWith(EConfigType.AETHER.protocolScheme))
        val encoded = uri.removePrefix(EConfigType.AETHER.protocolScheme)
        require(encoded.length in 1..16384)
        val json = String(Base64.UrlSafe.decode(encoded), Charsets.UTF_8)
        val shared = gson.fromJson(json, SharedProfile::class.java)
        require(shared.version == 1 && shared.settings.isValid() && shared.remarks.isNotBlank())
        ProfileItem.create(EConfigType.AETHER).copy(
            remarks = shared.remarks, server = "127.0.0.1", serverPort = AppConfig.PORT_SOCKS,
            aetherSettings = shared.settings,
        )
    }.getOrNull()
}
