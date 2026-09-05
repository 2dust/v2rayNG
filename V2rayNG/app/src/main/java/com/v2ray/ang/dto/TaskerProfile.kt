package com.v2ray.ang.dto

import androidx.compose.runtime.Immutable
import com.v2ray.ang.ui.base.BaseText

/**
 * A profile a Tasker task may target. Identified by guid, never by list position.
 *
 * [name] stays unresolved on purpose: the "default" entry is a string resource while every other
 * entry is user-typed remarks, and the data layer must not own a Context to tell them apart.
 */
@Immutable
data class TaskerProfile(
    val guid: String,
    val name: BaseText,
)
