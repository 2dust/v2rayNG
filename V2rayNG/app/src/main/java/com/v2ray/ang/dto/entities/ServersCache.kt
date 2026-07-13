package com.v2ray.ang.dto.entities

import com.v2ray.ang.util.JsonUtil

/**
 * Immutable UI snapshot used by MainActivity.
 *
 * ProfileItem overrides equals() to compare only connection parameters,
 * ignoring remarks, description, policy group, and other UI fields.
 *
 * profileSnapshot participates in data class equals(), ensuring that any
 * change in persistent fields causes ServersCache to be recognized as a
 * new value by StateFlow.
 */
data class ServersCache(
    val guid: String,
    val profile: ProfileItem,
    val testDelayMillis: Long = 0L,
    val testDelayString: String = "",

    /**
     * Must be generated when creating ServersCache; do not read dynamically
     * inside Composables.
     */
    val profileSnapshot: String = JsonUtil.toJson(profile)
)