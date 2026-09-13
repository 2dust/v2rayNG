package com.v2ray.ang.ui.server

import com.v2ray.ang.extension.moveItem

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

/** Both drag and accessibility moves resolve the current positions from stable draft keys. */
internal fun moveProxyChainMember(
    members: List<String>,
    memberKeys: List<String>,
    fromKey: String,
    toKey: String,
): Pair<List<String>, List<String>>? {
    val fromIndex = memberKeys.indexOf(fromKey)
    val toIndex = memberKeys.indexOf(toKey)
    val reordered = members.toMutableList()
    val reorderedKeys = memberKeys.toMutableList()
    if (!reordered.moveItem(fromIndex, toIndex) || !reorderedKeys.moveItem(fromIndex, toIndex)) return null
    return reordered to reorderedKeys
}
