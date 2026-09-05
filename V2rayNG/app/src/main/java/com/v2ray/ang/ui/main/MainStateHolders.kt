package com.v2ray.ang.ui.main

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.v2ray.ang.dto.ServerRowItem
import kotlinx.coroutines.flow.StateFlow

@Stable
class MainSlices(
    val servers: (String) -> StateFlow<List<ServerRowItem>>,
    val counts: (String) -> StateFlow<Int>,
)

@Stable
class GroupScrollStates {
    private val listStates = HashMap<String, LazyListState>()
    private val gridStates = HashMap<String, LazyGridState>()

    fun list(groupId: String): LazyListState = listStates.getOrPut(groupId) { LazyListState() }
    fun grid(groupId: String): LazyGridState = gridStates.getOrPut(groupId) { LazyGridState() }

    fun retain(validIds: Set<String>) {
        listStates.keys.retainAll(validIds)
        gridStates.keys.retainAll(validIds)
    }
}

@Stable
class MainDialogHost(private val onAction: (MainAction) -> Unit) {
    var current by mutableStateOf<MainDialog?>(null)
        private set
    var confirmRemove: Boolean = false

    val show: (MainDialog) -> Unit = { current = it }
    val dismiss: () -> Unit = { current = null }
    val requestRemove: (String) -> Unit = { guid ->
        if (confirmRemove) current = MainDialog.DeleteOne(guid)
        else onAction(MainAction.RemoveServer(guid))
    }
}

@Stable
class MainScreenHandles(
    val dispatch: (MainAction) -> Unit,
    val slices: MainSlices,
    val scrollStates: GroupScrollStates,
    val showDialog: (MainDialog) -> Unit,
    val requestRemove: (String) -> Unit,
)
