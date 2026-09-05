package com.v2ray.ang.ui.subscription

import androidx.lifecycle.viewModelScope
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.MmkvTestStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionsPersistenceTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var stores: MmkvTestStore
    private lateinit var viewModel: SubscriptionsViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        stores = MmkvTestStore()
        listOf("a", "b", "c").forEach {
            MmkvManager.encodeSubscription(it, SubscriptionItem(remarks = it, lastUpdated = 100))
        }
        viewModel = spy(SubscriptionsViewModel(mock(), dispatcher))
        doNothing().whenever(viewModel).toastError(any<Int>())
    }

    @After fun tearDown() {
        viewModel.viewModelScope.cancel()
        stores.close()
        Dispatchers.resetMain()
    }

    @Test fun `queued drag recovers from failed write and keeps new subscriptions`() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.move(0, 1)
        viewModel.move(1, 2)
        MmkvManager.encodeSubscription("new", SubscriptionItem())
        var writes = 0
        stores.main.failWrite = { it == "SUB_IDS" && ++writes == 1 }
        advanceUntilIdle()
        assertEquals(listOf("b", "c", "a", "new"), visible())
        assertEquals(MmkvManager.decodeSubsList(), visible())
    }

    @Test fun `failed drag restores persisted state and invalid moves do nothing`() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.move(-1, 0)
        viewModel.move(0, 99)
        assertEquals(listOf("a", "b", "c"), visible())
        stores.main.failWrite = { true }
        viewModel.move(0, 2)
        advanceUntilIdle()
        assertEquals(listOf("a", "b", "c"), visible())
    }

    @Test fun `toggle reloads merged timestamp from storage`() = runTest(dispatcher) {
        advanceUntilIdle()
        val old = viewModel.subsFlow.value.first().subscription
        MmkvManager.updateSubscription("a", old, old, updatedAt = 200)
        viewModel.update("a", old.copy(enabled = false))
        advanceUntilIdle()
        val displayed = viewModel.subsFlow.value.first().subscription
        assertFalse(displayed.enabled)
        assertEquals(200L, displayed.lastUpdated)
        viewModel.reload()
        advanceUntilIdle()
        assertEquals(displayed, viewModel.subsFlow.value.first().subscription)
    }

    @Test fun `stale settings edit reloads current settings without overwriting them`() = runTest(dispatcher) {
        advanceUntilIdle()
        val old = viewModel.subsFlow.value.first().subscription
        val changed = old.copy(remarks = "updated elsewhere")
        MmkvManager.updateSubscription("a", old, changed)
        viewModel.update("a", old.copy(enabled = false))
        advanceUntilIdle()
        assertEquals(changed, viewModel.subsFlow.value.first().subscription)
    }

    @Test fun `empty subscriptions load and ignore move and update`() = runTest(dispatcher) {
        stores.main.values["SUB_IDS"] = "[]"
        advanceUntilIdle()
        viewModel.move(0, 1)
        viewModel.update("missing", SubscriptionItem())
        advanceUntilIdle()
        assertTrue(viewModel.subsFlow.value.isEmpty())
    }

    private fun visible() = viewModel.subsFlow.value.map { it.guid }

    @Test fun `default Android factory can construct the ViewModel`() {
        assertNotNull(SubscriptionsViewModel::class.java.getConstructor(android.app.Application::class.java))
    }
}
