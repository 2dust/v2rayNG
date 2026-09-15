package com.v2ray.ang.ui.main

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tencent.mmkv.MMKV
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

// ExperimentalCoroutinesApi controls Main and virtual time; reevaluate when these test APIs become stable.
@OptIn(ExperimentalCoroutinesApi::class)
class MainImportTest {
    @Test
    fun externalImportUsesDefaultGroupAndRefreshesAfterCompletion() = withViewModel { model, source ->
        val result = CompletableDeferred<Pair<Int, Int>>()
        source.importResult = { result.await() }
        assertEquals("selected", model.uiState.value.selectedGroupId)
        assertFalse(model.isLoading.value)

        model.onAction(MainAction.ImportBatchConfig("external", "", false))
        runCurrent()
        assertTrue(model.isLoading.value)
        assertEquals(listOf("selected"), model.uiState.value.groups.map { it.id })
        whenever(source.getSubscriptions()).thenReturn(listOf(
            SubscriptionCache("selected", SubscriptionItem("Selected")),
            SubscriptionCache("new", SubscriptionItem("New"))
        ))
        result.complete(0 to 1)
        advanceUntilIdle()

        assertEquals(listOf(ImportCall("external", "", false)), source.imports)
        assertEquals(listOf("selected", "new"), model.uiState.value.groups.map { it.id })
        assertFalse(model.isLoading.value)
        verify(model).toast(R.string.import_subscription_success)
    }

    @Test
    fun regularImportKeepsSelectedGroupAndAppendMode() = withViewModel { model, source ->
        source.importResult = { 1 to 0 }
        whenever(source.getString(R.string.title_import_config_count, 1)).thenReturn("Imported 1")

        model.onAction(MainAction.ImportBatchConfig("profile"))
        advanceUntilIdle()

        assertEquals(listOf(ImportCall("profile", "selected", true)), source.imports)
        verify(model).toast("Imported 1")
        assertFalse(model.isLoading.value)
    }

    @Test
    fun emptyAndInvalidImportsFinishLoadingWithFailureFeedback() = withViewModel { model, source ->
        source.importResult = { 0 to 0 }
        for (text in listOf("", "invalid")) {
            model.onAction(MainAction.ImportBatchConfig(text, "", false))
            advanceUntilIdle()
            assertFalse(model.isLoading.value)
        }
        verify(model, times(2)).toastError(R.string.toast_failure)
        assertEquals(listOf("selected"), model.uiState.value.groups.map { it.id })
    }

    @Test
    fun overlappingImportsStayLoadingUntilBothComplete() = withViewModel { model, source ->
        val first = CompletableDeferred<Pair<Int, Int>>()
        val second = CompletableDeferred<Pair<Int, Int>>()
        source.importResult = { if (it.text == "first") first.await() else second.await() }
        model.onAction(MainAction.ImportBatchConfig("first"))
        model.onAction(MainAction.ImportBatchConfig("second"))
        runCurrent()
        assertEquals(2, source.imports.size)
        assertTrue(model.isLoading.value)

        first.complete(0 to 1)
        advanceUntilIdle()
        assertTrue(model.isLoading.value)
        second.complete(0 to 1)
        advanceUntilIdle()
        assertFalse(model.isLoading.value)
    }

    @Test
    fun failureClearsLoadingAndReportsAnError() = withViewModel { model, source ->
        source.importResult = { throw IllegalStateException("Import failed") }
        val settings = mock<MMKV>()
        mockStatic(MMKV::class.java).use { mmkv ->
            mmkv.`when`<MMKV> { MMKV.mmkvWithID("SETTING", MMKV.MULTI_PROCESS_MODE) }.thenReturn(settings)
            mockStatic(Log::class.java).use {
                model.onAction(MainAction.ImportBatchConfig("failure"))
                advanceUntilIdle()
            }
        }
        assertFalse(model.isLoading.value)
        verify(model).toastError(R.string.toast_failure)
    }

    @Test
    fun cancellationClearsLoadingWithoutFailureFeedback() = withViewModel { model, source ->
        source.importResult = { throw CancellationException() }
        model.onAction(MainAction.ImportBatchConfig("cancelled"))
        advanceUntilIdle()
        assertFalse(model.isLoading.value)
        verify(model, never()).toastError(any<Int>())
    }

    private fun withViewModel(block: suspend TestScope.(MainViewModel, ImportSource) -> Unit) = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val source = ImportSource()
        whenever(source.mainServiceEvent).thenReturn(emptyFlow())
        whenever(source.getSelectedSubscriptionId()).thenReturn("selected")
        whenever(source.getSubscriptions()).thenReturn(listOf(SubscriptionCache("selected", SubscriptionItem("Selected"))))
        whenever(source.defaults.getServerGuidList(any())).thenReturn(emptyList())
        val model = spy(MainViewModel(mock<Application>(), source, dispatcher, dispatcher))
        doNothing().whenever(model).toast(any<Int>())
        doNothing().whenever(model).toast(any<String>())
        doNothing().whenever(model).toastError(any<Int>())
        try {
            advanceUntilIdle()
            block(model, source)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private data class ImportCall(val text: String?, val subscriptionId: String, val append: Boolean)

    private class ImportSource(val defaults: MainDataSource = mock()) : MainDataSource by defaults {
        val imports = mutableListOf<ImportCall>()
        var importResult: suspend (ImportCall) -> Pair<Int, Int> = { 0 to 0 }

        override suspend fun importBatchConfig(server: String?, subscriptionId: String, updateUI: Boolean): Pair<Int, Int> {
            val call = ImportCall(server, subscriptionId, updateUI)
            imports += call
            return importResult(call)
        }
    }
}
