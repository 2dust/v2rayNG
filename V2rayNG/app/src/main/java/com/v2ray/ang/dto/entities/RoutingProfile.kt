package com.v2ray.ang.dto.entities

data class RoutingProfile(
    val id: String = "",
    var name: String = "Default",
    var domainStrategy: String = "AsIs",
    var rulesets: MutableList<RulesetItem> = mutableListOf()
)
