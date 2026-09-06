package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.RulesetItem
import java.util.UUID

/** Preserve valid identities and rule contents; only absent or repeated IDs need replacements. */
internal fun withUniqueRoutingRuleIds(
    rules: List<RulesetItem>,
    newId: () -> String = { UUID.randomUUID().toString() },
): List<RulesetItem> {
    // Legacy/imported JSON can contain null even though the Kotlin field is non-nullable.
    val reserved = rules.mapTo(mutableSetOf<String?>()) { it.id }
    val seen = mutableSetOf<String>()
    return rules.map { rule ->
        val id: String? = rule.id
        if (!id.isNullOrBlank() && seen.add(id)) {
            rule
        } else {
            var replacement: String
            do {
                replacement = newId()
            } while (replacement.isBlank() || !reserved.add(replacement))
            seen.add(replacement)
            rule.copy(id = replacement)
        }
    }
}
