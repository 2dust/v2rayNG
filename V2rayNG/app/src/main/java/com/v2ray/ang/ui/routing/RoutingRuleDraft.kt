package com.v2ray.ang.ui.routing

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.v2ray.ang.AppConfig.TAG_PROXY
import com.v2ray.ang.dto.entities.RulesetItem

internal val routingNetworkOptions = listOf("tcp", "udp", "tcp,udp")

/** Uncommitted form values shared by the phone and TV editors. */
@Stable
internal class RoutingRuleDraft(initial: RulesetItem?) {
    val remarks = mutableStateOf(initial?.remarks.orEmpty())
    val locked = mutableStateOf(initial?.locked == true)
    val domain = mutableStateOf(initial?.domain?.joinToString(",").orEmpty())
    val ip = mutableStateOf(initial?.ip?.joinToString(",").orEmpty())
    val process = mutableStateOf(initial?.process?.joinToString(",").orEmpty())
    val port = mutableStateOf(initial?.port.orEmpty())
    val protocol = mutableStateOf(initial?.protocol?.joinToString(",").orEmpty())
    val network = mutableStateOf(initial?.network.orEmpty())
    val outboundTag = mutableStateOf(initial?.outboundTag ?: TAG_PROXY)

    private val textFields: List<MutableState<String>> = listOf(remarks, domain, ip, process, port, protocol, network, outboundTag)

    fun buildRuleset(): RulesetItem =
        RulesetItem(
            remarks = remarks.value,
            locked = locked.value,
            domain = domain.value.parseList(),
            ip = ip.value.parseList(),
            process = process.value.parseList(),
            port = port.value.takeUnless(String::isBlank),
            protocol = protocol.value.parseList(),
            network = network.value.takeUnless(String::isBlank),
            outboundTag = outboundTag.value.trim().ifEmpty { TAG_PROXY }
        )

    companion object {
        val Saver: Saver<RoutingRuleDraft, Any> = listSaver(
            save = { draft -> draft.textFields.map { it.value } + draft.locked.value },
            restore = { values ->
                RoutingRuleDraft(null).apply {
                    textFields.forEachIndexed { index, field -> field.value = values[index] as String }
                    locked.value = values.last() as Boolean
                }
            }
        )
    }
}

internal fun String.parseRoutingRuleList(): List<String> =
    split(',').map(String::trim).filter(String::isNotEmpty)

private fun String.parseList(): List<String>? = parseRoutingRuleList().takeIf(List<String>::isNotEmpty)
