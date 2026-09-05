package com.v2ray.ang.ui.routing

import androidx.compose.runtime.Immutable
import com.v2ray.ang.dto.RoutingRuleRow
import com.v2ray.ang.enums.RoutingType
import com.v2ray.ang.ui.base.BaseAction
import com.v2ray.ang.ui.base.BaseEvent
import com.v2ray.ang.ui.base.BaseResult
import com.v2ray.ang.ui.base.BaseUiState

@Immutable
data class RoutingUiState(
    val domainStrategy: String = "",
    val rules: List<RoutingRuleRow> = emptyList(),
) : BaseUiState

sealed interface RoutingDialog {
    data object Presets : RoutingDialog
    @Immutable data class ConfirmImport(val pending: PendingImport) : RoutingDialog
}

sealed interface PendingImport {
    @Immutable data class Preset(val type: RoutingType) : PendingImport
    data object Clipboard : PendingImport
    @Immutable data class Text(val value: String) : PendingImport
}

sealed interface RoutingAction : BaseAction {
    data object Back : RoutingAction
    data object AddRule : RoutingAction
    data class EditRule(val ruleId: String) : RoutingAction
    data class ToggleRule(val ruleId: String, val enabled: Boolean) : RoutingAction
    data class MoveRule(val fromId: String, val toId: String) : RoutingAction
    data class SelectDomainStrategy(val value: String) : RoutingAction
    data object PresetClicked : RoutingAction
    data class PresetSelected(val type: RoutingType) : RoutingAction
    data object ImportFromClipboard : RoutingAction
    data object ImportFromQrCode : RoutingAction
    data class QrCodeScanned(val text: String?) : RoutingAction
    data object ExportToClipboard : RoutingAction
    data class ConfirmImport(val pending: PendingImport) : RoutingAction
    data class ResultReceived(val result: BaseResult) : RoutingAction
}

sealed interface RoutingEvent : BaseEvent.Platform {
    data object ScanQrCode : RoutingEvent
    data class ShowDialog(val dialog: RoutingDialog) : RoutingEvent
}

const val ROUTING_NETWORK_DEFAULT = "tcp,udp"
val ROUTING_NETWORK_OPTIONS = listOf("tcp", "udp", ROUTING_NETWORK_DEFAULT)
