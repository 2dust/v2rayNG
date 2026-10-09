package com.v2ray.ang.ui.server

import com.v2ray.ang.enums.EConfigType

/** Removes one draft member and its row key together, resolving its current position at confirmation. */
internal fun withoutProxyChainMember(
    members: List<String>,
    memberKeys: List<String>,
    memberKey: String,
): Pair<List<String>, List<String>> {
    val index = memberKeys.indexOf(memberKey)
    if (index < 0) return members to memberKeys

    return members.toMutableList().also { it.removeAt(index) } to
        memberKeys.toMutableList().also { it.removeAt(index) }
}

/**
 * True when [memberTypes], the types of a chain's members, hold more than one Aether profile. One can
 * stand anywhere in the chain; a second would need a core of its own, and one core runs at a time.
 */
internal fun hasSecondAetherMember(memberTypes: List<EConfigType?>): Boolean =
    memberTypes.count { it == EConfigType.AETHER } > 1
