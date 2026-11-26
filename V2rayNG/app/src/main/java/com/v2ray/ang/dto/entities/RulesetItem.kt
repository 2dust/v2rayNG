package com.v2ray.ang.dto.entities

import java.util.UUID

data class RulesetItem(
    var id: String = "",
    var remarks: String? = "",
    var ip: List<String>? = null,
    var domain: List<String>? = null,
    var process: List<String>? = null,
    var outboundTag: String = "",
    var port: String? = null,
    var network: String? = null,
    var protocol: List<String>? = null,
    var enabled: Boolean = true,
    var locked: Boolean? = false,
)

/** Keep the first occurrence: locked rules precede imported rules when the lists are combined. */
internal fun List<RulesetItem>.ensureUniqueIds(): Boolean {
    val seen = mutableSetOf<String>()
    var changed = false
    for (rule in this) {
        if (rule.id.isNullOrBlank() || !seen.add(rule.id)) {
            rule.id = UUID.randomUUID().toString()
            seen.add(rule.id)
            changed = true
        }
    }
    return changed
}
