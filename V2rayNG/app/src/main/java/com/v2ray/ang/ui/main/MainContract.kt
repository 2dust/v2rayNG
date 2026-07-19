package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.LocateTarget

/**
 * Main UI state
 */
data class MainUiState(
    val groups: List<GroupMapItem> = emptyList(),
    val selectedGroupId: String = "",
    val selectedGuid: String? = null,
    val isRunning: Boolean = false,
    val isLoading: Boolean = false,
    val isTesting: Boolean = false,
    val statusText: String = "",
    val userMessage: UserMessage? = null,
    val locateTarget: LocateTarget? = null,
    val confirmRemove: Boolean = false,
    val doubleColumnDisplay: Boolean = false
)

/**
 * One-time user message (e.g., Toast), using id to avoid re-consumption on recomposition
 */
data class UserMessage(
    val id: Long = System.nanoTime(),
    val text: String,
    val isError: Boolean = false,
    val isSuccess: Boolean = false
)

/**
 * All possible user interaction intents
 */
sealed interface MainAction {
    data object Initialize : MainAction
    data object RefreshGroups : MainAction
    data object ToggleService : MainAction
    data object TestCurrentServer : MainAction
    data object TestAllServers : MainAction
    data object CancelTesting : MainAction
    data object RemoveAllServers : MainAction
    data object RemoveDuplicateServers : MainAction
    data object RemoveInvalidServers : MainAction
    data object SortByTestResults : MainAction
    data object UpdateSubscriptions : MainAction
    data object ExportAll : MainAction
    data object ImportConfigViaSub : MainAction

    data class SelectGroup(val groupId: String) : MainAction
    data class SelectServer(val guid: String) : MainAction
    data class RemoveServer(val guid: String) : MainAction
    data class Search(val query: String) : MainAction
    data class SwapServer(val fromIndex: Int, val toIndex: Int) : MainAction

    data class ImportBatchConfig(val configText: String) : MainAction

    data class UserMessageShown(val messageId: Long) : MainAction
    data class LocateHandled(val target: LocateTarget) : MainAction
}
