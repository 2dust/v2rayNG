package com.v2ray.ang.ui.main

import androidx.lifecycle.viewModelScope
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class MainReorderTest {
    private val dispatcher = StandardTestDispatcher()
    private val source = mock<MainDataSource>()
    private var persisted = listOf("a", "b", "c")
    private lateinit var viewModel: MainViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        whenever(source.mainServiceEvent).thenReturn(emptyFlow())
        whenever(source.getSelectedSubscriptionId()).thenReturn("s")
        whenever(source.getSelectServer()).thenReturn("")
        whenever(source.getSubscriptions()).thenReturn(listOf(SubscriptionCache("s", SubscriptionItem())))
        whenever(source.getServerGuidList("s")).thenAnswer { persisted }
        whenever(source.decodeServerConfig(any())).thenAnswer {
            ProfileItem.create(EConfigType.VLESS).apply { remarks = it.getArgument(0) }
        }
        whenever(source.reorderServerList(any(), eq("s"))).thenAnswer {
            persisted = it.getArgument(0)
            true
        }
        viewModel = spy(MainViewModel(mock(), source, dispatcher, dispatcher))
        doNothing().whenever(viewModel).toastError(any<Int>())
    }

    @After fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test fun `later successful drag reconciles UI after earlier failed write`() = runTest(dispatcher) {
        advanceUntilIdle()
        assertEquals(persisted, visible())
        var attempts = 0
        whenever(source.reorderServerList(any(), eq("s"))).thenAnswer {
            if (++attempts == 1) false else { persisted = it.getArgument(0); true }
        }
        viewModel.moveServer("s", 0, 1)
        viewModel.moveServer("s", 1, 2)
        assertEquals(listOf("b", "c", "a"), visible())
        advanceUntilIdle()
        assertEquals(2, attempts)
        assertEquals(listOf("b", "c", "a"), persisted)
        assertEquals(persisted, visible())
        verify(viewModel).toastError(R.string.toast_failure)
    }

    @Test fun `failed final drag restores persisted order`() = runTest(dispatcher) {
        advanceUntilIdle()
        whenever(source.reorderServerList(any(), any())).thenReturn(false)
        viewModel.moveServer("s", 0, 2)
        advanceUntilIdle()
        assertEquals(listOf("a", "b", "c"), visible())
    }

    @Test fun `successful drag reloads storage instead of replaying stale membership`() = runTest(dispatcher) {
        advanceUntilIdle()
        whenever(source.reorderServerList(any(), any())).thenAnswer {
            persisted = listOf("c", "a", "new")
            true
        }
        viewModel.moveServer("s", 2, 0)
        advanceUntilIdle()
        assertEquals(persisted, visible())
    }

    @Test fun `invalid and empty moves do not persist`() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.moveServer("s", -1, 0)
        viewModel.moveServer("s", 0, 3)
        viewModel.moveServer("empty", 0, 1)
        advanceUntilIdle()
        assertEquals(persisted, visible())
        verify(source, never()).reorderServerList(any(), any())
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
        whenever(source.reorderServerList(any(), any())).thenAnswer {
            persisted = listOf("c", "a", "new")
            true
        }
        viewModel.moveServer("s", 2, 0)
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
        viewModel.reloadServerList()
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
        viewModel.moveServer("s", 0, 2)
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

    @Test fun `refresh invalidates shared profiles but retains unrelated group caches`() = runTest(dispatcher) {
        whenever(source.getSubscriptions()).thenReturn(listOf("s", "shared", "unrelated").map {
            SubscriptionCache(it, SubscriptionItem())
        })
        whenever(source.getServerGuidList("shared")).thenReturn(listOf("b"))
        whenever(source.getServerGuidList("unrelated")).thenReturn(listOf("x"))
        viewModel.setupGroupTab()
        advanceUntilIdle()
        whenever(source.decodeServerConfig("b")).thenReturn(ProfileItem.create(EConfigType.VLESS).apply { remarks = "edited" })
        viewModel.reloadServerList()
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
        viewModel.reloadServerList()
        advanceUntilIdle()
        viewModel.subscriptionIdChanged("s")
        advanceUntilIdle()
        assertEquals(listOf("new"), visible())
        verify(source, times(2)).getServerGuidList("s")
    }
}
