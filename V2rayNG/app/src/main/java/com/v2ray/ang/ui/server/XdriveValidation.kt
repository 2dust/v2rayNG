package com.v2ray.ang.ui.server

import androidx.annotation.StringRes
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreOutboundBuilder
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.NetworkType
import com.v2ray.ang.extension.nullIfBlank

/**
 * Mirrors the Xray-core checks for the xdrive transport (VLESS with encryption, no flow,
 * service-specific secrets) so the editor rejects a profile the core would refuse to start.
 * Returns the message to show, or null when the profile is valid or not xdrive.
 */
@StringRes
internal fun xdriveValidationError(config: ProfileItem): Int? {
    if (config.network != NetworkType.XDRIVE.type) return null

    val encryption = config.method.orEmpty().trim()
    if (encryption.isEmpty() || encryption == "none") return R.string.toast_xdrive_encryption
    if (!config.flow.isNullOrBlank()) return R.string.toast_xdrive_flow
    if (config.path.isNullOrBlank()) return R.string.server_lab_xdrive_folder

    val extraText = config.xdriveExtra.nullIfBlank()
    val extra = if (extraText == null) null else parseJsonObject(extraText) ?: return R.string.server_lab_xdrive_extra

    val secretCount = CoreOutboundBuilder.parseXdriveSecrets(config.xdriveSecrets).size
    return when (config.xdriveService) {
        AppConfig.XDRIVE_SERVICE_GOOGLE_DRIVE -> if (secretCount != 3) R.string.toast_xdrive_secrets else null
        AppConfig.XDRIVE_SERVICE_YANDEX_DISK -> if (secretCount != 2) R.string.toast_xdrive_secrets else null
        AppConfig.XDRIVE_SERVICE_TEMPLATE -> if (extra?.get("template")?.isJsonObject != true) R.string.toast_xdrive_template else null
        else -> R.string.server_lab_xdrive_service
    }
}

private fun parseJsonObject(text: String): JsonObject? =
    try {
        JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject
    } catch (_: Exception) {
        null
    }
