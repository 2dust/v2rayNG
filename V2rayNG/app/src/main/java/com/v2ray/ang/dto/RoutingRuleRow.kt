package com.v2ray.ang.dto

import androidx.compose.runtime.Immutable
import com.v2ray.ang.dto.entities.RulesetItem

@Immutable
data class RoutingRuleRow(
    val id: String,
    val remarks: String,
    val detail: String,
    val outboundTag: String,
    val locked: Boolean,
    val enabled: Boolean,
)

fun List<RulesetItem>.toRuleRows(): List<RoutingRuleRow> = map { item ->
    RoutingRuleRow(
        id = item.id,
        remarks = item.remarks.orEmpty(),
        detail = listOfNotNull(item.domain, item.ip, item.process)
            .firstOrNull { it.isNotEmpty() }
            ?.joinToString(", ")
            ?: item.port.orEmpty(),
        outboundTag = item.outboundTag,
        locked = item.locked == true,
        enabled = item.enabled == true,
    )
}
