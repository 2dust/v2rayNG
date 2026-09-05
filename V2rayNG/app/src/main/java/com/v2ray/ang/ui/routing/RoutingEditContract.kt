package com.v2ray.ang.ui.routing

import androidx.compose.runtime.Immutable
import com.v2ray.ang.AppConfig.TAG_PROXY
import com.v2ray.ang.AppConfig.BUILTIN_OUTBOUND_TAGS
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.ui.base.BaseAction
import com.v2ray.ang.ui.base.BaseEvent
import com.v2ray.ang.ui.base.BaseResult
import com.v2ray.ang.ui.base.BaseUiState

@Immutable
data class RoutingForm(
    val remarks: String = "",
    val locked: Boolean = false,
    val domain: String = "",
    val ip: String = "",
    val process: String = "",
    val protocol: String = "",
    val network: String = "",
    val port: String = "",
    val outboundTag: String = "",
)

enum class RoutingField { REMARKS, DOMAIN, IP, PROCESS, PROTOCOL, NETWORK, PORT, OUTBOUND }

@Immutable
data class RoutingEditUiState(
    val ruleId: String = "",
    val form: RoutingForm = RoutingForm(),
    val outboundOptions: List<String> = emptyList(),
    val canUseProcess: Boolean = false,
) : BaseUiState {
    val isEdit: Boolean get() = ruleId.isNotEmpty()
}

sealed interface RoutingEditAction : BaseAction {
    data class UpdateRemarks(val value: String) : RoutingEditAction
    data class UpdateDomain(val value: String) : RoutingEditAction
    data class UpdateIp(val value: String) : RoutingEditAction
    data class UpdateProcess(val value: String) : RoutingEditAction
    data class UpdateProtocol(val value: String) : RoutingEditAction
    data class UpdateNetwork(val value: String) : RoutingEditAction
    data class UpdatePort(val value: String) : RoutingEditAction
    data class UpdateOutbound(val value: String) : RoutingEditAction
    data class ToggleLocked(val value: Boolean) : RoutingEditAction
    data object SelectProcess : RoutingEditAction
    data class ResultReceived(val result: BaseResult) : RoutingEditAction
    data object Save : RoutingEditAction
    data object Back : RoutingEditAction
    data object Delete : RoutingEditAction
    data object ConfirmDelete : RoutingEditAction
}

sealed interface RoutingEditEvent : BaseEvent.Platform {
    data object ShowDeleteDialog : RoutingEditEvent
}

val RoutingForm.networkOrDefault: String get() = network.ifBlank { ROUTING_NETWORK_DEFAULT }

fun RoutingField.set(form: RoutingForm, value: String): RoutingForm = when (this) {
    RoutingField.REMARKS -> form.copy(remarks = value)
    RoutingField.DOMAIN -> form.copy(domain = value)
    RoutingField.IP -> form.copy(ip = value)
    RoutingField.PROCESS -> form.copy(process = value)
    RoutingField.PROTOCOL -> form.copy(protocol = value)
    RoutingField.NETWORK -> form.copy(network = value)
    RoutingField.PORT -> form.copy(port = value)
    RoutingField.OUTBOUND -> form.copy(outboundTag = value)
}

fun RulesetItem?.toRoutingForm(): RoutingForm = RoutingForm(
    remarks = this?.remarks.orEmpty(),
    locked = this?.locked == true,
    domain = this?.domain?.joinToString(",").orEmpty(),
    ip = this?.ip?.joinToString(",").orEmpty(),
    process = this?.process?.joinToString(",").orEmpty(),
    protocol = this?.protocol?.joinToString(",").orEmpty(),
    network = this?.network.orEmpty(),
    port = this?.port.orEmpty(),
    outboundTag = this?.outboundTag ?: BUILTIN_OUTBOUND_TAGS.first(),
)

fun RulesetItem.applyRoutingForm(form: RoutingForm): RulesetItem = copy(
    remarks = form.remarks,
    locked = form.locked,
    domain = form.domain.toCsvList(),
    ip = form.ip.toCsvList(),
    process = form.process.toCsvList(),
    protocol = form.protocol.toCsvList(),
    port = form.port.nullIfBlank(),
    network = form.network.nullIfBlank(),
    outboundTag = form.outboundTag.trim().ifEmpty { TAG_PROXY },
).also { it.id = this.id }

private fun String.toCsvList(): List<String>? = nullIfBlank()
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.takeIf { it.isNotEmpty() }
