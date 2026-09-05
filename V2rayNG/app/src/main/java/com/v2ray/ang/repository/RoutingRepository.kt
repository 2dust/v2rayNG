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
import kotlinx.coroutines.withContext
import java.util.UUID

open class RoutingRepository(private val app: Application) : BaseRepository() {

    // ----- Rule loading with id deduplication -----

    open suspend fun loadRulesets(): List<RulesetItem> = runIO(emptyList()) { repairedRulesets() }

    open suspend fun loadRuleRows(): List<RoutingRuleRow> = runIO(emptyList()) {
        repairedRulesets().toRuleRows()
    }

    /**
     * One IO pass for the whole edit screen so the ViewModel can publish a single state.
     *
     * Goes through [repairedRulesets] on purpose: [updateRule] re-assigns duplicated ids, an
     * editor holding a pre-repair id could later update the wrong rule or fail to find it.
     * Throws on storage failure - the caller must distinguish "not found" from "cannot read".
     */
    open suspend fun loadEditData(ruleId: String): RoutingEditData = withIO {
        RoutingEditData(
            ruleset = if (ruleId.isEmpty()) null else repairedRulesets().find { it.id == ruleId },
            canUseProcess = SettingsManager.canUseProcessRouting(),
            outboundOptions = buildOutboundOptions(),
        )
    }

    // ----- Insert / update / remove by id (atomic) -----

    open suspend fun insertRule(item: RulesetItem): String = runIO("") {
        val list = repairedRulesets().toMutableList()
        if (item.id.isEmpty()) item.id = UUID.randomUUID().toString()
        list.add(0, item)
        withContext(NonCancellable) { MmkvManager.encodeRoutingRulesets(ArrayList(list)) }
        item.id
    }

    open suspend fun updateRule(item: RulesetItem): Boolean = runIO(false) {
        val list = repairedRulesets().toMutableList()
        val index = list.indexOfFirst { it.id == item.id }
        if (index < 0) return@runIO false
        list[index] = item
        withContext(NonCancellable) { MmkvManager.encodeRoutingRulesets(ArrayList(list)) }
        true
    }

    open suspend fun removeRule(ruleId: String): Boolean = runIO(false) {
        val list = repairedRulesets().toMutableList()
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
        withContext(NonCancellable) {
            MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY, value)
        }
    }

    // ----- Preset / import / export -----

    open suspend fun importPresets(type: RoutingType): Boolean = runIO(false) {
        SettingsManager.resetRoutingRulesetsFromPresets(app, type)
    }

    open suspend fun importRulesets(text: String?): Boolean = runIO(false) {
        if (text.isNullOrBlank()) return@runIO false
        SettingsManager.resetRoutingRulesets(text)
    }

    open suspend fun readClipboard(): String = runIO("") { Utils.getClipboard(app) }

    open suspend fun exportToClipboard(): Boolean = runIO(false) {
        val json = MmkvManager.decodeRoutingRulesets()
            ?.takeIf { it.isNotEmpty() }
            ?.let(JsonUtil::toJson)
            ?: return@runIO false
        Utils.setClipboard(app, json)
        true
    }

    // ----- Internals -----

    /** Reads the rulesets, giving every item a unique id */
    private suspend fun repairedRulesets(): List<RulesetItem> {
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
        return list
    }

    private fun buildOutboundOptions(): List<String> {
        val builtin = AppConfig.BUILTIN_OUTBOUND_TAGS.toList()
        val remarks = SettingsManager.getProfileRemarks()
            .asSequence()
            .filterNot { it in builtin }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .toList()
        return builtin + remarks
    }
}
