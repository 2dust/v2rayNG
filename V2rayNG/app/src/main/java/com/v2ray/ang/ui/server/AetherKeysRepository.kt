package com.v2ray.ang.ui.server

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.AetherExit
import com.v2ray.ang.core.AetherIdentityManager
import com.v2ray.ang.core.AetherKey
import com.v2ray.ang.core.AetherKeysSettings
import com.v2ray.ang.enums.AetherFingerprint
import com.v2ray.ang.enums.AetherKeyKind
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AetherKeysSource {
    suspend fun isCoreAvailable(): Boolean

    /** The daemon's live Aether session, scanning or connected, or null; it may use keys a renewal would replace. */
    suspend fun activeSession(): AetherSession?

    /** The settings of the page as they were left, or the defaults the first time. */
    suspend fun loadSettings(): AetherKeysSettings

    suspend fun saveSettings(settings: AetherKeysSettings)

    /** Every key in use, each key file with the identity it holds. */
    suspend fun keys(): List<AetherKey>

    /** Registers new keys of [kind] with the core on [arguments], dialling out through [exit]; the new keys, or null when the old ones stay. */
    suspend fun renew(kind: AetherKeyKind, arguments: List<String>, exit: AetherExit, onOutput: (String) -> Unit): List<AetherKey>?
}

/**
 * The page that gets new WARP keys: its settings, kept in the app settings apart from every profile, and the
 * keys, which every Aether profile shares. Whether the core is there and whether a session runs are told as the
 * Aether editor tells them.
 */
class AetherKeysRepository(private val context: Context) : AetherKeysSource {

    private val editor = AetherEditorRepository(context)

    override suspend fun isCoreAvailable(): Boolean = editor.isCoreAvailable()

    override suspend fun activeSession(): AetherSession? = editor.activeSession()

    override suspend fun loadSettings(): AetherKeysSettings = withContext(Dispatchers.IO) {
        val defaults = AetherKeysSettings()
        AetherKeysSettings(
            kind = AetherKeyKind.fromString(MmkvManager.decodeSettingsString(AppConfig.PREF_AETHER_KEYS_KIND)),
            enrollAddress = text(AppConfig.PREF_AETHER_KEYS_ENROLL_ADDRESS, defaults.enrollAddress),
            ech = MmkvManager.decodeSettingsBool(AppConfig.PREF_AETHER_KEYS_ECH, defaults.ech),
            echDns = text(AppConfig.PREF_AETHER_KEYS_ECH_DNS, defaults.echDns),
            echDomain = text(AppConfig.PREF_AETHER_KEYS_ECH_DOMAIN, defaults.echDomain),
            fingerprint = AetherFingerprint.fromString(MmkvManager.decodeSettingsString(AppConfig.PREF_AETHER_KEYS_FINGERPRINT)),
            finalMask = text(AppConfig.PREF_AETHER_KEYS_FINAL_MASK, defaults.finalMask),
            dialMode = text(AppConfig.PREF_AETHER_KEYS_DIAL_MODE, defaults.dialMode),
            command = text(AppConfig.PREF_AETHER_KEYS_COMMAND, defaults.command),
        )
    }

    override suspend fun saveSettings(settings: AetherKeysSettings) {
        // Each setting stands alone: one that fails to save keeps its earlier value, and the others still count.
        val saved = withContext(Dispatchers.IO) { save(settings) }
        if (!saved) {
            LogUtil.w(AppConfig.TAG, "AetherKeys: not every setting of the WARP keys page was saved; the next visit shows the earlier value of those")
        }
    }

    private fun save(settings: AetherKeysSettings): Boolean =
        listOf(
            MmkvManager.encodeSettings(AppConfig.PREF_AETHER_KEYS_KIND, settings.kind.type),
            MmkvManager.encodeSettings(AppConfig.PREF_AETHER_KEYS_ENROLL_ADDRESS, settings.enrollAddress),
            MmkvManager.encodeSettings(AppConfig.PREF_AETHER_KEYS_ECH, settings.ech),
            MmkvManager.encodeSettings(AppConfig.PREF_AETHER_KEYS_ECH_DNS, unlessDefault(settings.echDns, AppConfig.AETHER_ECH_DNS)),
            MmkvManager.encodeSettings(AppConfig.PREF_AETHER_KEYS_ECH_DOMAIN, unlessDefault(settings.echDomain, AppConfig.AETHER_ECH_DOMAIN)),
            MmkvManager.encodeSettings(AppConfig.PREF_AETHER_KEYS_FINGERPRINT, settings.fingerprint.type),
            MmkvManager.encodeSettings(AppConfig.PREF_AETHER_KEYS_FINAL_MASK, settings.finalMask),
            MmkvManager.encodeSettings(AppConfig.PREF_AETHER_KEYS_DIAL_MODE, settings.dialMode),
            MmkvManager.encodeSettings(AppConfig.PREF_AETHER_KEYS_COMMAND, settings.command),
        ).all { it }

    override suspend fun keys(): List<AetherKey> = AetherIdentityManager.keys(context)

    override suspend fun renew(kind: AetherKeyKind, arguments: List<String>, exit: AetherExit, onOutput: (String) -> Unit): List<AetherKey>? =
        AetherIdentityManager.renew(context, kind, arguments, exit, onOutput)

    private fun text(key: String, default: String): String = MmkvManager.decodeSettingsString(key, default) ?: default

    /**
     * [value] trimmed as it is stored, or null, which stores nothing, when it is blank or [default]: it then reads as the
     * default, whatever that comes to be, as a profile's ECH resolver and domain do.
     */
    private fun unlessDefault(value: String, default: String): String? = value.trim().takeUnless { it.isEmpty() || it == default }
}
