package com.v2ray.ang.ui.main

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Immutable
import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.base.BaseAction
import com.v2ray.ang.ui.base.BaseEvent
import com.v2ray.ang.ui.base.BaseResult
import com.v2ray.ang.ui.base.BaseRoute
import com.v2ray.ang.ui.base.BaseUiState

@Immutable
data class LocateTarget(
    val serverGuid: String,
    val groupId: String
)

@Immutable
sealed interface MainStatus {
    data object Disconnected : MainStatus
    data object Connected : MainStatus
    data object Testing : MainStatus
    data class TestProgress(val progress: String) : MainStatus
    data class ConnectionTest(val result: ConnectionTestResult) : MainStatus
}

@Immutable
data class MainUiState(
    val groups: List<GroupMapItem> = emptyList(),
    val selectedGroupId: String = "",
    val selectedGuid: String? = null,
    val isRunning: Boolean = false,
    val isTesting: Boolean = false,
    val status: MainStatus = MainStatus.Disconnected,
    val confirmRemove: Boolean = false,
    val doubleColumnDisplay: Boolean = false,
    val isSearchActive: Boolean = false,
    val searchQuery: String = ""
) : BaseUiState {
    val isFiltering: Boolean get() = searchQuery.isNotBlank()
}

sealed interface MainAction : BaseAction {
    data object Initialize : MainAction
    data object RefreshGroups : MainAction
    data object ToggleService : MainAction
    data object RestartService : MainAction
    data object StatusBarClick : MainAction
    data object TestAllServers : MainAction
    data object TestRealAllServers : MainAction
    data object CancelTesting : MainAction
    data object RemoveAllServers : MainAction
    data object RemoveDuplicateServers : MainAction
    data object RemoveInvalidServers : MainAction
    data object SortByTestResults : MainAction
    data object UpdateSubscriptions : MainAction
    data object ExportAll : MainAction
    data object ImportFromQrCode : MainAction
    data object ImportFromClipboard : MainAction
    data object ImportFromFile : MainAction
    data class ConfigFileSelected(val uri: Uri) : MainAction
    data class ImportBatchConfig(val configText: String) : MainAction
    data class SelectGroup(val groupId: String) : MainAction
    data class SelectServer(val guid: String) : MainAction
    data class RemoveServer(val guid: String) : MainAction
    data class MoveServer(val groupId: String, val from: Int, val to: Int) : MainAction
    data class Search(val query: String) : MainAction
    data class SetSearchActive(val active: Boolean) : MainAction
    data object LocateSelectedServer : MainAction
    data object LocateFailed : MainAction
    data class AddServer(val configType: EConfigType) : MainAction
    data class EditServer(val guid: String, val configType: EConfigType) : MainAction
    data class ShareQrCode(val guid: String) : MainAction
    data class ShareClipboard(val guid: String) : MainAction
    data class ShareFullContent(val guid: String) : MainAction
    data class Navigate(val route: BaseRoute) : MainAction
    data object OpenPromotion : MainAction
    data class ResultReceived(val result: BaseResult) : MainAction
}

sealed interface MainEvent : BaseEvent.Platform {
    data class StartService(
        val requireVpnPermission: Boolean,
        val requireLocalNetwork: Boolean
    ) : MainEvent
    data object StopService : MainEvent
    data class RestartService(
        val stopFirst: Boolean,
        val requireVpnPermission: Boolean,
        val requireLocalNetwork: Boolean
    ) : MainEvent
    data object ScanQrCode : MainEvent
    data object PickConfigFile : MainEvent
    data class ShowQrCode(val bitmap: Bitmap) : MainEvent
    data class LocateProfile(val target: LocateTarget) : MainEvent
}
