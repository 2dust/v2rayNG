package com.v2ray.ang.ui.main

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

class MainImportTest {
    @Test
    fun externalImportUsesDefaultGroupAndRefreshesAfterCompletion() = withViewModel { model, source ->
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        runBlocking {
            whenever(source.importBatchConfig("external", "", false)).thenAnswer {
                started.countDown()
                check(finish.await(5, TimeUnit.SECONDS))
                0 to 1
            }
        }

        assertEquals("selected", model.uiState.value.selectedGroupId)
        assertFalse(model.isLoading.value)
        model.onAction(MainAction.ImportBatchConfig("external", "", false))
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertTrue(model.isLoading.value)
            assertEquals(listOf("selected"), model.uiState.value.groups.map { it.id })
            whenever(source.getSubscriptions()).thenReturn(listOf(
                SubscriptionCache("selected", SubscriptionItem("Selected")),
                SubscriptionCache("new", SubscriptionItem("New"))
            ))
        } finally {
            finish.countDown()
        }

        awaitIdle(model)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (model.uiState.value.groups.none { it.id == "new" } && System.nanoTime() < deadline) Thread.sleep(10)
        assertEquals(listOf("selected", "new"), model.uiState.value.groups.map { it.id })
        verify(model).toast(R.string.import_subscription_success)
    }

    @Test
    fun regularImportKeepsSelectedGroupAndAppendMode() = withViewModel { model, source ->
        runBlocking { whenever(source.importBatchConfig("profile", "selected", true)).thenReturn(1 to 0) }
        whenever(source.getString(R.string.title_import_config_count, 1)).thenReturn("Imported 1")

        model.onAction(MainAction.ImportBatchConfig("profile"))

        verify(model, timeout(5000)).toast("Imported 1")
        awaitIdle(model)
        runBlocking { verify(source).importBatchConfig("profile", "selected", true) }
    }

    @Test
    fun emptyAndInvalidImportsFinishLoadingWithFailureFeedback() = withViewModel { model, source ->
        for (text in listOf("", "invalid")) {
            runBlocking { whenever(source.importBatchConfig(text, "", false)).thenReturn(0 to 0) }
            model.onAction(MainAction.ImportBatchConfig(text, "", false))
            awaitIdle(model)
        }
        verify(model, timeout(5000).times(2)).toastError(R.string.toast_failure)
        assertEquals(listOf("selected"), model.uiState.value.groups.map { it.id })
    }

    private fun withViewModel(block: (MainViewModel, MainDataSource) -> Unit) {
        val dispatcher = object : MainCoroutineDispatcher() {
            override val immediate get() = this
            override fun isDispatchNeeded(context: CoroutineContext) = false
            override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
        }
        mockStatic(Dispatchers::class.java, CALLS_REAL_METHODS).use { dispatchers ->
            dispatchers.`when`<MainCoroutineDispatcher> { Dispatchers.Main }.thenReturn(dispatcher)
            val source = mock<MainDataSource>()
            whenever(source.mainServiceEvent).thenReturn(emptyFlow())
            whenever(source.getSelectedSubscriptionId()).thenReturn("selected")
            whenever(source.getSubscriptions()).thenReturn(listOf(SubscriptionCache("selected", SubscriptionItem("Selected"))))
            whenever(source.getServerGuidList(any())).thenReturn(emptyList())
            val model = spy(MainViewModel(mock<Application>(), source))
            doNothing().whenever(model).toast(any<Int>())
            doNothing().whenever(model).toast(any<String>())
            doNothing().whenever(model).toastError(any<Int>())
            try {
                model.setupGroupTab().joinBlocking()
                block(model, source)
            } finally {
                model.viewModelScope.cancel()
            }
        }
    }

    private fun kotlinx.coroutines.Job.joinBlocking() = runBlocking { join() }

    private fun awaitIdle(model: MainViewModel) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (model.isLoading.value && System.nanoTime() < deadline) Thread.sleep(10)
        assertFalse(model.isLoading.value)
    }
}
