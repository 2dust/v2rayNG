package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.dto.entities.ProfileItem

/** Locale-neutral state formatted only when it reaches the main UI. */
sealed interface MainStatus {
    data object Disconnected : MainStatus
    data object Connected : MainStatus
    data object Testing : MainStatus
    data class TestProgress(val progress: String) : MainStatus
    data class ConnectionTest(val result: ConnectionTestResult) : MainStatus
}

/**
 * Main UI state
 */
data class MainUiState(
    val groups: List<GroupMapItem> = emptyList(),
    val selectedGroupId: String = "",
    val selectedGuid: String? = null,
    val isRunning: Boolean = false,
    val isTesting: Boolean = false,
    val status: MainStatus = MainStatus.Disconnected,
    val testStatus: MainStatus? = null,
    val searchQuery: String = "",
    val locateTarget: LocateTarget? = null,
    val confirmRemove: Boolean = false,
    val doubleColumnDisplay: Boolean = false,
    val shareQRCodeBitmap: android.graphics.Bitmap? = null
)

/** Retain counts while refreshing tab metadata and reloading each group's visible rows. */
internal fun List<GroupMapItem>.withServerCountsFrom(previous: List<GroupMapItem>): List<GroupMapItem> {
    val counts = previous.associate { it.id to it.serverCount }
    return map { group -> counts[group.id]?.let { group.copy(serverCount = it) } ?: group }
}

/**
 * All possible user interaction intents
 */
sealed interface MainAction {
    sealed interface ViewModelIntent : MainAction
    sealed interface ActivityRequest : MainAction

    data object Initialize : ViewModelIntent
    data object RefreshGroups : ViewModelIntent
    data object TestAllServers : ViewModelIntent
    data object TestRealAllServers : ViewModelIntent
    data object CancelTesting : ViewModelIntent
    data object RemoveAllServers : ViewModelIntent
    data object RemoveDuplicateServers : ViewModelIntent
    data object RemoveInvalidServers : ViewModelIntent
    data object SortByTestResults : ViewModelIntent
    data object UpdateSubscriptions : ViewModelIntent
    data class UpdateGroupSubscription(val groupId: String) : ViewModelIntent
    data object ExportAll : ViewModelIntent
    data object LocateSelectedServer : ViewModelIntent
    data class SelectGroup(val groupId: String) : ViewModelIntent
    data class RemoveServer(val guid: String) : ViewModelIntent
    data class Search(val query: String) : ViewModelIntent
    data class ShareQRCode(val guid: String) : ViewModelIntent
    data object DismissQRCodeDialog : ViewModelIntent
    data class ImportBatchConfig(val configText: String) : ViewModelIntent
    data class LocateHandled(val target: LocateTarget) : ViewModelIntent

    data object ToggleService : ActivityRequest
    data object TestCurrentServer : ActivityRequest
    data object ImportQRcode : ActivityRequest
    data object ImportClipboard : ActivityRequest
    data object ImportConfigLocal : ActivityRequest
    data class ImportManually(val type: Int) : ActivityRequest
    data object RestartService : ActivityRequest
    data object Exit : ActivityRequest
    data class SelectServer(val guid: String) : ActivityRequest
    data class EditServer(val guid: String, val profile: ProfileItem) : ActivityRequest
    data class ShareClipboard(val guid: String) : ActivityRequest
    data class ShareFullContent(val guid: String) : ActivityRequest
    data class OpenSubscriptionLink(val url: String) : ActivityRequest
}
