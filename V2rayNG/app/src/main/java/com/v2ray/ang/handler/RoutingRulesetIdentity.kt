package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.RulesetItem
import java.util.UUID

/** Keep rule contents and valid identities when repairing legacy or imported duplicate IDs. */
internal fun withUniqueRoutingRuleIds(rules: List<RulesetItem>): List<RulesetItem> {
    val seen = mutableSetOf<String>()
    return rules.map { rule ->
        // Gson can populate a non-null Kotlin field with null from legacy JSON.
        val id: String? = rule.id
        if (!id.isNullOrBlank() && seen.add(id)) {
            rule
        } else {
            rule.copy(id = UUID.randomUUID().toString()).also { seen.add(it.id) }
        }
    }
}
