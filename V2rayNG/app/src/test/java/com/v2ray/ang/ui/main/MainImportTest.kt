package com.v2ray.ang.ui.main

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.BatchImportResult
import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
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
import org.mockito.Mockito.clearInvocations
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
        val result = CompletableDeferred<BatchImportResult>()
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
        result.complete(subscriptionImport("new"))
        advanceUntilIdle()

        assertEquals(listOf(ImportCall("external", "", false)), source.imports)
        assertEquals(listOf("selected", "new"), model.uiState.value.groups.map { it.id })
        assertFalse(model.isLoading.value)
        verify(model).toast(R.string.import_subscription_success)
    }

    @Test
    fun regularImportKeepsSelectedGroupAndAppendMode() = withViewModel { model, source ->
        source.importResult = { BatchImportResult(profileCount = 1) }
        whenever(source.getString(R.string.title_import_config_count, 1)).thenReturn("Imported 1")

        model.onAction(MainAction.ImportBatchConfig("profile"))
        advanceUntilIdle()

        assertEquals(listOf(ImportCall("profile", "selected", true)), source.imports)
        verify(model).toast("Imported 1")
        assertFalse(model.isLoading.value)
    }

    @Test
    fun emptyAndInvalidImportsFinishLoadingWithFailureFeedback() = withViewModel { model, source ->
        source.importResult = { BatchImportResult() }
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
        val first = CompletableDeferred<BatchImportResult>()
        val second = CompletableDeferred<BatchImportResult>()
        source.importResult = { if (it.text == "first") first.await() else second.await() }
        model.onAction(MainAction.ImportBatchConfig("first"))
        model.onAction(MainAction.ImportBatchConfig("second"))
        runCurrent()
        assertEquals(2, source.imports.size)
        assertTrue(model.isLoading.value)

        first.complete(subscriptionImport("first"))
        advanceUntilIdle()
        assertTrue(model.isLoading.value)
        second.complete(subscriptionImport("second"))
        advanceUntilIdle()
        assertFalse(model.isLoading.value)
    }

    @Test
    fun initialDownloadFailureKeepsTheGroupAndReportsFailureCounts() = withViewModel { model, source ->
        source.importResult = { BatchImportResult(
            subscriptionIds = listOf("new"), subscriptionUpdates = SubscriptionUpdateResult(failureCount = 1)
        ) }
        whenever(source.getSubscriptions()).thenReturn(listOf(
            SubscriptionCache("selected", SubscriptionItem("Selected")), SubscriptionCache("new", SubscriptionItem("New"))
        ))
        whenever(source.getString(R.string.title_update_subscription_result, 0, 0, 1, 0)).thenReturn("0 success, 1 failed")

        model.onAction(MainAction.ImportBatchConfig("failed download"))
        advanceUntilIdle()

        verify(model).toast("0 success, 1 failed")
        verify(model, never()).toast(R.string.import_subscription_success)
        assertEquals(listOf("selected", "new"), model.uiState.value.groups.map { it.id })
        assertFalse(model.isLoading.value)
    }

    @Test
    fun mixedImportReportsPartialFailureAndTotalProfileCount() = withViewModel { model, source ->
        source.importResult = { BatchImportResult(
            profileCount = 2, subscriptionIds = listOf("new"),
            subscriptionUpdates = SubscriptionUpdateResult(configCount = 3, successCount = 1, failureCount = 1, skipCount = 1)
        ) }
        whenever(source.getString(R.string.title_update_subscription_result, 5, 1, 1, 1)).thenReturn("5 configs, 1 failed, 1 skipped")

        model.onAction(MainAction.ImportBatchConfig("mixed"))
        advanceUntilIdle()

        verify(model).toast("5 configs, 1 failed, 1 skipped")
        verify(model, never()).toast(R.string.import_subscription_success)
    }

    @Test
    fun failedSaveReportsFailureWithoutRefreshingGroups() = withViewModel { model, source ->
        source.importResult = { BatchImportResult(subscriptionUpdates = SubscriptionUpdateResult(failureCount = 1)) }
        whenever(source.getString(R.string.title_update_subscription_result, 0, 0, 1, 0)).thenReturn("1 failed")
        clearInvocations(source.defaults)

        model.onAction(MainAction.ImportBatchConfig("unsaved"))
        advanceUntilIdle()

        verify(model).toast("1 failed")
        verify(source.defaults, never()).getSubscriptions()
        verify(source.defaults, never()).getServerGuidList(any())
    }

    @Test
    fun addingSubscriptionPreservesExistingCacheAndRefreshesAllGroup() = withViewModel { model, source ->
        whenever(source.getSubscriptions()).thenReturn(listOf(
            SubscriptionCache("", SubscriptionItem("All")), SubscriptionCache("selected", SubscriptionItem("Selected"))
        ))
        model.setupGroupTab()
        advanceUntilIdle()
        clearInvocations(source.defaults)
        whenever(source.getSubscriptions()).thenReturn(listOf(
            SubscriptionCache("", SubscriptionItem("All")), SubscriptionCache("selected", SubscriptionItem("Selected")),
            SubscriptionCache("new", SubscriptionItem("New"))
        ))
        whenever(source.getServerGuidList("new")).thenReturn(listOf("new-profile"))
        whenever(source.getServerGuidList("")).thenReturn(listOf("new-profile"))
        whenever(source.decodeServerConfig("new-profile")).thenReturn(ProfileItem(configType = EConfigType.VLESS, subscriptionId = "new"))
        source.importResult = { subscriptionImport("new") }

        model.onAction(MainAction.ImportBatchConfig("new"))
        advanceUntilIdle()

        verify(source.defaults, never()).getServerGuidList("selected")
        verify(source.defaults).getServerGuidList("new")
        verify(source.defaults).getServerGuidList("")
        assertEquals(listOf("new-profile"), model.serverGroupState("new").value.servers.map { it.guid })
        assertEquals(listOf("new-profile"), model.serverGroupState("").value.servers.map { it.guid })
        assertEquals("selected", model.uiState.value.selectedGroupId)
    }

    @Test
    fun overlappingMixedImportsInvalidateBothTargetsAndDefaultGroup() = withViewModel { model, source ->
        val groupIds = listOf("", "selected", AppConfig.DEFAULT_SUBSCRIPTION_ID)
        whenever(source.getSubscriptions()).thenReturn(groupIds.map { SubscriptionCache(it, SubscriptionItem(it)) })
        model.setupGroupTab()
        advanceUntilIdle()
        clearInvocations(source.defaults)
        whenever(source.getString(R.string.title_import_config_count, 1)).thenReturn("Imported 1")
        whenever(source.getServerGuidList("selected")).thenReturn(listOf("selected-profile"))
        whenever(source.getServerGuidList(AppConfig.DEFAULT_SUBSCRIPTION_ID)).thenReturn(listOf("default-profile"))
        whenever(source.getServerGuidList("")).thenReturn(listOf("selected-profile", "default-profile"))
        whenever(source.decodeServerConfig("selected-profile")).thenReturn(ProfileItem(configType = EConfigType.VLESS, subscriptionId = "selected"))
        whenever(source.decodeServerConfig("default-profile")).thenReturn(ProfileItem(configType = EConfigType.VLESS, subscriptionId = AppConfig.DEFAULT_SUBSCRIPTION_ID))
        source.importResult = { BatchImportResult(profileCount = 1) }

        model.onAction(MainAction.ImportBatchConfig("selected"))
        model.onAction(MainAction.ImportBatchConfig("default", "", false))
        advanceUntilIdle()

        assertEquals(listOf("selected-profile"), model.serverGroupState("selected").value.servers.map { it.guid })
        assertEquals(listOf("default-profile"), model.serverGroupState(AppConfig.DEFAULT_SUBSCRIPTION_ID).value.servers.map { it.guid })
        assertEquals(listOf("selected-profile", "default-profile"), model.serverGroupState("").value.servers.map { it.guid })
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

    private fun subscriptionImport(id: String) = BatchImportResult(
        subscriptionIds = listOf(id), subscriptionUpdates = SubscriptionUpdateResult(successCount = 1)
    )

    private data class ImportCall(val text: String?, val subscriptionId: String, val append: Boolean)

    private class ImportSource(val defaults: MainDataSource = mock()) : MainDataSource by defaults {
        val imports = mutableListOf<ImportCall>()
        var importResult: suspend (ImportCall) -> BatchImportResult = { BatchImportResult() }

        override suspend fun importBatchConfig(server: String?, subscriptionId: String, append: Boolean): BatchImportResult {
            val call = ImportCall(server, subscriptionId, append)
            imports += call
            return importResult(call)
        }
    }
}
