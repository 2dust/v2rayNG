package com.v2ray.ang.ui.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.moveGuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class MainReorderTest {
    private val dispatcher = StandardTestDispatcher()
    private val source = mock<MainDataSource>()
    private var persisted = listOf("a", "b", "c")
    private lateinit var viewModel: MainViewModel

    @BeforeEach fun setUp() {
        Dispatchers.setMain(dispatcher)
        whenever(source.mainServiceEvent).thenReturn(emptyFlow())
        whenever(source.getSelectedSubscriptionId()).thenReturn("s")
        whenever(source.getSelectServer()).thenReturn("")
        whenever(source.getSubscriptions()).thenReturn(listOf(SubscriptionCache("s", SubscriptionItem())))
        whenever(source.getServerGuidList("s")).thenAnswer { persisted }
        whenever(source.decodeServerConfig(any())).thenAnswer {
            ProfileItem.create(EConfigType.VLESS).apply { remarks = it.getArgument(0) }
        }
        whenever(source.moveServerInGroup(eq("s"), any(), any())).thenAnswer {
            saveMove(it.getArgument(1), it.getArgument(2))
        }
        viewModel = spy(MainViewModel(mock(), source, SavedStateHandle(), dispatcher, dispatcher))
        doNothing().whenever(viewModel).toastError(any<Int>())
    }

    @AfterEach fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test fun `later successful drag reconciles UI after earlier failed write`() = runTest(dispatcher) {
        advanceUntilIdle()
        assertEquals(persisted, visible())
        var attempts = 0
        whenever(source.moveServerInGroup(eq("s"), any(), any())).thenAnswer {
            if (++attempts == 1) null else saveMove(it.getArgument(1), it.getArgument(2))
        }
        move("a", "b")
        move("c", "b")
        assertEquals(listOf("c", "b", "a"), visible())
        advanceUntilIdle()
        assertEquals(2, attempts)
        assertEquals(listOf("a", "c", "b"), persisted)
        assertEquals(persisted, visible())
        verify(viewModel).toastError(R.string.toast_failure)
    }

    @Test fun `failed final drag restores persisted order`() = runTest(dispatcher) {
        advanceUntilIdle()
        whenever(source.moveServerInGroup(any(), any(), any())).thenReturn(null)
        val write = requireNotNull(move("a", "c"))
        assertEquals(false, write.isCompleted)
        advanceUntilIdle()
        assertEquals(false, write.await())
        assertEquals(listOf("a", "b", "c"), visible())
    }

    @Test fun `successful drag reloads storage instead of replaying stale membership`() = runTest(dispatcher) {
        advanceUntilIdle()
        whenever(source.moveServerInGroup(any(), any(), any())).thenAnswer {
            persisted = listOf("c", "a", "new")
            persisted
        }
        val write = requireNotNull(move("c", "a"))
        assertEquals(false, write.isCompleted)
        advanceUntilIdle()
        assertEquals(true, write.await())
        assertEquals(persisted, visible())
    }

    @Test fun `invalid and empty moves do not persist`() = runTest(dispatcher) {
        advanceUntilIdle()
        assertNull(move("missing", "a"))
        assertNull(move("a", "missing"))
        assertNull(move("a", "a"))
        assertNull(viewModel.moveServer(MainAction.MoveServer("empty", "a", "b")))
        viewModel.filterConfig("a")
        assertNull(move("a", "b"))
        viewModel.filterConfig("")
        advanceUntilIdle()
        assertEquals(persisted, visible())
        verify(source, never()).moveServerInGroup(any(), any(), any())
    }

    private fun move(fromGuid: String, toGuid: String) =
        viewModel.moveServer(MainAction.MoveServer("s", fromGuid, toGuid))

    private fun saveMove(fromGuid: String, toGuid: String): List<String>? {
        val order = persisted.toMutableList()
        if (!order.moveGuid(fromGuid, toGuid)) return null
        persisted = order
        return order
    }

    private fun visible(groupId: String = "s"): List<String> {
        val state = viewModel.serverGroupState(groupId).value
        assertEquals(state.servers.map { it.guid }, state.rows.map { it.guid })
        assertEquals(state.servers.map { it.profile.remarks }, state.rows.map { it.remarks })
        return state.servers.map { it.guid }
    }

    @Test fun `repeated group selection uses its populated cache`() = runTest(dispatcher) {
        advanceUntilIdle()
        repeat(2) {
            viewModel.subscriptionIdChanged("s")
            advanceUntilIdle()
        }
        assertEquals(persisted, visible())
        verify(source, times(1)).getServerGuidList("s")
        verify(source, times(1)).decodeServerConfig("a")
    }

    @Test fun `collected tab servers follow reconciled row state`() = runTest(dispatcher) {
        val tabServers = viewModel.serversForGroup("s")
        val collection = backgroundScope.launch { tabServers.collect {} }
        advanceUntilIdle()
        assertEquals(visible(), tabServers.value.map { it.guid })
        whenever(source.moveServerInGroup(any(), any(), any())).thenAnswer {
            persisted = listOf("c", "a", "new")
            persisted
        }
        move("c", "a")
        advanceUntilIdle()
        assertEquals(persisted, visible())
        assertEquals(visible(), tabServers.value.map { it.guid })
        collection.cancel()
    }

    @Test fun `empty groups are cached and explicit refresh bypasses cache`() = runTest(dispatcher) {
        persisted = emptyList()
        advanceUntilIdle()
        viewModel.subscriptionIdChanged("s")
        advanceUntilIdle()
        verify(source, times(1)).getServerGuidList("s")
        persisted = listOf("new")
        viewModel.onAction(MainAction.RefreshGroups)
        advanceUntilIdle()
        assertEquals(persisted, visible())
        verify(source, times(2)).getServerGuidList("s")
    }

    @Test fun `reorder invalidates All cache and caches the saved group order`() = runTest(dispatcher) {
        whenever(source.getSubscriptions()).thenReturn(listOf("s", "").map { SubscriptionCache(it, SubscriptionItem()) })
        whenever(source.getServerGuidList("")).thenAnswer { persisted }
        viewModel.setupGroupTab()
        advanceUntilIdle()
        assertEquals(persisted, visible(""))
        move("a", "c")
        advanceUntilIdle()
        viewModel.subscriptionIdChanged("")
        advanceUntilIdle()
        assertEquals(listOf("b", "c", "a"), visible(""))
        viewModel.subscriptionIdChanged("s")
        advanceUntilIdle()
        assertEquals(persisted, visible())
        verify(source, times(2)).getServerGuidList("s")
        verify(source, times(2)).getServerGuidList("")
    }

    @Test fun `pending reorder refreshes All when selected before the write completes`() = runTest(dispatcher) {
        whenever(source.getSubscriptions()).thenReturn(listOf("s", "").map { SubscriptionCache(it, SubscriptionItem()) })
        whenever(source.getServerGuidList("")).thenAnswer { persisted }
        viewModel.setupGroupTab()
        advanceUntilIdle()

        val write = requireNotNull(move("a", "c"))
        viewModel.subscriptionIdChanged("")
        advanceUntilIdle()

        assertEquals(true, write.await())
        assertEquals(listOf("b", "c", "a"), visible(""))
    }

    @Test fun `refresh invalidates shared profiles but retains unrelated group caches`() = runTest(dispatcher) {
        whenever(source.getSubscriptions()).thenReturn(listOf("s", "shared", "unrelated").map {
            SubscriptionCache(it, SubscriptionItem())
        })
        whenever(source.getServerGuidList("shared")).thenReturn(listOf("b"))
        whenever(source.getServerGuidList("unrelated")).thenReturn(listOf("x"))
        viewModel.setupGroupTab()
        advanceUntilIdle()
        whenever(source.decodeServerConfig("b")).thenReturn(ProfileItem.create(EConfigType.VLESS).apply { remarks = "edited" })
        viewModel.reloadAllGroups(listOf("s"))
        advanceUntilIdle()
        viewModel.subscriptionIdChanged("shared")
        advanceUntilIdle()
        assertEquals(listOf("b"), visible("shared"))
        assertEquals("edited", viewModel.serverGroupState("shared").value.rows.single().remarks)
        viewModel.subscriptionIdChanged("unrelated")
        advanceUntilIdle()
        verify(source, times(2)).getServerGuidList("shared")
        verify(source, times(1)).getServerGuidList("unrelated")
    }

    @Test fun `All refresh invalidates individual group caches`() = runTest(dispatcher) {
        whenever(source.getSubscriptions()).thenReturn(listOf("s", "").map { SubscriptionCache(it, SubscriptionItem()) })
        whenever(source.getServerGuidList("")).thenAnswer { persisted }
        viewModel.setupGroupTab()
        advanceUntilIdle()
        viewModel.subscriptionIdChanged("")
        advanceUntilIdle()
        assertEquals("", viewModel.uiState.value.selectedGroupId)
        persisted = listOf("new")
        viewModel.reloadAllGroups(listOf(""))
        advanceUntilIdle()
        viewModel.subscriptionIdChanged("s")
        advanceUntilIdle()
        assertEquals(listOf("new"), visible())
        verify(source, times(2)).getServerGuidList("s")
    }
}
