package com.v2ray.ang.repository

import android.app.Application
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.RoutingEditData
import com.v2ray.ang.dto.RoutingRuleRow
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.dto.toRuleRows
import com.v2ray.ang.enums.RoutingType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

open class RoutingRepository(private val app: Application) : BaseRepository() {

    private val outboundMutex = Mutex()
    private val outboundEpoch = AtomicLong(0L)
    @Volatile private var outboundCache: List<String>? = null

    // ----- Rule loading with id deduplication -----

    open suspend fun loadRulesets(): List<RulesetItem> = runIO(emptyList()) {
        val list = MmkvManager.decodeRoutingRulesets()?.toMutableList() ?: mutableListOf()
        var patched = false
        val seen = HashSet<String>(list.size)
        list.forEach { item ->
            if (item.id.isEmpty() || !seen.add(item.id)) {
                item.id = UUID.randomUUID().toString()
                seen.add(item.id)
                patched = true
            }
        }
        if (patched) {
            withContext(NonCancellable) { MmkvManager.encodeRoutingRulesets(ArrayList(list)) }
        }
        list
    }

    open suspend fun loadRuleRows(): List<RoutingRuleRow> = runIO(emptyList()) {
        loadRulesets().toRuleRows()
    }

    open suspend fun loadEditData(ruleId: String): RoutingEditData = runIO(
        RoutingEditData(ruleset = null, canUseProcess = false)
    ) {
        val rules = loadRulesets()
        val item = rules.find { it.id == ruleId }
        RoutingEditData(
            ruleset = item,
            canUseProcess = SettingsManager.canUseProcessRouting(),
        )
    }

    // ----- Insert / update / remove by id (atomic) -----

    open suspend fun insertRule(item: RulesetItem): String = runIO("") {
        val list = loadRulesets().toMutableList()
        if (item.id.isEmpty()) item.id = UUID.randomUUID().toString()
        list.add(0, item)
        withContext(NonCancellable) { MmkvManager.encodeRoutingRulesets(ArrayList(list)) }
        item.id
    }

    open suspend fun updateRule(item: RulesetItem): Boolean = runIO(false) {
        val list = loadRulesets().toMutableList()
        val index = list.indexOfFirst { it.id == item.id }
        if (index < 0) return@runIO false
        list[index] = item
        withContext(NonCancellable) { MmkvManager.encodeRoutingRulesets(ArrayList(list)) }
        true
    }

    open suspend fun removeRule(ruleId: String): Boolean = runIO(false) {
        val list = loadRulesets().toMutableList()
        if (!list.removeAll { it.id == ruleId }) return@runIO false
        withContext(NonCancellable) { MmkvManager.encodeRoutingRulesets(ArrayList(list)) }
        true
    }

    open suspend fun saveOrder(list: List<RulesetItem>) = runIO(Unit) {
        withContext(NonCancellable) { MmkvManager.encodeRoutingRulesets(ArrayList(list)) }
    }

    // ----- Domain strategy -----

    open suspend fun getDomainStrategy(): String = runIO("") {
        MmkvManager.decodeSettingsString(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY).orEmpty()
    }

    open suspend fun setDomainStrategy(value: String) = runIO(Unit) {
        withContext(NonCancellable) { MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY, value) }
    }

    // ----- Preset / import / export -----

    open suspend fun importPresets(type: RoutingType): Boolean = runIO(false) {
        SettingsManager.resetRoutingRulesetsFromPresets(app, type)
    }

    open suspend fun importRulesets(text: String?): Boolean = runIO(false) {
        if (text.isNullOrBlank()) return@runIO false
        SettingsManager.resetRoutingRulesets(text)
    }

    open suspend fun readClipboard(): String = runIO("") {
        Utils.getClipboard(app)
    }

    open suspend fun exportToClipboard(): Boolean = runIO(false) {
        val json = MmkvManager.decodeRoutingRulesets()
            ?.takeIf { it.isNotEmpty() }
            ?.let(JsonUtil::toJson)
            ?: return@runIO false
        Utils.setClipboard(app, json)
        true
    }

    // ----- Outbound options cache with epoch guard -----

    open suspend fun outboundOptions(): List<String> {
        outboundCache?.let { return it }
        return outboundMutex.withLock {
            outboundCache?.let { return@withLock it }
            val epoch = outboundEpoch.get()
            val loaded = runIO(emptyList()) {
                val builtin = AppConfig.BUILTIN_OUTBOUND_TAGS.toList()
                val remarks = SettingsManager.getProfileRemarks()
                    .asSequence()
                    .filterNot { it in builtin }
                    .distinct()
                    .sortedWith(String.CASE_INSENSITIVE_ORDER)
                    .toList()
                builtin + remarks
            }
            if (outboundEpoch.get() == epoch) {
                outboundCache = loaded
            }
            loaded
        }
    }

    open fun invalidateOutboundOptions() {
        outboundEpoch.incrementAndGet()
        outboundCache = null
    }

    open suspend fun canUseProcessRouting(): Boolean = runIO(false) {
        SettingsManager.canUseProcessRouting()
    }
}
