package com.v2ray.ang.handler

import android.content.Context
import android.content.res.AssetManager
import android.text.TextUtils
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.google.gson.JsonObject
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.AppConfig.DEFAULT_SUBSCRIPTION_ID
import com.v2ray.ang.AppConfig.GEOIP_PRIVATE
import com.v2ray.ang.AppConfig.GEOSITE_PRIVATE
import com.v2ray.ang.AppConfig.TAG_DIRECT
import com.v2ray.ang.AppConfig.VPN
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.dto.RulesetItem
import com.v2ray.ang.dto.SubscriptionItem
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.Language
import com.v2ray.ang.enums.RoutingType
import com.v2ray.ang.enums.VpnInterfaceAddressConfig
import com.v2ray.ang.handler.MmkvManager.decodeAllServerList
import com.v2ray.ang.handler.MmkvManager.decodeServerConfig
import com.v2ray.ang.handler.MmkvManager.decodeSubsList
import com.v2ray.ang.handler.MmkvManager.decodeSubscription
import com.v2ray.ang.handler.MmkvManager.encodeSubscription
import com.v2ray.ang.handler.MmkvManager.removeSubscription
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.Locale

object SettingsManager {
    @Volatile
    private var localSocksAuthEnabled: Boolean = true

    @Volatile
    private var localSocksAuthUser: String? = null

    @Volatile
    private var localSocksAuthPass: String? = null

    fun initApp(context: Context) {
        ensureDefaultSettings()
        //ensureDefaultSubscription()
        initRoutingRulesets(context)
        migrateServerListToSubscriptions()
        migrateHysteria2PinSHA256()
    }

    /**
     * Initialize routing rulesets.
     * @param context The application context.
     */
    private fun initRoutingRulesets(context: Context) {
        val exist = MmkvManager.decodeRoutingRulesets()
        if (exist.isNullOrEmpty()) {
            val rulesetList = getPresetRoutingRulesets(context)
            MmkvManager.encodeRoutingRulesets(rulesetList)
        }
    }

    /**
     * Get preset routing rulesets.
     * @param context The application context.
     * @param index The index of the routing type.
     * @return A mutable list of RulesetItem.
     */
    private fun getPresetRoutingRulesets(context: Context, index: Int = 0): MutableList<RulesetItem>? {
        val fileName = RoutingType.fromIndex(index).fileName
        val assets = Utils.readTextFromAssets(context, fileName)
        if (TextUtils.isEmpty(assets)) {
            return null
        }

        return JsonUtil.fromJson(assets, Array<RulesetItem>::class.java)?.toMutableList()
    }

    /**
     * Reset routing rulesets from presets.
     * @param context The application context.
     * @param index The index of the routing type.
     */
    fun resetRoutingRulesetsFromPresets(context: Context, index: Int) {
        val rulesetList = getPresetRoutingRulesets(context, index) ?: return
        resetRoutingRulesetsCommon(rulesetList)
    }

    /**
     * Reset routing rulesets.
     * @param content The content of the rulesets.
     * @return True if successful, false otherwise.
     */
    fun resetRoutingRulesets(content: String?): Boolean {
        if (content.isNullOrEmpty()) {
            return false
        }

        try {
            val rulesetList = JsonUtil.fromJson(content, Array<RulesetItem>::class.java)?.toMutableList()
            if (rulesetList.isNullOrEmpty()) {
                return false
            }

            resetRoutingRulesetsCommon(rulesetList)
            return true
        } catch (e: Exception) {
            Log.e(ANG_PACKAGE, "Failed to reset routing rulesets", e)
            return false
        }
    }

    /**
     * Common method to reset routing rulesets.
     * @param rulesetList The list of rulesets.
     */
    private fun resetRoutingRulesetsCommon(rulesetList: MutableList<RulesetItem>) {
        val rulesetNew: MutableList<RulesetItem> = mutableListOf()
        MmkvManager.decodeRoutingRulesets()?.forEach { key ->
            if (key.locked == true) {
                rulesetNew.add(key)
            }
        }

        rulesetNew.addAll(rulesetList)
        MmkvManager.encodeRoutingRulesets(rulesetNew)
    }

    /**
     * Get a routing ruleset by index.
     * @param index The index of the ruleset.
     * @return The RulesetItem.
     */
    fun getRoutingRuleset(index: Int): RulesetItem? {
        if (index < 0) return null

        val rulesetList = MmkvManager.decodeRoutingRulesets()
        if (rulesetList.isNullOrEmpty()) return null

        return rulesetList[index]
    }

    /**
     * Save a routing ruleset.
     * @param index The index of the ruleset.
     * @param ruleset The RulesetItem to save.
     */
    fun saveRoutingRuleset(index: Int, ruleset: RulesetItem?) {
        if (ruleset == null) return

        var rulesetList = MmkvManager.decodeRoutingRulesets()
        if (rulesetList.isNullOrEmpty()) {
            rulesetList = mutableListOf()
        }

        if (index < 0 || index >= rulesetList.count()) {
            rulesetList.add(0, ruleset)
        } else {
            rulesetList[index] = ruleset
        }
        MmkvManager.encodeRoutingRulesets(rulesetList)
    }

    /**
     * Remove a routing ruleset by index.
     * @param index The index of the ruleset.
     */
    fun removeRoutingRuleset(index: Int) {
        if (index < 0) return

        val rulesetList = MmkvManager.decodeRoutingRulesets()
        if (rulesetList.isNullOrEmpty()) return

        rulesetList.removeAt(index)
        MmkvManager.encodeRoutingRulesets(rulesetList)
    }

    /**
     * Check if routing rulesets bypass LAN.
     * @return True if bypassing LAN, false otherwise.
     */
    fun routingRulesetsBypassLan(): Boolean {
        val vpnBypassLan = MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_BYPASS_LAN) ?: "1"
        if (vpnBypassLan == "1") {
            return true
        } else if (vpnBypassLan == "2") {
            return false
        }

        val guid = MmkvManager.getSelectServer() ?: return false
        val config = decodeServerConfig(guid) ?: return false
        if (config.configType == EConfigType.CUSTOM) {
            val raw = MmkvManager.decodeServerRaw(guid) ?: return false
            val v2rayConfig = JsonUtil.fromJson(raw, V2rayConfig::class.java)
            val exist = v2rayConfig?.routing?.rules?.filter { it.outboundTag == TAG_DIRECT }?.any {
                it.domain?.contains(GEOSITE_PRIVATE) == true || it.ip?.contains(GEOIP_PRIVATE) == true
            }
            return exist == true
        }

        val rulesetItems = MmkvManager.decodeRoutingRulesets()
        val exist = rulesetItems?.filter { it.enabled && it.outboundTag == TAG_DIRECT }?.any {
            it.domain?.contains(GEOSITE_PRIVATE) == true || it.ip?.contains(GEOIP_PRIVATE) == true
        }
        return exist == true
    }

    /**
     * Swap routing rulesets.
     * @param fromPosition The position to swap from.
     * @param toPosition The position to swap to.
     */
    fun swapRoutingRuleset(fromPosition: Int, toPosition: Int) {
        val rulesetList = MmkvManager.decodeRoutingRulesets()
        if (rulesetList.isNullOrEmpty()) return

        Collections.swap(rulesetList, fromPosition, toPosition)
        MmkvManager.encodeRoutingRulesets(rulesetList)
    }

    /**
     * Swap subscriptions.
     * @param fromPosition The position to swap from.
     * @param toPosition The position to swap to.
     */
    fun swapSubscriptions(fromPosition: Int, toPosition: Int) {
        val subsList = MmkvManager.decodeSubsList()
        if (subsList.isEmpty()) return

        Collections.swap(subsList, fromPosition, toPosition)
        MmkvManager.encodeSubsList(subsList)
    }

    /**
     * Get server via remarks.
     * @param remarks The remarks of the server.
     * @return The ProfileItem.
     */
    fun getServerViaRemarks(remarks: String?): ProfileItem? {
        if (remarks.isNullOrEmpty()) {
            return null
        }
        val serverList = decodeAllServerList()
        return serverList
            .mapNotNull { guid -> decodeServerConfig(guid) }
            .firstOrNull { it.remarks == remarks }
    }

    /**
     * Removes the subscription.
     * If there are no remaining subscriptions,
     * it creates a new default subscription to ensure that ungroup
     **/
    fun removeSubscriptionWithDefault(subid: String) {
//        val subsList = decodeSubsList()
//        if (subsList.size == 1 && subsList.first() == DEFAULT_SUBSCRIPTION_ID) {
//            Log.i(ANG_PACKAGE,"Attempted to remove the only existing default subscription, operation ignored.")
//            return
//        }

        // Remove the subscription
        removeSubscription(subid)

        // After removal, check if there are any subscriptions left. If not, create a default subscription.
        val subsList2 = decodeSubsList()
        if (subsList2.isNotEmpty()) {
            return
        }

        val defaultSub = SubscriptionItem(
            remarks = "Default",
        )
        encodeSubscription(DEFAULT_SUBSCRIPTION_ID, defaultSub)
    }

    /**
     * Get the SOCKS port.
     * @return The SOCKS port.
     */
    fun getSocksPort(): Int {
        return Utils.parseInt(MmkvManager.decodeSettingsString(AppConfig.PREF_SOCKS_PORT), AppConfig.PORT_SOCKS.toInt())
    }

    /**
     * Get the HTTP port.
     * @return The HTTP port.
     */
    fun getHttpPort(): Int {
        return getSocksPort() + if (Utils.isXray()) 0 else 1
    }

    /**
     * Applies local SOCKS auth from custom JSON.
     * Returns true when custom config provides usable password auth credentials for local SOCKS.
     */
    @Synchronized
    fun applyCustomLocalSocksAuth(rawConfig: String?): Boolean {
        val customAuth = resolveCustomLocalSocksAuth(rawConfig)
        localSocksAuthEnabled = customAuth.enabled
        localSocksAuthUser = customAuth.user
        localSocksAuthPass = customAuth.pass
        return customAuth.validForHevTun
    }

    /**
     * Rotates local SOCKS credentials in-memory for a new service run.
     */
    @Synchronized
    fun rotateLocalSocksAuth() {
        val userSeed = (Utils.getUuid() + Utils.getUuid()).ifEmpty {
            System.currentTimeMillis().toString(16) + System.nanoTime().toString(16)
        }
        val passSeed = (Utils.getUuid() + Utils.getUuid()).ifEmpty {
            (System.currentTimeMillis().toString(16) + System.nanoTime().toString(16))
        }
        localSocksAuthEnabled = true
        localSocksAuthUser = userSeed.take(12)
        localSocksAuthPass = passSeed.take(24)
    }

    /**
     * Returns true when the current local SOCKS runtime expects username/password auth.
     */
    fun isLocalSocksAuthEnabled(): Boolean {
        return localSocksAuthEnabled
    }

    /**
     * Ensures generated credentials exist for app-managed configs.
     */
    @Synchronized
    fun ensureManagedLocalSocksAuth() {
        if (!localSocksAuthEnabled || localSocksAuthUser == null || localSocksAuthPass == null) {
            rotateLocalSocksAuth()
        }
    }

    /**
     * Returns the local SOCKS username for the current service run.
     */
    fun getLocalSocksAuthUser(): String {
        if (!localSocksAuthEnabled) {
            return ""
        }
        return localSocksAuthUser.orEmpty()
    }

    /**
     * Returns the local SOCKS password for the current service run.
     */
    fun getLocalSocksAuthPass(): String {
        if (!localSocksAuthEnabled) {
            return ""
        }
        return localSocksAuthPass.orEmpty()
    }

    private data class LocalSocksAuth(
        val enabled: Boolean,
        val user: String? = null,
        val pass: String? = null,
        val validForHevTun: Boolean = true
    )

    private fun resolveCustomLocalSocksAuth(rawConfig: String?): LocalSocksAuth {
        val json = JsonUtil.parseString(rawConfig)
        val inbounds = if (json?.has("inbounds") == true && json.get("inbounds")?.isJsonArray == true) {
            json.getAsJsonArray("inbounds")
        } else {
            null
        }
        if (inbounds == null) {
            Log.w(AppConfig.TAG, "Custom config has no inbounds array")
            return LocalSocksAuth(enabled = false, validForHevTun = false)
        }

        val targetPort = getSocksPort()
        var foundSocksInboundOnTargetPort = false
        var foundPasswordAuthOnTargetPort = false
        var foundAccountObjectOnTargetPort = false

        for (element in inbounds) {
            if (!element.isJsonObject) continue
            val inbound = element.asJsonObject
            if (!getJsonString(inbound, "protocol").equals("socks", ignoreCase = true)) {
                continue
            }
            val inboundPort = getJsonInt(inbound, "port")
            if (inboundPort != null && inboundPort != targetPort) {
                continue
            }
            foundSocksInboundOnTargetPort = true

            val settings = if (inbound.has("settings") && inbound.get("settings")?.isJsonObject == true) {
                inbound.getAsJsonObject("settings")
            } else {
                null
            }
            val auth = getJsonString(settings, "auth")
            if (!auth.equals("password", ignoreCase = true)) {
                continue
            }
            foundPasswordAuthOnTargetPort = true

            val accounts = if (settings?.has("accounts") == true && settings.get("accounts")?.isJsonArray == true) {
                settings.getAsJsonArray("accounts")
            } else {
                null
            }
            if (accounts == null) {
                continue
            }

            for (account in accounts) {
                if (!account.isJsonObject) continue
                foundAccountObjectOnTargetPort = true
                val accountObject = account.asJsonObject
                val user = getJsonString(accountObject, "user").orEmpty().trim()
                val pass = getJsonString(accountObject, "pass").orEmpty().trim()
                if (user.isBlank() || pass.isBlank()) {
                    continue
                }
                return LocalSocksAuth(
                    enabled = true,
                    user = user,
                    pass = pass,
                    validForHevTun = true
                )
            }
        }

        if (!foundSocksInboundOnTargetPort) {
            Log.w(AppConfig.TAG, "Custom config has no socks inbound on local port $targetPort")
        } else if (!foundPasswordAuthOnTargetPort) {
            Log.w(AppConfig.TAG, "Custom socks inbound on port $targetPort must use password auth")
        } else if (!foundAccountObjectOnTargetPort) {
            Log.w(AppConfig.TAG, "Custom socks inbound on port $targetPort must include at least one account")
        } else {
            Log.w(
                AppConfig.TAG,
                "Custom socks inbound on port $targetPort must include non-empty user and pass"
            )
        }
        return LocalSocksAuth(enabled = false, validForHevTun = false)
    }

    private fun getJsonString(jsonObject: JsonObject?, name: String): String? {
        if (jsonObject == null || !jsonObject.has(name)) {
            return null
        }
        val element = jsonObject.get(name)
        if (element == null || element.isJsonNull) {
            return null
        }
        return try {
            element.asString
        } catch (_: Exception) {
            null
        }
    }

    private fun getJsonInt(jsonObject: JsonObject?, name: String): Int? {
        if (jsonObject == null || !jsonObject.has(name)) {
            return null
        }
        val element = jsonObject.get(name)
        if (element == null || element.isJsonNull) {
            return null
        }
        return try {
            if (element.isJsonPrimitive && element.asJsonPrimitive.isNumber) {
                element.asInt
            } else {
                element.asString.toIntOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Initialize assets.
     * @param context The application context.
     * @param assets The AssetManager.
     */
    fun initAssets(context: Context, assets: AssetManager) {
        val extFolder = Utils.userAssetPath(context)

        try {
            val geo = arrayOf(AppConfig.GEOSITE_DAT, AppConfig.GEOIP_DAT, AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT)
            assets.list("")
                ?.filter { geo.contains(it) }
                ?.filter { !File(extFolder, it).exists() }
                ?.forEach {
                    val target = File(extFolder, it)
                    assets.open(it).use { input ->
                        FileOutputStream(target).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.i(AppConfig.TAG, "Copied from apk assets folder to ${target.absolutePath}")
                }
        } catch (e: Exception) {
            Log.e(ANG_PACKAGE, "asset copy failed", e)
        }
    }

    /**
     * Get domestic DNS servers from preference.
     * @return A list of domestic DNS servers.
     */
    fun getDomesticDnsServers(): List<String> {
        val domesticDns =
            MmkvManager.decodeSettingsString(AppConfig.PREF_DOMESTIC_DNS) ?: AppConfig.DNS_DIRECT
        val ret = domesticDns.split(",").filter { Utils.isPureIpAddress(it) || Utils.isCoreDNSAddress(it) }
        if (ret.isEmpty()) {
            return listOf(AppConfig.DNS_DIRECT)
        }
        return ret
    }

    /**
     * Get remote DNS servers from preference.
     * @return A list of remote DNS servers.
     */
    fun getRemoteDnsServers(): List<String> {
        val remoteDns =
            MmkvManager.decodeSettingsString(AppConfig.PREF_REMOTE_DNS) ?: AppConfig.DNS_PROXY
        val ret = remoteDns.split(",").filter { Utils.isPureIpAddress(it) || Utils.isCoreDNSAddress(it) }
        if (ret.isEmpty()) {
            return listOf(AppConfig.DNS_PROXY)
        }
        return ret
    }

    /**
     * Get VPN DNS servers from preference.
     * @return A list of VPN DNS servers.
     */
    fun getVpnDnsServers(): List<String> {
        val vpnDns = MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_DNS) ?: AppConfig.DNS_VPN
        return vpnDns.split(",").filter { Utils.isPureIpAddress(it) }
    }

    /**
     * Get delay test URL.
     * @param second Whether to use the second URL.
     * @return The delay test URL.
     */
    fun getDelayTestUrl(second: Boolean = false): String {
        return if (second) {
            AppConfig.DELAY_TEST_URL2
        } else {
            MmkvManager.decodeSettingsString(AppConfig.PREF_DELAY_TEST_URL)
                ?: AppConfig.DELAY_TEST_URL
        }
    }

    /**
     * Get the locale.
     * @return The locale.
     */
    fun getLocale(): Locale {
        val langCode =
            MmkvManager.decodeSettingsString(AppConfig.PREF_LANGUAGE) ?: Language.AUTO.code
        val language = Language.fromCode(langCode)

        return when (language) {
            Language.AUTO -> Utils.getSysLocale()
            Language.ENGLISH -> Locale.ENGLISH
            Language.CHINA -> Locale.CHINA
            Language.TRADITIONAL_CHINESE -> Locale.TRADITIONAL_CHINESE
            Language.VIETNAMESE -> Locale.forLanguageTag("vi")
            Language.RUSSIAN -> Locale.forLanguageTag("ru")
            Language.PERSIAN -> Locale.forLanguageTag("fa")
            Language.ARABIC -> Locale.forLanguageTag("ar")
            Language.BANGLA -> Locale.forLanguageTag("bn")
            Language.BAKHTIARI -> Locale.forLanguageTag("bqi-IR")
        }
    }

    /**
     * Set night mode.
     */
    fun setNightMode() {
        when (MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0")) {
            "0" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            "1" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "2" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    /**
     * Retrieves the currently selected VPN interface address configuration.
     * This method reads the user's preference for VPN interface addressing and returns
     * the corresponding configuration containing IPv4 and IPv6 addresses.
     *
     * @return The selected VpnInterfaceAddressConfig instance, or the default configuration
     *         if no valid selection is found or if the stored index is invalid.
     */
    fun getCurrentVpnInterfaceAddressConfig(): VpnInterfaceAddressConfig {
        val selectedIndex = MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_INTERFACE_ADDRESS_CONFIG_INDEX, "0")?.toInt()
        return VpnInterfaceAddressConfig.getConfigByIndex(selectedIndex ?: 0)
    }

    /**
     * Get the VPN MTU from settings, defaulting to AppConfig.VPN_MTU.
     */
    fun getVpnMtu(): Int {
        return Utils.parseInt(MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_MTU), AppConfig.VPN_MTU)
    }

    /**
     * Check if HEV TUN is being used.
     * @return True if HEV TUN is used, false otherwise.
     */
    fun isUsingHevTun(): Boolean {
        return MmkvManager.decodeSettingsBool(AppConfig.PREF_USE_HEV_TUNNEL, true)
    }

    /**
     * Check if VPN mode is enabled.
     * @return True if VPN mode is enabled, false otherwise.
     */
    fun isVpnMode(): Boolean {
        val mode = MmkvManager.decodeSettingsString(AppConfig.PREF_MODE)
        return mode == null || mode == VPN
    }

    /**
     * Ensure default settings are present in MMKV.
     */
    private fun ensureDefaultSettings() {
        // Write defaults in the exact order requested by the user
        ensureDefaultValue(AppConfig.PREF_MODE, AppConfig.VPN)
        ensureDefaultValue(AppConfig.PREF_VPN_DNS, AppConfig.DNS_VPN)
        ensureDefaultValue(AppConfig.PREF_VPN_MTU, AppConfig.VPN_MTU.toString())
        ensureDefaultValue(AppConfig.SUBSCRIPTION_AUTO_UPDATE_INTERVAL, AppConfig.SUBSCRIPTION_DEFAULT_UPDATE_INTERVAL)
        ensureDefaultValue(AppConfig.PREF_SOCKS_PORT, AppConfig.PORT_SOCKS)
        ensureDefaultValue(AppConfig.PREF_REMOTE_DNS, AppConfig.DNS_PROXY)
        ensureDefaultValue(AppConfig.PREF_DOMESTIC_DNS, AppConfig.DNS_DIRECT)
        ensureDefaultValue(AppConfig.PREF_DELAY_TEST_URL, AppConfig.DELAY_TEST_URL)
        ensureDefaultValue(AppConfig.PREF_IP_API_URL, AppConfig.IP_API_URL)
        ensureDefaultValue(AppConfig.PREF_HEV_TUNNEL_RW_TIMEOUT, AppConfig.HEVTUN_RW_TIMEOUT)
        ensureDefaultValue(AppConfig.PREF_MUX_CONCURRENCY, "8")
        ensureDefaultValue(AppConfig.PREF_MUX_XUDP_CONCURRENCY, "8")
        ensureDefaultValue(AppConfig.PREF_FRAGMENT_LENGTH, "50-100")
        ensureDefaultValue(AppConfig.PREF_FRAGMENT_INTERVAL, "10-20")
    }

    private fun ensureDefaultValue(key: String, default: String) {
        if (MmkvManager.decodeSettingsString(key).isNullOrEmpty()) {
            MmkvManager.encodeSettings(key, default)
        }
    }

    private fun migrateHysteria2PinSHA256() {
        // Check if migration has already been done
        val migrationKey = "hysteria2_pin_sha256_migrated"
        if (MmkvManager.decodeSettingsBool(migrationKey, false)) {
            return
        }

        val serverList = decodeAllServerList()

        for (guid in serverList) {
            val profile = decodeServerConfig(guid) ?: continue
            if (profile.configType != EConfigType.HYSTERIA2) {
                continue
            }
            if (profile.pinSHA256.isNullOrEmpty() || !profile.pinnedCA256.isNullOrEmpty()) {
                continue
            }
            profile.pinnedCA256 = profile.pinSHA256
            profile.pinSHA256 = null
            MmkvManager.encodeServerConfig(guid, profile)
        }

        MmkvManager.encodeSettings(migrationKey, true)
    }

    /**
     * Migrates server list from legacy KEY_ANG_CONFIGS to subscription-based storage.
     * This method should be called once during app initialization after the storage structure change.
     * Servers are grouped by their subscriptionId into respective subscription's serverList.
     * Servers without subscription are moved to the default subscription.
     * After migration, KEY_ANG_CONFIGS is removed.
     */
    private fun migrateServerListToSubscriptions() {
        // Check if migration has already been done
        val migrationKey = "server_list_to_subscriptions_migrated"
        if (MmkvManager.decodeSettingsBool(migrationKey, false)) {
            return
        }

        // Ensure default subscription exists before migration
        ensureDefaultSubscription()

        // Read existing server list from legacy KEY_ANG_CONFIGS
        val oldJson = MmkvManager.readLegacyServerList()
        if (oldJson.isNullOrBlank()) {
            // No data to migrate, mark as done
            MmkvManager.encodeSettings(migrationKey, true)
            return
        }

        val guids = JsonUtil.fromJson(oldJson, Array<String>::class.java) ?: run {
            MmkvManager.encodeSettings(migrationKey, true)
            return
        }

        val subscriptionServerMap = mutableMapOf<String, MutableList<String>>()

        // Group servers by subscription (use default subscription for empty subscriptionId)
        guids.forEach { guid ->
            val config = decodeServerConfig(guid) ?: return@forEach
            val subId = config.subscriptionId.ifEmpty { DEFAULT_SUBSCRIPTION_ID }

            subscriptionServerMap.getOrPut(subId) { mutableListOf() }.add(guid)
        }

        // Update each subscription's serverList (including default subscription)
        subscriptionServerMap.forEach { (subId, serverGuids) ->
            MmkvManager.encodeServerList(serverGuids, subId)
        }


        // Mark migration as complete
        MmkvManager.encodeSettings(migrationKey, true)
    }

    /**
     * Ensures the default subscription exists for ungrouped servers.
     * This subscription is used internally to store servers without a subscription.
     * Made public for migration in SettingsManager.
     */
    private fun ensureDefaultSubscription() {
        if (decodeSubscription(DEFAULT_SUBSCRIPTION_ID) == null) {
            val defaultSub = SubscriptionItem(
                remarks = "Default",
            )
            encodeSubscription(DEFAULT_SUBSCRIPTION_ID, defaultSub)

            // Move top
            val subsList = decodeSubsList()
            if (subsList.count() > 1) {
                swapSubscriptions(0, subsList.count() - 1)
            }
        }
    }

}
