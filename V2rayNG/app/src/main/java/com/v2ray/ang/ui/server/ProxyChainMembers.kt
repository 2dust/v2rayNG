package com.v2ray.ang.ui.server

internal fun firstRepeatedMember(members: List<String>): String? {
    val seen = mutableSetOf<String>()
    return members.firstOrNull { !seen.add(it) }
}

internal fun availableProxyChainMembers(options: List<String>, members: List<String>, editingIndex: Int): List<String> {
    val usedElsewhere = members.filterIndexed { index, _ -> index != editingIndex }.toSet()
    return options.filterNot(usedElsewhere::contains)
}

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
