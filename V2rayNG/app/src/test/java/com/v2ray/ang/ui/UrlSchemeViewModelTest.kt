package com.v2ray.ang.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.RemoteControlManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class UrlSchemeViewModelTest {
    private val application = mock<Application>()
    private val configs = mock<AngConfigManager>()
    private val access = mock<RemoteControlManager>()
    private val content = "socks://127.0.0.1:9#Import-test"
    private val logPriority = LogUtil::class.java.getDeclaredField("cachedMinPriority").apply { isAccessible = true }
    private var previousLogPriority = 0

    // Coroutine test APIs only override Main for these JVM tests; no production opt-in.
    @OptIn(ExperimentalCoroutinesApi::class)
    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        previousLogPriority = logPriority.getInt(null)
        logPriority.setInt(null, Int.MAX_VALUE)
        whenever(configs.importBatchConfig(any(), eq(""), any())).thenReturn(1 to 0)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After fun tearDown() {
        Dispatchers.resetMain()
        logPriority.setInt(null, previousLogPriority)
    }

    private fun model() = UrlSchemeViewModel(application, configs, access)

    private fun UrlSchemeViewModel.load(
        text: String? = content,
        uid: Int = -1,
        claimed: String? = null,
        token: String? = null,
    ) = initialize(Intent.ACTION_SEND, "text/plain", null, text, uid, claimed, token)

    private suspend fun UrlSchemeViewModel.settled(): UrlSchemeState = withTimeout(5000) {
        state.first { it != UrlSchemeState.Loading && it != UrlSchemeState.Importing }
    }

    @Test fun loadingAndUnauthenticatedRequestsDoNotImport() = runBlocking<Unit> {
        val model = model()
        assertEquals(UrlSchemeState.Loading, model.state.value)
        model.onAction(UrlSchemeAction.Confirm)
        verifyNoInteractions(configs)
        model.load()
        assertEquals(UrlSchemeState.Pending(content), model.settled())
        verifyNoInteractions(configs)
        verify(access).isAuthorized(application, -1, null, null)
    }

    @Test fun confirmationAppendsWithoutGrantingAutomationAccess() = runBlocking<Unit> {
        val model = model()
        model.load()
        model.settled()
        model.onAction(UrlSchemeAction.Confirm)
        assertEquals(UrlSchemeState.Imported, model.settled())
        verify(configs).importBatchConfig(content, "", true)
        verify(access, never()).setSelectedPackages(any(), any())
    }

    @Test fun allowedAutomationRetainsReplacementMode() = runBlocking<Unit> {
        whenever(access.isAuthorized(application, 1234, null, null)).thenReturn(true)
        val model = model()
        model.load(uid = 1234)
        assertEquals(UrlSchemeState.Imported, model.settled())
        verify(configs).importBatchConfig(content, "", false)
    }

    @Test fun savedCapabilityUsesTheSameGrantManager() = runBlocking<Unit> {
        whenever(access.isAuthorized(application, -1, "allowed.host", "saved-capability")).thenReturn(true)
        whenever(configs.importBatchConfig(content, "", false)).thenReturn(0 to 1)
        val model = model()
        model.load(claimed = "allowed.host", token = "saved-capability")
        assertEquals(UrlSchemeState.Imported, model.settled())
        verify(configs).importBatchConfig(content, "", false)
    }

    @Test fun revokedOrForgedCredentialsRequireConfirmation() = runBlocking<Unit> {
        val model = model()
        model.load(uid = 2345, claimed = "allowed.host", token = "forged")
        assertEquals(UrlSchemeState.Pending(content), model.settled())
        verify(access).isAuthorized(application, 2345, "allowed.host", "forged")
        verifyNoInteractions(configs)
    }

    @Test fun cancelAndLaterConfirmCannotImport() = runBlocking<Unit> {
        val model = model()
        model.load()
        model.settled()
        model.onAction(UrlSchemeAction.Cancel)
        model.onAction(UrlSchemeAction.Confirm)
        assertEquals(UrlSchemeState.Cancelled, model.state.value)
        verifyNoInteractions(configs)
    }

    @Test fun cancellationDuringAuthorizationCannotTriggerAutomaticImport() = runBlocking<Unit> {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        whenever(access.isAuthorized(application, -1, null, null)).thenAnswer {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            true
        }
        val model = model()
        model.load()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            model.onAction(UrlSchemeAction.Cancel)
        } finally {
            release.countDown()
        }
        withTimeout(5000) { model.viewModelScope.coroutineContext[Job]!!.children.toList().joinAll() }
        assertEquals(UrlSchemeState.Cancelled, model.state.value)
        verifyNoInteractions(configs)
    }

    @Test fun activityRecreationDoesNotReplacePendingInputOrReplayCompletedImport() = runBlocking<Unit> {
        val model = model()
        model.load()
        model.settled()
        model.load(text = "different payload")
        assertEquals(UrlSchemeState.Pending(content), model.state.value)
        model.onAction(UrlSchemeAction.Confirm)
        assertEquals(UrlSchemeState.Imported, model.settled())
        model.load()
        model.onAction(UrlSchemeAction.Confirm)
        verify(configs, times(1)).importBatchConfig(content, "", true)
    }

    @Test fun repeatedConfirmWhileImportingDoesNotStartAnotherWrite() = runBlocking<Unit> {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        whenever(configs.importBatchConfig(content, "", true)).thenAnswer {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            1 to 0
        }
        val model = model()
        model.load()
        model.settled()
        model.onAction(UrlSchemeAction.Confirm)
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            model.onAction(UrlSchemeAction.Confirm)
            model.onAction(UrlSchemeAction.Cancel)
            assertEquals(UrlSchemeState.Importing, model.state.value)
        } finally {
            release.countDown()
        }
        assertEquals(UrlSchemeState.Imported, model.settled())
        verify(configs, times(1)).importBatchConfig(content, "", true)
    }

    @Test fun invalidInputFailsBeforeAuthorizationOrImport() = runBlocking<Unit> {
        for (text in listOf(null, "", " ")) {
            val model = model()
            model.load(text)
            assertEquals(UrlSchemeState.Failed, model.settled())
        }
        verifyNoInteractions(access, configs)
    }

    @Test fun emptyImportResultAndImportExceptionReportFailure() = runBlocking<Unit> {
        for (throwError in listOf(false, true)) {
            reset(configs)
            if (throwError) {
                whenever(configs.importBatchConfig(content, "", true)).thenThrow(IllegalStateException("secret"))
            } else {
                whenever(configs.importBatchConfig(content, "", true)).thenReturn(0 to 0)
            }
            val model = model()
            model.load()
            model.settled()
            model.onAction(UrlSchemeAction.Confirm)
            assertEquals(UrlSchemeState.Failed, model.settled())
        }
    }

    @Test fun authorizationFailureDoesNotFallBackToImport() = runBlocking<Unit> {
        whenever(access.isAuthorized(application, -1, null, null))
            .thenThrow(IllegalStateException("unavailable"))
        val model = model()
        model.load()
        assertEquals(UrlSchemeState.Failed, model.settled())
        verifyNoInteractions(configs)
    }
}
