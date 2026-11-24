package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayNShareItem
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils

object V2rayNFmt : FmtBase() {
    /**
     * Parses v2rayN inner share lines the way v2rayN InnerFmt.Resolve does: an entry whose
     * ConfigType this app cannot represent is skipped on its own, entries without IndexId are all
     * kept, and a group whose listed members were all skipped is dropped.
     */
    fun parse(list: List<String>, subId: String): List<ProfileItem> {
        val idMap = mutableMapOf<String, V2rayNShareItem>()
        val entries = mutableListOf<Pair<V2rayNShareItem, ProfileItem>>()
        list.mapNotNull(::parseShareItem).forEach { item ->
            val profile = item.toProfileItem() ?: return@forEach
            // IndexId is optional and only links group members, so it must not merge entries without one.
            val indexId = item.IndexId.orEmpty()
            if (indexId.isEmpty() || idMap.putIfAbsent(indexId, item) == null) {
                entries.add(item to profile)
            }
        }

        return entries.mapNotNull { (item, profile) ->
            val proto = item.ProtoExtraObj
            val isSelfSub = proto?.SubChildItems == "self"
            profile.policyGroupSubscriptionId = if (isSelfSub) subId else null

            val childIds = proto?.ChildItems?.takeIf { it.isNotNullEmpty() }?.split(",").orEmpty()
            val remarks = childIds
                .mapNotNull { idMap[it]?.Remarks }
                .filter { it.isNotNullEmpty() }
            if (remarks.isNotEmpty()) {
                when (item.ConfigType) {
                    101 -> profile.policyGroupFilter = remarks.joinToString("|", "^(", ")$") { Regex.escape(it) }
                    102 -> {
                        profile.proxyChainProfileList = remarks
                        profile.description = remarks.joinToString(" -> ")
                    }
                }
            }

            // Every listed member was skipped, so the group would have nothing to route through.
            if (childIds.isNotEmpty() && remarks.isEmpty() && !isSelfSub) null else profile
        }
    }

    private fun parseShareItem(str: String): V2rayNShareItem? = try {
        JsonUtil.fromJson(Utils.decode(str.substringAfterLast('/')), V2rayNShareItem::class.java)
    } catch (e: Exception) {
        LogUtil.e(AppConfig.TAG, "Failed to parse V2rayN share item", e)
        null
    }
}
