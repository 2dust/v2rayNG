package com.v2ray.ang.dto

import com.v2ray.ang.dto.entities.RulesetItem

data class RoutingEditData(
    val ruleset: RulesetItem?,
    val canUseProcess: Boolean,
)
